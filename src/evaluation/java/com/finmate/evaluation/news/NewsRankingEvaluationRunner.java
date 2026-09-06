package com.finmate.evaluation.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.finmate.domain.news.dto.NewsItem;
import com.finmate.service.news.NewsRankingType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 뉴스 랭킹 전략들의 품질을 비교하는 오프라인 평가 프로그램이다.
 *
 * ./gradlew -PwithEvaluation evaluateNewsRanking 을 실행하면 Gradle이 이 클래스의 main()을 호출한다.
 *
 *
 * 전체 평가 순서는 다음과 같다.
 *
 * 1. 미리 수집해 둔 뉴스 후보 JSON을 읽는다.
 * 2. 같은 후보에 모든 랭킹 전략과 임계값 조합을 실행한다.
 * 3. 전략들이 선택한 기사만 모아 OpenAI Judge로 관련도와 동일 사건 여부를 판정한다.
 * 4. 동일한 Judge 판정을 이용해 각 전략의 품질 지표를 계산한다.
 * 5. 데이터셋별 상세 결과와 전략별 평균 결과를 CSV로 저장한다.
 *
 *
 * 이미 저장된 JSON만 사용하므로 평가 중에는 NAVER 뉴스 API를 다시 호출하지 않는다.
 * 또한 Spring Boot 애플리케이션을 시작하지 않으며 DB와 Redis에도 연결하지 않는다.
 */
public final class NewsRankingEvaluationRunner {
    // 운영 화면이 최대 10개의 뉴스를 보여주므로 평가에서도 같은 개수만 선택한다.
    private static final int RESULT_LIMIT = 10;
    // -Pdataset을 생략했을 때 평가할 뉴스 후보 JSON들이 저장된 기본 디렉터리다.
    private static final Path DEFAULT_DATASET_PATH =
            Path.of("evaluation/news-ranking/datasets/raw");
    // OpenAI Judge 판정 결과를 저장하는 디렉터리다. 같은 평가 조건이면 저장된 판정을 재사용한다.
    private static final Path LABEL_DIRECTORY =
            Path.of("evaluation/news-ranking/labels/generated");
    // 데이터셋별 상세 CSV와 전략별 평균 CSV를 저장하는 디렉터리다.
    private static final Path RESULT_DIRECTORY =
            Path.of("evaluation/news-ranking/results");
    // 실행할 때마다 새 결과 파일을 만들 수 있도록 현재 시각을 파일 이름 형식으로 변환한다.
    private static final DateTimeFormatter RESULT_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    // 상태를 가지는 객체가 아니라 main()으로만 실행하는 클래스이므로 인스턴스 생성을 막는다.
    private NewsRankingEvaluationRunner() {
    }

    public static void main(String[] args) throws Exception {
        // -Pdataset을 지정하면 Gradle이 그 값을 args[0]으로 전달한다.
        // JSON 파일이면 해당 파일 하나만 평가하고, 디렉터리이면 바로 아래의 모든 JSON을 평가한다.
        // 값을 지정하지 않으면 DEFAULT_DATASET_PATH 아래의 모든 JSON을 사용한다.
        Path datasetPath = args.length > 0 ? Path.of(args[0]) : DEFAULT_DATASET_PATH;
        List<Path> datasetFiles = findDatasetFiles(datasetPath);
        if (datasetFiles.isEmpty()) {
            throw new IllegalStateException("평가할 데이터셋 JSON이 없습니다: " + datasetPath.toAbsolutePath());
        }

        // 첫 평가에서도 파일을 바로 저장할 수 있도록 필요한 출력 디렉터리를 만든다.
        // 이미 존재하는 디렉터리라면 createDirectories()는 아무 작업도 하지 않는다.
        Files.createDirectories(LABEL_DIRECTORY);
        Files.createDirectories(RESULT_DIRECTORY);

        // findAndRegisterModules(): OffsetDateTime 같은 Java 시간 타입을 JSON으로 변환할 모듈을 자동 등록한다.
        // INDENT_OUTPUT: 생성되는 Judge 라벨 JSON을 사람이 읽기 쉬운 들여쓰기 형식으로 저장한다.
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT);

        // 실제 판정은 OpenAI Responses API를 사용하는 구현체가 담당한다.
        // 아래 평가 흐름은 NewsJudge 인터페이스만 사용하므로 Judge 구현이 바뀌어도 그대로 재사용할 수 있다.
        NewsJudge judge = new OpenAiNewsJudge(objectMapper);

        // 각 데이터셋에서 계산한 전략별 결과를 모두 모은다.
        // 반복 처리가 끝난 뒤 이 목록으로 상세 CSV와 평균 CSV를 각각 한 번씩 생성한다.
        List<NewsRankingMetrics.MetricRow> metricRows = new ArrayList<>();

