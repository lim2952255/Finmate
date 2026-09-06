package com.finmate.service.news;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.finmate.domain.news.NewsSentiment;
import com.finmate.domain.news.dto.NewsItem;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * KR-FinBert-SC를 ONNX Runtime으로 실행해 최종 선별된 종목·시장 뉴스의 감성을 분석한다.
 * 모델과 토크나이저는 최초 뉴스 갱신 때 한 번만 로딩하며 이후 요청에서는 같은 세션을 재사용한다.
 *
 * E5가 문장을 의미 벡터로 바꾸는 모델이라면, 이 모델은 문장을 세 분류의 점수로 바꾼다.
 * 모델 출력 순서는 {negative, neutral, positive}이고, 애플리케이션에서는 각각
 * {악재, 보통, 호재}로 표시한다.
 *
 *
 * download-kr-finbert-sentiment-model.sh가 PyTorch 원본을 ONNX INT8 파일로 미리
 * 변환한다. 이 클래스는 Python이나 변환 도구를 실행하지 않고 완성된 model.onnx만 읽는다.
 */

// 실제 뉴스 캐시 갱신이 발생하기 전에는 큰 모델 파일을 메모리에 올리지 않는다.
@Lazy
@Component
// AutoCloseable 구현은 세션과 토크나이저처럼 명시적으로 정리해야 하는 자원을 가졌다는 뜻이다.
public class FinBertNewsSentimentAnalyzer implements AutoCloseable {
    // model.onnx 출력 배열의 각 위치가 어떤 라벨인지 나타낸다. 이 순서가 실제 모델의
    // config.json과 달라지면 긍정과 부정을 반대로 해석할 수 있으므로 고정 revision과 함께 관리한다.
    private static final int NEGATIVE_LABEL_INDEX = 0;
    private static final int NEUTRAL_LABEL_INDEX = 1;
    private static final int POSITIVE_LABEL_INDEX = 2;
    private static final int LABEL_COUNT = 3;
    // 네이버 뉴스 제목과 요약에 포함된 <b> 등의 HTML 태그를 제거하기 위한 정규식이다.
    // Pattern을 매 기사마다 다시 컴파일하지 않고 한 번 만들어 재사용한다.
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    // 줄바꿈, 탭, 연속 공백을 하나의 공백으로 합쳐 모델 입력 형식을 일정하게 만든다.
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    // ONNX Runtime의 프로세스 공용 기반이다. 세션과 입력 텐서를 생성할 때 사용한다.
    private final OrtEnvironment environment;
    // 양자화된 model.onnx를 로딩한 실제 감성분류 모델 실행 객체다. 요청마다 생성하지 않고 재사용한다.
    private final OrtSession session;
    // tokenizer.json의 어휘와 분리 규칙으로 문자열을 모델 입력 숫자 배열로 변환한다.
    private final HuggingFaceTokenizer tokenizer;
    // 긍정·부정으로 표시하기 위해 필요한 최소 확률이다. 확신이 낮은 방향성 예측은 NEUTRAL로 낮춘다.
    private final double directionalThreshold;

    public FinBertNewsSentimentAnalyzer(
            // 설정값이 없으면 models/kr-finbert-sentiment 아래의 기본 파일을 사용한다.
            // Docker/EC2에서는 application.properties의 환경변수 연결을 통해 마운트 경로를 주입한다.
            @Value("${finmate.news.sentiment.model-path:models/kr-finbert-sentiment/model.onnx}") String modelPath,
            @Value("${finmate.news.sentiment.tokenizer-path:models/kr-finbert-sentiment/tokenizer.json}") String tokenizerPath,
            @Value("${finmate.news.sentiment.max-token-length:256}") int maxTokenLength,
            @Value("${finmate.news.sentiment.directional-threshold:0.65}") double directionalThreshold) {
        // 모델이 허용하는 범위를 벗어난 길이를 시작 시점에 차단한다. 지나치게 긴 입력은
        // 메모리와 추론 시간을 크게 늘리고, 지나치게 짧은 입력은 기사 의미를 잃을 수 있다.
        if (maxTokenLength < 8 || maxTokenLength > 512) {
            throw new IllegalArgumentException("뉴스 감성 모델 최대 토큰 길이는 8 이상 512 이하여야 합니다.");
        }
        if (directionalThreshold < 0.0 || directionalThreshold > 1.0) {
            throw new IllegalArgumentException("뉴스 감성 방향 임계값은 0 이상 1 이하여야 합니다.");
        }

        // 경로 오류를 session 생성 중의 모호한 네이티브 오류 대신 읽기 쉬운 메시지로 먼저 알린다.
        Path resolvedModelPath = requireRegularFile(modelPath, "KR-FinBert-SC ONNX 모델");
        Path resolvedTokenizerPath = requireRegularFile(tokenizerPath, "KR-FinBert-SC 토크나이저");
        try {
            // environment는 ONNX Runtime 전체 기반이고 session은 그 위에 특정 모델을 올린 실행 인스턴스이다.
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(
                    resolvedModelPath.toString(), // 미리 변환해둔 ONNX model을 읽는다.
                    new OrtSession.SessionOptions());
            // 특수 토큰을 자동으로 추가하고 긴 기사는 설정 길이까지만 사용한다. 패딩은 아래에서
            // 배치 안의 가장 긴 문장 길이에 맞춰 명시적으로 수행한다.
            this.tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(resolvedTokenizerPath) // 미리 저장해둔 토크나이저 모델을 읽는다.
                    .optAddSpecialTokens(true) // 특수 토큰을 추가한다.
                    .optTruncation(true) // 최대길이를 초과한 토큰은 자른다.
                    .optMaxLength(maxTokenLength) // 최대길이를 지정한다.
                    .build();
            this.directionalThreshold = directionalThreshold; // 호재 / 보통 / 악재를 구분할 임계치를 설정한다.
        } catch (OrtException | IOException e) {
            throw new IllegalStateException("KR-FinBert-SC 모델을 초기화할 수 없습니다.", e);
        }
    }

