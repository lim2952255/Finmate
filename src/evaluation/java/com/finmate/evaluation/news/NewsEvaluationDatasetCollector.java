package com.finmate.evaluation.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.finmate.domain.news.dto.NewsItem;
import com.finmate.infra.naver.news.NaverNewsClient;
import com.finmate.infra.naver.news.NaverNewsProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 시장 6개와 업종이 다른 대표 종목 6개의 후보를 네이버 API에서 한 번 수집해 JSON으로 고정한다.
 * 이후 전략 평가는 이 파일만 읽으므로 전략마다 서로 다른 검색 결과가 들어가는 것을 방지한다.
 * 따라서 모든 전략을 동일한 기사목록을 통해 평가한다.
 *
 * ./gradlew -PwithEvaluation collectNewsEvaluationDataset의 실제 코드 진입점이다.
 */
public final class NewsEvaluationDatasetCollector {
    // 파일 이름과 collectedAt을 한국 시간 기준으로 맞춘다.
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    // 같은 주제를 여러 날짜에 수집해도 덮어쓰지 않도록 초 단위 시각을 파일 이름에 넣는다.
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    // Gradle에 -PoutputDir을 전달하지 않았을 때 사용하는 프로젝트 기준 기본 출력 위치다.
    private static final Path DEFAULT_OUTPUT_DIRECTORY =
            Path.of("evaluation/news-ranking/datasets/raw");

    // main()만 사용하는 실행 클래스이므로 인스턴스 생성을 막는다. 따라서 생성자를 private으로 설정한다.
    private NewsEvaluationDatasetCollector() {
    }

    public static void main(String[] args) throws IOException {
        // build.gradle의 JavaExec task가 -PoutputDir 값을 첫 번째 인자로 전달한다.
        // 인자가 없으면 Git에서 제외된 기본 datasets/raw 디렉터리를 사용한다.
        Path outputDirectory = args.length > 0
                ? Path.of(args[0])
                : DEFAULT_OUTPUT_DIRECTORY;
        // 상위 디렉터리가 없어도 JSON 파일을 쓸 수 있도록 필요한 경로를 모두 생성한다.(상위디렉터리까지 모두 생성한다)
        Files.createDirectories(outputDirectory);

        // 자바 객체를 Json문자열로 변환해주는 mapper
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules() // Json 관련 모듈들을 찾아 Java Time 타입을 Json으로 적절히 처리할 수 있도록 설정한다.
                .enable(SerializationFeature.INDENT_OUTPUT); // Json 문자열을 사람이 읽기 좋은 형태로 들여쓰기하여 저장한다.
        // Spring DI를 사용하지 않는 독립 실행이므로 client와 properties를 직접 만든다.
		// Spring boot를 사용하는 경우에는 스프링 빈으로 객체가 생성되어 저장되지만, 스프링부트를 사용하지 않는 경우에는 직접 객체를 생성해야 한다.
        NaverNewsClient naverNewsClient = createNaverNewsClient(objectMapper);

        // 한 번의 실행에서 12개 주제를 연속 수집해 주제 사이의 수집 시각 차이를 작게 유지한다.
        for (EvaluationSubject subject : EvaluationSubject.values()) {
            OffsetDateTime collectedAt = OffsetDateTime.now(KOREA_ZONE); // 네이버 API를 호출하여 데이터를 받아오는 시각을 저장한다.
            // 운영과 같은 NaverNewsClient를 재사용해 해당 검색어의 최대 80개 후보를 가져온다.
            List<NewsItem> candidates = naverNewsClient.searchRelevantCandidates(subject.query());
            // datasetId는 라벨 파일과 결과 CSV에서 이 후보 집합을 연결하는 키다.
            String timestamp = FILE_TIMESTAMP.format(collectedAt); // 네이버 API를 호출한 시간을 기준으로 TimeStamp를 생성한다.
            String datasetId = subject.name() + "-" + timestamp; // 주제와 timestamp를 기준으로 datasetId를 생성한다.
            NewsEvaluationDataset dataset = new NewsEvaluationDataset(
                    datasetId,
                    subject.name(),
                    subject.displayName(),
                    subject.subjectType(),
                    subject.query(),
                    collectedAt,
                    subject.keywords(),
                    candidates);

            // 예: 20260906-153000-samsung.json. 주제별 JSON 하나에 후보 전체를 저장한다.
            Path outputFile = outputDirectory.resolve(
                    timestamp + "-" + subject.name().toLowerCase() + ".json");
            objectMapper.writeValue(outputFile.toFile(), dataset); // json파일을 저장한다.
            System.out.printf("평가 데이터 저장: %s (%d개 후보)%n", outputFile, candidates.size());
        }
    }

	// NaverNewsClient 객체를 직접 생성한다.
    private static NaverNewsClient createNaverNewsClient(ObjectMapper objectMapper) {
        // 운영에서는 Spring이 application.properties를 바인딩하지만, 평가 프로그램에는 Spring context가
        // 없으므로 환경변수를 읽어 NaverNewsProperties를 직접 채운다.
        NaverNewsProperties properties = new NaverNewsProperties();
        properties.setBaseUrl(EvaluationEnvironment.optional(
                "NAVER_NEWS_BASE_URL",
                "https://naverapihub.apigw.ntruss.com"));
        properties.setClientId(EvaluationEnvironment.required("NAVER_API_HUB_CLIENT_ID"));
        properties.setClientSecret(EvaluationEnvironment.required("NAVER_API_HUB_CLIENT_SECRET"));
        // 이 client는 데이터를 파일로 고정하는 동안에만 사용되며 DB 캐시를 거치지 않는다.
        return new NaverNewsClient(properties, objectMapper);
    }
}
