package com.finmate.evaluation.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.domain.news.dto.NewsItem;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * OpenAI Responses API의 Structured Outputs로 평가 대상 기사의 관련도와 사건 그룹을 판정한다.
 * 이 클래스는 evaluation source set에만 존재하므로 운영 요청과 운영 JAR에서는 호출되지 않는다.
 * 즉 해당 평가용 class는 운영용 jar 파일에 포함되지 않는다.
 *
 * 각 기사가 평가 주제의 투자 판단에 얼마나 유용한지(relevance)와 같은 사건을 반복 보도하는지(eventId)를 라벨링한다.
 * 이 라벨은 이후 nDCG와 중복률 계산의 기준값으로 사용된다.
 */
final class OpenAiNewsJudge implements NewsJudge {
    // 프롬프트의 판단 기준이 바뀌면 이 값을 올려 기존 라벨을 자동으로 재사용하지 않게 한다.
    private static final String PROMPT_VERSION = "news-judge-v1";
    // 프록시나 호환 endpoint를 지정하지 않았을 때 사용할 OpenAI API 기본 주소다.
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    // <b> 같은 네이버 검색 강조 태그를 Judge 입력에서 제거한다.
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final ObjectMapper objectMapper; // Java 객체와 API JSON을 상호 변환한다.
    private final HttpClient httpClient; // 별도 SDK 없이 JDK 기본 HTTP client로 요청한다.
    private final String apiKey; // Authorization 헤더에만 사용하며 결과 파일에는 저장하지 않는다.
    private final String model; // 평가 재현성을 위해 실행자가 명시한 고정 모델 ID
    private final URI responsesUri; // 최종 /v1/responses endpoint