    /**
     * 랭킹 전략이 최종 선택한 기사들을 한 배치로 분석한다.
     * 서로 다른 종목의 동시 갱신도 하나의 ONNX 세션을 안전하게 공유하도록 추론 구간을 직렬화한다.
     * synchronized 때문에 한 JVM에서는 이 메서드를 한 스레드씩 실행한다. 모델 초기화 비용은 다시 들지 않으며, 여러 기사를 batch로 묶어 한 번의 session.run()으로 처리한다.
     */
    public synchronized List<NewsItem> analyze(List<NewsItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }

        // NewsItem 원본은 유지하고 모델에 넣을 제목+요약 문자열 목록을 별도로 만든다.
		// 각 NewsItem 마다 createInput()을 호출하여 제목 + 요약 문자열 목록을 새로 생성한다.
        List<String> inputs = items.stream()
                .map(this::createInput)
                .toList();
        // 토큰화는 전처리 단계이며 이는 제목 + 요약 문자열들을 토큰으로 변환한다.
        Encoding[] encodings = tokenizer.batchEncode(inputs);
		// 모델에 배치단위 입력하기 위해서는 텐서단위로 입력을 해야하기 때문에, 각 Encoding 배열들의 길이를 맞춘다.
        BatchInputs batchInputs = createBatchInputs(encodings);