        // StrategySet은 평가할 모든 전략과 임계값 조합을 준비한다.
        // 무거운 임베딩 모델은 여기서 한 번만 만들고 모든 데이터셋에서 공유하며,
        // try-with-resources가 평가 종료 시 모델과 형태소 분석기 자원을 안전하게 정리한다.
        try (NewsEvaluationStrategySet strategySet = new NewsEvaluationStrategySet()) {
            for (Path datasetFile : datasetFiles) {
                // Collector가 저장한 JSON 한 개를 평가용 데이터 객체로 변환한다.
                NewsEvaluationDataset dataset =
                        objectMapper.readValue(datasetFile.toFile(), NewsEvaluationDataset.class);

                // 모든 전략이 정확히 같은 뉴스 후보와 키워드를 받게 하여 결과를 공정하게 비교한다.
                List<NewsStrategyRun> runs = runStrategies(dataset, strategySet.variants());

                // 원본 후보 전체를 Judge에 보내지 않고, 전략 결과에 한 번이라도 포함된 기사만 중복 없이 모은다.
                // 이렇게 하면 비교에 필요하지 않은 기사 판정을 줄여 API 비용과 평가 시간을 절약할 수 있다.
                List<NewsItem> union = topResultUnion(runs);

                // 호환되는 기존 라벨 파일이 있으면 재사용하고, 없거나 조건이 달라졌으면 Judge를 새로 호출한다.
                NewsJudgeReport report = loadOrCreateJudgeReport(
                        dataset,
                        union,
                        judge,
                        objectMapper);

                // 지표를 계산할 때 기사 ID로 판정 결과를 바로 찾을 수 있도록 Map 형태의 인덱스를 만든다.
                Map<String, NewsJudgeLabel> labelsById = labelsByArticleId(report);

                // 모든 전략에 같은 Judge 판정을 적용해 nDCG, 중복률, Yield 등의 지표를 계산한다.
                for (NewsStrategyRun run : runs) {
                    metricRows.add(NewsRankingMetrics.evaluate(
                            dataset.datasetId(),
                            run,
                            labelsById));
                }
                System.out.printf("평가 완료: %s (%d개 judge 기사)%n", dataset.datasetId(), union.size());
            }
        }

        // 상세 파일과 요약 파일에 같은 실행 시각을 사용하여 두 파일이 한 번의 평가 결과임을 표시한다.
        String resultTimestamp = RESULT_TIMESTAMP.format(LocalDateTime.now());
        Path detailFile = RESULT_DIRECTORY.resolve(
                "news-ranking-" + resultTimestamp + "-detail.csv");
        Path summaryFile = RESULT_DIRECTORY.resolve(
                "news-ranking-" + resultTimestamp + "-summary.csv");