	// EvaluationEnvironment를 활용해서 평가 JVM 환경에서 환경변수를 읽는다.
    OpenAiNewsJudge(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // 민감한 API key를 소스나 JSON 파일에 넣지 않고 실행 환경에서만 읽는다.
        this.apiKey = EvaluationEnvironment.required("OPENAI_API_KEY");
        // 모델 변화로 평가 기준이 흔들리지 않도록 사용자가 평가 실행 때 모델 ID를 명시하게 한다.
        this.model = EvaluationEnvironment.required("NEWS_EVALUATION_JUDGE_MODEL");
        String baseUrl = EvaluationEnvironment.optional("OPENAI_BASE_URL", DEFAULT_BASE_URL);
        // 사용자가 base URL 끝에 /를 붙여도 //responses가 되지 않도록 마지막 slash를 제거한다.
        this.responsesUri = URI.create(stripTrailingSlash(baseUrl) + "/responses");
        // 서버 연결 자체가 오래 걸릴 때 무한 대기하지 않도록 연결 제한 시간을 설정한다.
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // 캐시된 Judge 결과가 현재 모델, 프롬프트, 기사 집합으로 만든 것인지 확인한다.
    // 순서가 달라도 articleId 집합이 같으면 같은 기사들을 판정한 것이므로 재사용할 수 있다.
    @Override
    public boolean isCompatible(NewsJudgeReport report, List<NewsItem> articles) {
        if (!model.equals(report.judgeModel())
                || !PROMPT_VERSION.equals(report.promptVersion())) {
            return false;
        }
        var labeledIds = report.articles().stream()
                .map(NewsJudgeLabel::articleId)
                .collect(java.util.stream.Collectors.toSet());
        // 현재 모든 articleId가 기존 라벨에 있고 개수도 정확히 같아야 한다.
		// 즉 articleId 집합이 기존이랑 같은지를 검사한다. 같으면 재활용이 가능하다.
        return articles.stream()
                .map(EvaluationArticleIds::from)
                .allMatch(labeledIds::contains)
                && labeledIds.size() == articles.size();
    }

    @Override
    public NewsJudgeReport judge(NewsEvaluationDataset dataset, List<NewsItem> articles)
            throws IOException, InterruptedException {
        // LinkedHashMap을 사용해 사람이 요청 JSON을 확인할 때 필드 순서가 예측 가능하게 유지한다.
        Map<String, Object> requestBody = new LinkedHashMap<>();
		// LLM judge로 사용할 모델을 지정한다.
        requestBody.put("model", model);
        // 평가 입력과 출력을 API 제공자의 장기 저장 대상으로 요청하지 않는다. 즉 요청결과를 API 제공자(OPEN AI)가 저장하지 않도록 설정한다.
        requestBody.put("store", false);
        // instructions에는 평가 기준, input에는 실제 기사 목록을 분리해 전달한다.
        requestBody.put("instructions", systemInstructions(dataset)); // 평가 프롬프트
        requestBody.put("input", articleInput(articles)); // 입력 기사목록
        // LLM judge가 일관되지 않은 형식으로 응답하는 것이 아니라, 아래 JSON Schema를 지키는 구조화된 결과를 요청한다.
		// 이는 Openai API에서 제공하는 Structured Outputs 기능을 활용하여 응답구조를 강제한다.
        requestBody.put("text", Map.of(
                "format", Map.of(
                        "type", "json_schema",
                        "name", "news_judge_labels",
                        "strict", true,
                        "schema", responseSchema(articles)))); // 해당 responseSchema 구조를 지키도록 강하게 제약을 건다.

        HttpRequest request = HttpRequest.newBuilder()
                .uri(responsesUri)
                // 모델 판정 전체가 120초를 넘으면 요청을 실패시켜 평가가 무한정 멈추지 않게 한다.
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody))) // requestBody는 아직 객체이기 때문에 이를 ObjectMapper를 활용하여 json 문자열로 변환한다.
                .build();

        // send는 동기 호출이다. 응답이 올 때까지 현재 평가 스레드가 기다리며 응답 본문을 문자열로 받는다.
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "OpenAI judge 요청이 실패했습니다. status=" + response.statusCode()
                            + ", body=" + response.body());
        }

        // Responses API 전체 응답에서 모델이 생성한 output_text만 꺼낸 뒤 내부 DTO로 역직렬화한다.
        String outputText = extractOutputText(objectMapper.readTree(response.body())); // LLM judge가 생성한 output_text만 추출한다.
        JudgeArticles judgeArticles = objectMapper.readValue(outputText, JudgeArticles.class); // output_text의 문자열데이터를 읽어 DTO로 변환한다.
        // Schema를 사용해도 애플리케이션이 기대한 ID가 모두 한 번씩 왔는지 다시 방어적으로 검사한다.
        validateLabels(articles, judgeArticles.articles());
		// LLM judge의 응답 결과를 기반으로 NewsJudgeReport를 생성한다.
        return new NewsJudgeReport(
                dataset.datasetId(),
                model,
                PROMPT_VERSION,
                judgeArticles.articles());
    }

	// 모델에 입력으로 넣을 시스템 프롬프트를 설정한다.
    private String systemInstructions(NewsEvaluationDataset dataset) {
        // subjectName과 subjectType을 넣어 같은 기사가 주제별로 다른 관련도를 가질 수 있게 한다.
        // 예를 들어 환율 기사는 환율 데이터셋에서는 관련도가 높지만 삼성전자에서는 낮을 수 있다.
        return """
                당신은 한국 주식 뉴스 랭킹을 평가하는 엄격한 심사자다.
                제목과 네이버 API description만 근거로 판단하고 외부 사실을 추측하지 않는다.
                평가 주제는 '%s'이며 유형은 %s다.
                relevance는 다음 기준의 정수다.
                0: 주제가 단순 언급되거나 투자 판단과 무관함.
                1: 주제와 관련 있지만 투자 판단에 주는 정보가 제한적임.
                2: 실적, 주가, 수급, 사업, 제품, 산업 변화 등 투자 판단에 직접 유용함.
                같은 구체적 사건을 다루는 기사에는 반드시 같은 eventId를 부여한다.
                서로 다른 사건에는 다른 eventId를 부여한다.
                eventId는 EVENT-001 형식으로 현재 입력 안에서만 일관되게 만든다.
                reason은 한국어 한 문장으로 짧게 작성한다.
                모든 articleId를 정확히 한 번씩 판정한다.
                """.formatted(dataset.subjectName(), dataset.subjectType());
    }

	// LLM judge에 입력으로 넣을 input format을 결정한다.
    private String articleInput(List<NewsItem> articles) throws IOException {
        List<Map<String, String>> inputs = new ArrayList<>();
        for (NewsItem item : articles) { // 기사 목록을 순회하면서 모델에는 판정에 필요한 식별자, 제목, 요약, 발행시각만 전송한다.
            // 전체 원본 DTO 대신 판정에 필요한 식별자, 제목, 요약, 발행시각만 전송한다.
            inputs.add(Map.of(
                    "articleId", EvaluationArticleIds.from(item),
                    "title", normalize(item.title()),
                    "description", normalize(item.description()),
                    "publishedAt", item.publishedAt() == null ? "" : item.publishedAt()));
        }
		// 지시문과 함꼐 리스트를 json 문자열로 변환하여 리턴한다.
        return "다음 JSON 배열의 모든 기사를 평가하라.\n"
                + objectMapper.writeValueAsString(inputs);
    }

	// LLM judge의 응답 구조를 설정한다.
    private Map<String, Object> responseSchema(List<NewsItem> articles) {
        // Judge 모델이 일부 기사를 생략하거나 존재하지 않는 ID를 만들지 못하도록 허용 articleId와 정확한 응답 개수를 JSON Schema에서 제한한다.
        List<String> articleIds = articles.stream()
                .map(EvaluationArticleIds::from)
                .toList();
        // 배열 원소 하나, 즉 기사 라벨 한 건이 지켜야 하는 구조다.
        Map<String, Object> articleSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "articleId", Map.of("type", "string", "enum", articleIds), // articleIds에 들어있는 ID를 넣도록 강제한다.
                        "relevance", Map.of("type", "integer", "minimum", 0, "maximum", 2), // 투자 관련도는 최소 0, 최대 2점으로 강제한다.
                        "eventId", Map.of("type", "string"),
                        "reason", Map.of("type", "string")),
                "required", List.of("articleId", "relevance", "eventId", "reason"));
        // 최상위 객체는 articles 배열 하나만 허용하며 입력 기사 수와 정확히 같아야 한다.
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "articles", Map.of(
                                "type", "array",
                                "items", articleSchema, // 각 기사 하나하나가 articleSchema를 만족해야 한다.
                                "minItems", articles.size(),
                                "maxItems", articles.size())),
                "required", List.of("articles"));
    }

	// OpenAI Response API의 전체 JSON 응답에서 실제 모델이 생성한 텍스트 부분인 output_text를 찾아서 문자열로 꺼낸다.
	/**
	{
	  "output": [
		{
		  "content": [
			{
			  "type": "output_text",
			  "text": "{\"articles\":[...]}"
			}
		  ]
		}
	  ]
	}
	* 즉 OpenAI API의 응답에서 실제 모델이 응답한 부분만 찾아서 리턴한다.
	**/
    private String extractOutputText(JsonNode response) {
        // Responses API의 output[] -> content[]를 순회해 type이 output_text인 실제 생성 결과를 찾는다.
        for (JsonNode output : response.path("output")) { // 최상위 JSON에서 output nodes들을 순회한다.
            for (JsonNode content : output.path("content")) { // output node 내부의 content nodes들을 순회한다.
                // 이때 content의 타입이 output_text인지, 그리고 text정보가 실제로 존재하는지를 검사한다.
				if ("output_text".equals(content.path("type").asText())
                        && content.hasNonNull("text")) {
                    return content.path("text").asText();
                }
            }
        }
        throw new IllegalStateException("OpenAI judge 응답에서 output_text를 찾을 수 없습니다.");
    }

    private void validateLabels(List<NewsItem> inputArticles, List<NewsJudgeLabel> labels) {
        // Map에 넣으면서 같은 articleId가 두 번 반환됐는지 검사한다.
        Map<String, NewsJudgeLabel> labelsById = new LinkedHashMap<>();
        for (NewsJudgeLabel label : labels) {
            if (labelsById.put(label.articleId(), label) != null) {
                throw new IllegalStateException("OpenAI judge가 중복 articleId를 반환했습니다: " + label.articleId());
            }
            if (label.eventId() == null || label.eventId().isBlank()) {
                throw new IllegalStateException("OpenAI judge가 빈 eventId를 반환했습니다: " + label.articleId());
            }
        }
        // 반대로 입력 기사 중 라벨이 빠진 것도 없는지 검사한다.
        for (NewsItem item : inputArticles) {
            String articleId = EvaluationArticleIds.from(item);
            if (!labelsById.containsKey(articleId)) {
                throw new IllegalStateException("OpenAI judge가 기사 라벨을 누락했습니다: " + articleId);
            }
        }
        if (labelsById.size() != inputArticles.size()) {
            throw new IllegalStateException("OpenAI judge의 라벨 수가 평가 기사 수와 다릅니다.");
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        // HTML 태그를 제거하고 &amp; 같은 entity와 연속 공백을 사람이 읽는 일반 문장으로 바꾼다.
        String withoutTags = HTML_TAG_PATTERN.matcher(value).replaceAll(" ");
        return HtmlUtils.htmlUnescape(withoutTags).replaceAll("\\s+", " ").trim();
    }

    private String stripTrailingSlash(String value) {
        // base URL 끝에 slash가 하나 있을 때만 제거한다.
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    // Structured Outputs의 최상위 {"articles": [...]}를 Jackson으로 읽기 위한 내부 DTO다.
    private record JudgeArticles(List<NewsJudgeLabel> articles) {
    }
}