        // Java long[][] 배열을 ONNX Runtime이 읽는 텐서로 변환한다. 텐서와 Result는 Java heap
        // 밖의 네이티브 자원을 가질 수 있으므로 try-with-resources로 이번 호출 직후 닫는다.
        try (OnnxTensor inputIds = OnnxTensor.createTensor(environment, batchInputs.inputIds());
             OnnxTensor attentionMask = OnnxTensor.createTensor(environment, batchInputs.attentionMask());
             OnnxTensor tokenTypeIds = OnnxTensor.createTensor(environment, batchInputs.tokenTypeIds())) {
            Map<String, OnnxTensor> modelInputs = createSessionInputs(inputIds, attentionMask, tokenTypeIds);

			// 실제 ONNX 모델을 호출해서 기사별 negative/neutral/positive 점수가 나온다.
            try (OrtSession.Result result = session.run(modelInputs)) {
				// 모델 응답에서 logit 결과를 추출한다. 데이터 형식은 float[기사 수][3] 이며, 각 기사별 positive점수, neutral 점수, negative 점수가 출력된다.
                float[][] logits = extractLogits(result);
                if (logits.length != items.size()) {
                    throw new IllegalStateException("뉴스 감성 모델의 출력 개수가 입력 기사 개수와 다릅니다.");
                }

                // 입력 순서와 출력 행의 순서가 같으므로 같은 index의 기사에 분류 결과를 붙인다.
                ArrayList<NewsItem> analyzedItems = new ArrayList<>(items.size());
                for (int index = 0; index < items.size(); index++) { // 각 기사를 순회하며, ONNX 모델 출력 결과를 기반으로 감성을 분석해서, NewsItem에 감성을 추가한다.
                    analyzedItems.add(items.get(index).withSentiment(classify(logits[index])));
                }
                return List.copyOf(analyzedItems);
            }
        } catch (OrtException e) {
            throw new IllegalStateException("KR-FinBert-SC 뉴스 감성 추론에 실패했습니다.", e);
        }
    }

    // 네이버가 검색어 강조용으로 제목과 요약문에 넣은 HTML 표현을 모델이 단어로 오해하지 않도록
    // 제거한 뒤, 제목과 요약을 하나의 분류 입력으로 결합한다.
    private String createInput(NewsItem item) {
        String title = normalizeText(item.title());
        String description = normalizeText(item.description());
        if (description.isBlank()) {
            return title;
        }
        return title + ". " + description;
    }

    private String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // 1. <b> 같은 태그를 공백으로 바꾼다.
        String withoutTags = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");
        // 2. &quot;, &amp; 같은 HTML entity를 실제 문자로 되돌린다.
        String unescaped = HtmlUtils.htmlUnescape(withoutTags);
        // 3. 연속 공백과 줄바꿈을 하나로 합치고 앞뒤 공백을 제거한다.
        return WHITESPACE_PATTERN.matcher(unescaped).replaceAll(" ").trim();
    }

    // ONNX 배치 입력은 [기사 수][토큰 수]의 직사각형이어야 하므로 가장 긴 기사에 맞춰
    // 나머지 행의 뒤쪽을 0으로 남긴다. attentionMask의 0은 모델이 해당 PAD 자리를 무시하게 한다.
    private BatchInputs createBatchInputs(Encoding[] encodings) {
		// 최대 길이 Encoding 배열을 찾는다.
        int maxLength = 0;
        for (Encoding encoding : encodings) {
            maxLength = Math.max(maxLength, encoding.getIds().length);
        }

		// 모든 배열의 크기를 문장 개수 x 최대길이로 맞춘다. 이때 자바 배열은 처음 생성시 모든 값이 0으로 채워진다.
        long[][] inputIds = new long[encodings.length][maxLength]; // 토큰을 어휘 번호로 표현한 배열
        long[][] attentionMask = new long[encodings.length][maxLength]; // 실제 토큰 1, PAD 자리 0
        long[][] tokenTypeIds = new long[encodings.length][maxLength]; // 문장 구간을 구분하는 번호

        for (int index = 0; index < encodings.length; index++) { // 모든 문장을 하나씩 순회한다.
            Encoding encoding = encodings[index]; // 인코딩된 문장을 하나 꺼낸다.
			// 각 문장이 인코딩된 Encoding 객체에는 토큰 ID, attention mask, type ID가 들어있다.

			// 토크나이저가 만든 토큰 ID 배열을 batchInput 배열에 복사한다.
			// 이렇게 배열에 복사할때 남는길이 만큼은 기존의 0이 채워지면서 자연스럽게 패딩이 추가되는 효과가 나타난다.
            System.arraycopy(encoding.getIds(), 0, inputIds[index], 0, encoding.getIds().length);

			// 토크나이저가 만든 attention mask 배열을 batchInput 배열에 복사한다.
            System.arraycopy(encoding.getAttentionMask(), 0, attentionMask[index], 0,
                    encoding.getAttentionMask().length);

			// 토크나이저가 만든 type ID 배열을 batchInput 배열에 복사한다.
			System.arraycopy(encoding.getTypeIds(), 0, tokenTypeIds[index], 0,
                    encoding.getTypeIds().length);
        }
        return new BatchInputs(inputIds, attentionMask, tokenTypeIds);
    }

    // 모델마다 요구하는 입력 이름이 다를 수 있다. input_ids와 attention_mask는 항상 전달하고,
    // ONNX 그래프가 token_type_ids를 선언한 경우에만 세 번째 입력을 추가한다.
    private Map<String, OnnxTensor> createSessionInputs(OnnxTensor inputIds,
                                                        OnnxTensor attentionMask,
                                                        OnnxTensor tokenTypeIds) {
        Set<String> inputNames = session.getInputNames(); // model.onnx가 선언한 실제 입력 이름
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put("input_ids", inputIds);
        inputs.put("attention_mask", attentionMask);
        if (inputNames.contains("token_type_ids")) {
            inputs.put("token_type_ids", tokenTypeIds);
        }
        return inputs;
    }

    // logits는 확률로 바꾸기 전의 원시 분류 점수다. 기사 한 건당 세 숫자가 있으며 값의 합이
    // 1일 필요는 없다. 아래 softmax가 이를 비교 가능한 확률로 바꾼다.
    private float[][] extractLogits(OrtSession.Result result) throws OrtException {
        // 출력 이름이 보존되면 logits로 찾고, export 과정에서 이름이 달라진 경우 첫 출력을 사용한다.
        OnnxValue output = result.get("logits").orElseGet(() -> result.get(0));
        Object value = output.getValue(); // 실제 모델 출력 결과인 onnx 벡터를 꺼낸다.
        if (value instanceof float[][] logits) {
            return logits;
        }
        throw new IllegalStateException("지원하지 않는 뉴스 감성 모델 출력 형식입니다: " + value.getClass());
    }

    // 방향성 확률이 충분하지 않은 긍정·부정 결과는 과도한 호재/악재 표시를 막기 위해 보통으로 처리한다.
    private NewsSentiment classify(float[] logits) {
        if (logits.length != LABEL_COUNT) {
            throw new IllegalStateException("뉴스 감성 모델 라벨 개수는 3개여야 합니다: " + logits.length);
        }
		// logits의 호재, 보통, 악재 점수는 확률이 아니라 점수이기 때문에, 이 점수들을 softmax를 통해 확률로 변환한다.
        double[] probabilities = softmax(logits);
        // 세 확률 중 가장 큰 값의 index를 찾는다.
        int predictedIndex = 0;
        for (int index = 1; index < probabilities.length; index++) {
            if (probabilities[index] > probabilities[predictedIndex]) {
                predictedIndex = index;
            }
        }

        // 긍정과 부정은 가장 높은 라벨인 것만으로 부족하고 설정한 확률 임계값도 넘어야 한다.
        // neutral이 가장 높거나 방향성 확률이 낮으면 보수적으로 NEUTRAL을 반환한다.
		// 즉 긍정 / 부정 라벨이 가장 높다고 바로 해당 라벨로 판정하는 것이 아니라, 임계치를 넘어야 한다.
        if (predictedIndex == NEGATIVE_LABEL_INDEX
                && probabilities[NEGATIVE_LABEL_INDEX] >= directionalThreshold) {
            return NewsSentiment.NEGATIVE;
        }
        if (predictedIndex == POSITIVE_LABEL_INDEX
                && probabilities[POSITIVE_LABEL_INDEX] >= directionalThreshold) {
            return NewsSentiment.POSITIVE;
        }
        return NewsSentiment.NEUTRAL;
    }

    // softmax는 임의 범위의 logits를 합계 1인 확률 세 개로 변환한다.
    // 모든 값에서 같은 수를 빼도 softmax 결과는 같으므로 가장 큰 logit을 먼저 빼서
    // Math.exp 계산이 너무 커져 Infinity가 되는 것을 방지한다.

	// ONNX 모델이 출력한 원시 점수인 logits을 softmax를 통해 확률로 변환한다.
    private double[] softmax(float[] logits) {
		// 가장 큰 logit을 찾는다.
        double maxLogit = Math.max(logits[NEGATIVE_LABEL_INDEX],
                Math.max(logits[NEUTRAL_LABEL_INDEX], logits[POSITIVE_LABEL_INDEX]));
        double[] probabilities = new double[logits.length];
        double sum = 0.0;
        for (int index = 0; index < logits.length; index++) {
            probabilities[index] = Math.exp(logits[index] - maxLogit); // 각 로짓에 가장 큰값을 뺌으로서 로짓값이 큰 경우에도 오버플로우가 발생하는 것을 방지한다.
            sum += probabilities[index];
        }
        for (int index = 0; index < probabilities.length; index++) {
            probabilities[index] /= sum;
        }
        return probabilities; // 최종적으로 softmax를 적용하고 나면, 각 라벨의 값이 확률로 변환된다.
    }

    // 상대 경로를 현재 실행 디렉터리 기준의 절대 경로로 바꾸고 실제 일반 파일인지 확인한다.
    // Docker에서 volume mount를 빠뜨린 경우에도 어떤 경로가 문제인지 바로 확인할 수 있다.
    private Path requireRegularFile(String path, String description) {
        Path resolvedPath = Path.of(path).toAbsolutePath().normalize();
        if (!Files.isRegularFile(resolvedPath)) {
            throw new IllegalStateException(description + " 파일을 찾을 수 없습니다: " + resolvedPath);
        }
        return resolvedPath;
    }

    // @PreDestroy는 Spring 빈 종료 시 이 메서드를 호출한다. tokenizer와 session은 네이티브
    // 자원을 소유하므로 명시적으로 닫는다. 공용 OrtEnvironment는 별도로 닫지 않는다.
    @Override
    @PreDestroy
    public void close() {
        tokenizer.close();
        try {
            session.close();
        } catch (OrtException e) {
            throw new IllegalStateException("KR-FinBert-SC ONNX 세션을 종료할 수 없습니다.", e);
        }
    }

    // 서로 항상 같은 배치 크기와 토큰 길이를 가져야 하는 세 입력 행렬을 하나의 값으로 묶는다.
    private record BatchInputs(long[][] inputIds, long[][] attentionMask, long[][] tokenTypeIds) {
    }
}