        // detail: 데이터셋마다 계산한 개별 결과
        // summary: 같은 전략과 임계값 조합의 전체 데이터셋 평균
        writeMetricCsv(detailFile, metricRows);
        writeSummaryCsv(summaryFile, metricRows);
        System.out.println("상세 평가 결과 저장: " + detailFile);
        System.out.println("전략별 평균 결과 저장: " + summaryFile);
    }

    /**
     * 하나의 데이터셋에 모든 전략과 임계값 조합을 실행한다.
     * 각 전략이 선택한 기사와 순수 랭킹 처리 시간을 {@link NewsStrategyRun}으로 묶어 반환한다.
     */
    private static List<NewsStrategyRun> runStrategies(
            NewsEvaluationDataset dataset,
            List<NewsStrategyVariant> variants) {
        List<NewsStrategyRun> runs = new ArrayList<>();
        for (NewsStrategyVariant variant : variants) {
            // nanoTime()은 현재 날짜가 아니라 두 시점 사이의 경과 시간을 재는 데 적합한 시계다.
            long startedAt = System.nanoTime();
            List<NewsItem> items = variant.strategy().rank(
                    dataset.candidates(),
                    dataset.keywords(),
                    RESULT_LIMIT);

            // JSON 읽기나 Judge API 호출 시간은 제외하고 rank() 자체의 실행 시간만 측정한다.
            // nanoTime()의 단위가 나노초이므로 CSV에서는 비교하기 쉬운 밀리초로 변환한다.
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            runs.add(new NewsStrategyRun(
                    variant.strategy().type(),
                    variant.similarityThreshold(),
                    items,
                    elapsedMillis));
        }
        return List.copyOf(runs);
    }

    /**
     * 모든 전략이 선택한 기사들의 합집합을 만든다.
     * 같은 기사가 여러 전략이나 임계값 결과에 등장해도 Judge에는 한 번만 전달한다.
	 * 동일 기사에 대해서 반복적으로 LLM judge를 수행하는 것을 방지하여 비용과 시간을 절약한다.
     */
    private static List<NewsItem> topResultUnion(List<NewsStrategyRun> runs) {
        // LinkedHashMap을 사용하면 처음 발견한 순서를 유지하면서 articleId 기준으로 중복을 제거할 수 있다.
        Map<String, NewsItem> uniqueArticles = new LinkedHashMap<>();
        for (NewsStrategyRun run : runs) {
            for (NewsItem item : run.items()) {
                // putIfAbsent()는 같은 articleId가 이미 있으면 기존 기사를 그대로 유지한다.
                uniqueArticles.putIfAbsent(EvaluationArticleIds.from(item), item);
            }
        }
        return List.copyOf(uniqueArticles.values());
    }

    /**
     * 현재 평가 조건과 호환되는 Judge 라벨을 불러오거나 새로 생성한다.
     * 캐시가 호환되면 OpenAI API를 호출하지 않으므로 같은 평가를 반복할 때 비용을 줄일 수 있다.
     */
    private static NewsJudgeReport loadOrCreateJudgeReport(
            NewsEvaluationDataset dataset,
            List<NewsItem> articles,
            NewsJudge judge,
            ObjectMapper objectMapper) throws IOException, InterruptedException {
        // 데이터셋 ID별로 파일을 나누어 서로 다른 종목이나 시장 주제의 판정이 섞이지 않게 한다.
        Path labelFile = LABEL_DIRECTORY.resolve(dataset.datasetId() + "-openai.json");
        if (Files.isRegularFile(labelFile)) {
            NewsJudgeReport cached = objectMapper.readValue(labelFile.toFile(), NewsJudgeReport.class);
            // 모델, 프롬프트 버전, 판정할 기사 ID 집합이 모두 같아야 이전 판정을 재사용할 수 있다.
            if (judge.isCompatible(cached, articles)) {
                return cached;
            }
        }

        // 저장된 파일이 없거나 현재 조건과 맞지 않으면 새 판정을 생성하여 기존 경로에 저장한다.
        NewsJudgeReport created = judge.judge(dataset, articles);
        objectMapper.writeValue(labelFile.toFile(), created);
        return created;
    }

    /**
     * Judge가 반환한 라벨 목록을 {@code articleId -> label} 형태의 조회용 Map으로 변환한다.
     */
    private static Map<String, NewsJudgeLabel> labelsByArticleId(NewsJudgeReport report) {
        Map<String, NewsJudgeLabel> labels = new LinkedHashMap<>();
        for (NewsJudgeLabel label : report.articles()) {
            // 지표 계산 중 목록을 매번 순회하지 않고 기사 ID로 판정을 즉시 찾기 위한 인덱스다.
            labels.put(label.articleId(), label);
        }
        return Map.copyOf(labels);
    }

    /**
     * 사용자가 지정한 경로에서 평가할 JSON 파일 목록을 찾는다.
     * 파일 경로이면 그 파일 하나를, 디렉터리이면 바로 아래의 JSON 파일들을 이름순으로 반환한다.
     */
    private static List<Path> findDatasetFiles(Path path) throws IOException {
        // 특정 파일 하나를 지정했다면 디렉터리를 탐색하지 않고 바로 반환한다.
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }

        // 존재하지 않는 경로처럼 평가할 수 없는 입력은 빈 목록으로 반환한다.
        // main()이 빈 목록을 확인해 사용자가 이해하기 쉬운 오류 메시지를 만든다.
        if (!Files.isDirectory(path)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(path)) {
            // 하위 디렉터리까지 재귀 탐색하지 않고 현재 디렉터리 바로 아래의 JSON만 선택한다.
            // 파일 이름순으로 정렬하여 같은 입력이라면 언제 실행해도 처리 순서가 같게 한다.
            return files
                    .filter(file -> file.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static void writeMetricCsv(
            Path outputFile,
            List<NewsRankingMetrics.MetricRow> rows) throws IOException {
        // 첫 줄에는 열 이름을 쓰고, 이후에는 데이터셋 × 전략 × 임계값 조합마다 한 줄씩 기록한다.
        StringBuilder csv = new StringBuilder();
        csv.append("datasetId,strategy,threshold,ndcgAt10,redundancyRateAt10,")
                .append("relevantUniqueEventsAt10,yieldAt10,selectedCount,elapsedMillis\n");
        for (NewsRankingMetrics.MetricRow row : rows) {
            csv.append(csvValue(row.datasetId())).append(',')
                    .append(row.strategy()).append(',')
                    .append(formatThreshold(row.similarityThreshold())).append(',')
                    .append(format(row.ndcgAt10())).append(',')
                    .append(format(row.redundancyRateAt10())).append(',')
                    .append(row.relevantUniqueEventsAt10()).append(',')
                    .append(format(row.yieldAt10())).append(',')
                    .append(row.selectedCount()).append(',')
                    .append(row.elapsedMillis()).append('\n');
        }

        // CREATE_NEW를 사용하면 같은 이름의 파일이 이미 있을 때 덮어쓰지 않고 오류를 발생시킨다.
        Files.writeString(
                outputFile,
                csv,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
    }

    // 여러 주제와 날짜의 점수를 전략·threshold별 평균으로 묶어 최종 후보를 비교하기 쉽게 만든다.
    private static void writeSummaryCsv(
            Path outputFile,
            List<NewsRankingMetrics.MetricRow> rows) throws IOException {
        // 같은 전략이라도 threshold가 다르면 별도 실험이므로 StrategyConfiguration을 그룹 키로 사용한다.
        Map<StrategyConfiguration, List<NewsRankingMetrics.MetricRow>> rowsByConfiguration =
                new LinkedHashMap<>();
        for (NewsRankingMetrics.MetricRow row : rows) {
            // 전략 종류와 임계값이 모두 같아야 같은 실험 설정으로 묶는다.
            StrategyConfiguration configuration = new StrategyConfiguration(
                    row.strategy(),
                    row.similarityThreshold());
            // 해당 설정의 목록이 아직 없으면 새로 만든 뒤 현재 데이터셋의 결과를 추가한다.
            rowsByConfiguration.computeIfAbsent(configuration, ignored -> new ArrayList<>())
                    .add(row);
        }

        StringBuilder csv = new StringBuilder();
        csv.append("strategy,threshold,datasetCount,meanNdcgAt10,meanRedundancyRateAt10,")
                .append("meanRelevantUniqueEventsAt10,meanYieldAt10,meanElapsedMillis\n");
        for (Map.Entry<StrategyConfiguration, List<NewsRankingMetrics.MetricRow>> entry
                : rowsByConfiguration.entrySet()) {
            StrategyConfiguration configuration = entry.getKey();
            List<NewsRankingMetrics.MetricRow> strategyRows = entry.getValue();
            csv.append(configuration.strategy()).append(',')
                    .append(formatThreshold(configuration.similarityThreshold())).append(',')
                    .append(strategyRows.size()).append(',')
                    .append(format(average(strategyRows, NewsRankingMetrics.MetricRow::ndcgAt10))).append(',')
                    .append(format(average(strategyRows, NewsRankingMetrics.MetricRow::redundancyRateAt10))).append(',')
                    .append(format(average(strategyRows,
                            row -> row.relevantUniqueEventsAt10()))).append(',')
                    .append(format(average(strategyRows, NewsRankingMetrics.MetricRow::yieldAt10))).append(',')
                    .append(format(average(strategyRows, row -> row.elapsedMillis()))).append('\n');
        }
        Files.writeString(
                outputFile,
                csv,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
    }

    private static double average(
            List<NewsRankingMetrics.MetricRow> rows,
            java.util.function.ToDoubleFunction<NewsRankingMetrics.MetricRow> extractor) {
        // extractor가 각 행에서 평균을 낼 숫자를 선택한다.
        // 덕분에 nDCG, 중복률, Yield, 처리 시간이 모두 같은 평균 계산 코드를 사용할 수 있다.
        return rows.stream().mapToDouble(extractor).average().orElse(0.0);
    }

    private static String format(double value) {
        // Locale.ROOT를 사용해 실행 환경과 관계없이 소수점 구분자를 '.'으로 고정한다.
        // 평가 지표는 소수점 아래 네 자리까지 CSV에 기록한다.
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private static String formatThreshold(Double value) {
        // 유사도 임계값을 사용하지 않는 KEYWORD 전략은 threshold 열을 빈 칸으로 기록한다.
        return value == null ? "" : format(value);
    }

    private static String csvValue(String value) {
        // CSV 문자열은 큰따옴표로 감싸고, 값 안의 큰따옴표는 두 번 반복해야 한다.
        // 예: 삼성 "전자" -> "삼성 ""전자"""
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    // 요약 CSV에서 결과를 그룹화할 때 사용하는 키다.
    // 전략이 같아도 similarityThreshold가 다르면 서로 다른 실험으로 취급한다.
    private record StrategyConfiguration(
            NewsRankingType strategy,
            Double similarityThreshold
    ) {
    }
}
