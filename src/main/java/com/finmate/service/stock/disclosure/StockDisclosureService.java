package com.finmate.service.stock.disclosure;

import com.finmate.domain.news.NewsSentiment;
import com.finmate.domain.news.dto.NewsItem;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.disclosure.StockDisclosureRefreshState;
import com.finmate.domain.stock.dto.disclosure.StockDisclosureItem;
import com.finmate.domain.stock.dto.disclosure.StockDisclosureResponse;
import com.finmate.infra.kis.stock.disclosure.KisStockDisclosureClient;
import com.finmate.infra.kis.stock.disclosure.KisStockDisclosureResponse.DisclosureTitle;
import com.finmate.repository.stock.StockRepository;
import com.finmate.repository.stock.disclosure.StockDisclosureRefreshStateRepository;
import com.finmate.repository.stock.disclosure.StockDisclosureRepository;
import com.finmate.service.news.FinBertNewsSentimentAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

// KIS 시황/공시 제목을 종목별로 수집하고 신규 항목만 FinBERT로 분석해 최근 목록을 제공한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class StockDisclosureService {
    // KIS 작성일자와 작성시간을 LocalDateTime으로 변환할 때 사용하는 형식이다.
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");

    // 요청받은 종목과 시장 유형을 확인한다.
    private final StockRepository stockRepository;
    // 종목별 시황/공시정보가 저장되어 있는 레파지터리. 해당 레파지터리에 저장되어 있는 정보별 식별자 키를 기반으로 중복된 정보를 저장하는 것을 방지한다.
    private final StockDisclosureRepository disclosureRepository;
    // 종목별 마지막 정상 갱신시각을 조회한다.
    private final StockDisclosureRefreshStateRepository refreshStateRepository;
    // KIS 종합 시황/공시 제목 API를 호출한다.
    private final KisStockDisclosureClient disclosureClient;
    // 실제 분석이 필요할 때만 FinBERT 모델을 지연 로딩하여 시황/공시정보들에 대해 감성분석을 실시한다.
    private final ObjectProvider<FinBertNewsSentimentAnalyzer> sentimentAnalyzerProvider;
    // 신규 항목과 갱신 상태를 하나의 트랜잭션으로 저장한다.
    private final StockDisclosurePersistenceService persistenceService;
    // 같은 JVM에서 동일 종목의 동시 요청이 KIS와 FinBERT를 중복 실행하지 않도록 공유한다. 즉 KIS API에 동일 종목에 대한 시황/공시정보를 여러 사용자가 동시에 요청하는것을 방지한다.
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> inFlightRefreshes = new ConcurrentHashMap<>();

    @Value("${finmate.disclosure.refresh-interval-minutes:10}")
    // 동일 종목에 대한 시황/공시정보를 갱신할 TTL
    private long refreshIntervalMinutes;

    // 지원 시장이면 필요할 때 데이터를 갱신하고 DB에 저장된 최신 10건을 반환한다.
    public StockDisclosureResponse getDisclosures(Long stockId) {
        // 존재하지 않는 종목은 KIS API를 호출하지 않고 404로 처리한다.
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다."));

		// 시황/공시정보를 지원하는 종목에 대해서는 TTL을 검사해서 시황/공시정보를 갱신한다.
        if (supportsDisclosure(stock)) {
            refreshIfNeeded(stock);
        }

        // 마지막 정상 갱신시각은 화면의 업데이트 시각으로 사용한다.
        LocalDateTime updatedAt = refreshStateRepository.findByStock_Id(stockId)
                .map(StockDisclosureRefreshState::getLastSuccessfulRefreshAt)
                .orElse(null);
        // DB에 누적된 항목 중 작성시각 기준 최신 10건만 응답한다.
        List<StockDisclosureItem> items = disclosureRepository
                .findTop10ByStock_IdOrderByDisclosedAtDescIdDesc(stockId)
                .stream()
                .map(StockDisclosureItem::from)
                .toList();
		// 프론트에 전달할 데이터들만 DTO에 담아서 리턴한다.
        return new StockDisclosureResponse(stock.getId(), stock.getNameKo(), updatedAt, items);
    }

	// 해당 종목이 시황/공시정보를 제공하는지를 검사한다.
    private boolean supportsDisclosure(Stock stock) {
        // KIS 국내 시황/공시 API는 현재 KOSPI와 KOSDAQ 종목에만 적용한다.
        return stock.getMarketType() == StockMarketType.KOSPI
                || stock.getMarketType() == StockMarketType.KOSDAQ;
    }

    // 종목의 마지막 시황/공시정보 갱신시각이랑 TTL을 비교하면서 시황/공시정보 갱신이 필요한지를 검사한다.
    private void refreshIfNeeded(Stock stock) {
        LocalDateTime now = LocalDateTime.now();
        // 설정된 갱신 간격(TTL)이 지나지 않았다면 기존 DB 데이터를 사용한다.
        if (isFresh(stock.getId(), now)) {
            return;
        }

		// 종목의 시황/공시정보를 갱신하기 위한 CompletableFuture.
		// CompletableFuture는 작업을 우선 진행하면서 다른 작업을 비동기로 처리하고, 작업이 완료되면 해당 객체에 작업 결과를 저장하는 객체이다.
        CompletableFuture<Void> refresh = new CompletableFuture<>();
		// inFlightRefreshes에 해당 종목에 대한 갱신작업이 예약되어 있지 않은 경우에는 작업을 등록한다.
        CompletableFuture<Void> existingRefresh = inFlightRefreshes.putIfAbsent(stock.getId(), refresh);
		// 이미 등록되어 있던 갱신작업이 존재하는 경우
        if (existingRefresh != null) {
            // 이미 등록되어 있던 갱신작업이 끝날때까지 대기하며, 기존 갱신작업이 종료되면 해당 갱신정보를 활용한다.
            await(existingRefresh);
            return;
        }

		// 이미 등록되어 있던 갱신작업이 존재하지 않는 경우에는 갱신작업을 시작한다.
        try {
            // 대기하는 동안 다른 요청이 갱신했을 수 있으므로 실행 직전에 신선도를 다시 확인한다.
            if (!isFresh(stock.getId(), LocalDateTime.now())) {
				// 만약 TTL이 지났다면, 해당 종목의 시황/공시정보 갱신작업을 수행한다.
                refresh(stock, now);
            }
			// 갱신작업이 완료되면, 갱신완료 신호를 보낸다.
            refresh.complete(null);
        } catch (RuntimeException exception) {
            // 외부 API나 모델 장애가 화면 전체 장애로 번지지 않도록 마지막 정상 데이터를 유지한다.
            log.warn("KIS 시황/공시 갱신에 실패해 기존 저장 데이터를 반환합니다. stockId={}, symbol={}",
                    stock.getId(), stock.getSymbol(), exception);
            refresh.complete(null);
        } finally {
			// 작업이 완료되면 등록되어 있던 작업을 삭제한다.
            inFlightRefreshes.remove(stock.getId(), refresh);
        }
    }

	// 종목의 마지막 시황/공시정보 갱신시각과 TTL을 비교하여 TTL이 지났는지를 검사한다. TTL이 지났으면 KIS API를 호출하여 정보를 갱신하고, 그렇지 않은 경우에는 DB에 저장된 정보를 재활용한다.
    private boolean isFresh(Long stockId, LocalDateTime now) {
        // 잘못된 0 이하 설정값도 최소 1분으로 보정한다.
        Duration refreshInterval = Duration.ofMinutes(Math.max(1, refreshIntervalMinutes)); // TTL
        return refreshStateRepository.findByStock_Id(stockId)
                .map(StockDisclosureRefreshState::getLastSuccessfulRefreshAt)
                .map(refreshedAt -> refreshedAt.plus(refreshInterval).isAfter(now)) // 아직 TTL이 지나지않았으면 True
                .orElse(false); // TTL이 지났으면 False
    }

	// KIS API를 호출하여 종목의 시황/공시정보를 갱신한다.
    private void refresh(Stock stock, LocalDateTime refreshedAt) {
        List<DisclosureCandidate> candidates = disclosureClient.fetchLatestTitles(stock.getSymbol()).output().stream()
                .map(this::toCandidate) // KIS API의 응답 DTO를 애플리케이션 내부에서 사용하는 DisclosureCandidate 객체로 변환한다.
                .flatMap(java.util.Optional::stream) // Optional에서 비어있는 객체들은 필터링하고, 실제 DisclosureCandidate 객체들만 남긴다.
                // 한 응답 안에서 같은 외부 식별자가 반복되면 첫 항목만 유지한다.
				// 이번에 KIS API에서 조회한 시황/공시정보들중 중복되는 시황/공시 정보를 중복을 제거하여 하나씩만 남긴다.
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap( // 각 식별자(externalKey)를 기반으로 Map을 생성한다.
                                DisclosureCandidate::externalKey, // 각 DisclosureCandidate 별로 식별자 키를 생성하고, 이를 Map의 key로 사용한다.
                                candidate -> candidate, // Map의 value로 DisclosureCandidate 객체 자체를 사용한다.
                                (first, ignored) -> first, // 하나의 키에 대해서 중복된 정보가 있으면 첫번째 값만 남긴다.
                                LinkedHashMap::new),
                        values -> new ArrayList<>(values.values()))); // 이렇게 생성된 Map에서 DisclosureCandidate정보만 추출해서 List를 생성한다.


        // DB에 이미 저장된 식별자정보를 조회하고, 이번에 새로 조회한 시황/공시정보들중 기존 DB에 이미 저장되어 있던 정보들은 필터링한다.
        Set<String> existingKeys = candidates.isEmpty()
                ? Set.of()
                : disclosureRepository.findExistingExternalKeys(
                        stock.getId(),
                        candidates.stream().map(DisclosureCandidate::externalKey).toList());
        // 아직 저장되지 않은 제목만 이후 FinBERT 분석 대상으로 남긴다.
        List<DisclosureCandidate> unseen = candidates.stream()
                .filter(candidate -> !existingKeys.contains(candidate.externalKey()))
                .toList();

        // 새로운 시황/공시정보들은 FinBERT 감성분석 모델에 넣어 감성정보를 추가한 새로운 AnalyzedDisclosure DTO를 생성한다.
        List<AnalyzedDisclosure> analyzed = analyze(unseen);
		// persistenceService를 호출하여 새로운 시황/공시정보를 레퍼지터리에 저장한다.
        persistenceService.storeSuccessfulRefresh(stock, analyzed, refreshedAt);
    }

    // 기존 뉴스 분석기와 같은 입력 형식을 사용해 시황/공시 제목만 한 배치로 분석한다.
    private List<AnalyzedDisclosure> analyze(List<DisclosureCandidate> candidates) {
        // 신규 제목이 없으면 모델을 로딩하거나 호출하지 않는다.
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<NewsItem> inputs = candidates.stream()
                // 원문과 요약이 없으므로 제목과 작성시각만 NewsItem에 담는다.
                .map(candidate -> new NewsItem(
                        candidate.title(),
                        null,
                        null,
                        "",
                        candidate.disclosedAt().toString()))
                .toList();
        // 모든 신규 제목을 한 번의 배치로 FinBERT에 전달한다.
        List<NewsItem> analyzedItems = sentimentAnalyzerProvider.getObject().analyze(inputs);
        if (analyzedItems.size() != candidates.size()) {
            throw new IllegalStateException("공시 감성 분석 결과 개수가 입력 개수와 다릅니다.");
        }

        List<AnalyzedDisclosure> result = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            // 모델 출력 순서는 입력 순서와 같다는 분석기 계약에 따라 결과를 결합한다.
            DisclosureCandidate candidate = candidates.get(index);
            NewsSentiment sentiment = analyzedItems.get(index).sentiment();
            if (sentiment == null) {
                throw new IllegalStateException("공시 감성 분석 결과는 필수입니다.");
            }
			// 기존 시황/공시정보에 감성분석정보를 추가해 AnalyzedDisclosure DTO를 생성하여 리스트에 추가한다.
            result.add(new AnalyzedDisclosure(
                    candidate.externalKey(),
                    candidate.contentSerialNumber(),
                    candidate.providerCode(),
                    candidate.title(),
                    candidate.source(),
                    candidate.categoryCode(),
                    candidate.disclosedAt(),
                    sentiment));
        }
        return List.copyOf(result);
    }

	// KIS API의 응답으로 받은 DisclosureTitle DTO를 애플리케이션 내부에서 사용하는 DisclosureCandidate 객체로 변환한다.
    private java.util.Optional<DisclosureCandidate> toCandidate(DisclosureTitle title) {
        // 저장 전에 공백 표현을 정규화해 중복 키와 화면 표시가 흔들리지 않게 한다.
        String normalizedTitle = normalize(title.title());
        String source = normalize(title.source());
        if (normalizedTitle.isBlank()) {
            // 화면과 감성 분석에 사용할 제목이 없으면 저장 대상에서 제외한다.
            return java.util.Optional.empty();
        }
        if (source.isBlank()) {
            // 자료원이 비어 있어도 제목은 버리지 않고 KIS 기본 출처로 표시한다.
            source = "KIS";
        }

        try {
            LocalDateTime disclosedAt = parseDateTime(title.dataDate(), title.dataTime());
            String externalKey = externalKey(title, normalizedTitle, source, disclosedAt);
            return java.util.Optional.of(new DisclosureCandidate(
                    externalKey,
                    normalizeToNull(title.contentSerialNumber()),
                    normalizeToNull(title.providerCode()),
                    normalizedTitle,
                    source,
                    normalizeToNull(title.categoryCode()),
                    disclosedAt));
        } catch (DateTimeParseException exception) {
            log.warn("KIS 시황/공시의 작성일시를 해석할 수 없어 건너뜁니다. date={}, time={}, title={}",
                    title.dataDate(), title.dataTime(), normalizedTitle);
            return java.util.Optional.empty();
        }
    }

    private LocalDateTime parseDateTime(String dateValue, String timeValue) {
        // KIS 값에 구분자나 앞자리 접두사가 있어도 마지막 YYYYMMDD/HHmmss 숫자를 사용한다.
        String dateDigits = digits(dateValue);
        String timeDigits = digits(timeValue);
        if (dateDigits.length() > 8) {
            dateDigits = dateDigits.substring(dateDigits.length() - 8);
        }
        if (timeDigits.length() > 6) {
            timeDigits = timeDigits.substring(timeDigits.length() - 6);
        }
        if (timeDigits.isBlank()) {
            timeDigits = "000000";
        }
        timeDigits = String.format("%6s", timeDigits).replace(' ', '0');
        return LocalDateTime.of(
                LocalDate.parse(dateDigits, DATE_FORMATTER),
                LocalTime.parse(timeDigits, TIME_FORMATTER));
    }

	// 종목별 시황/공시정보를 DB에 중복으로 저장하지 않도록 각 시황/공시정보를 구분할 수 있는 식별자를 생성하는 메서드
    private String externalKey(
            DisclosureTitle title,
            String normalizedTitle,
            String source,
            LocalDateTime disclosedAt) {
		// 시황/공시정보 제공원 + 제목 일련번호 + 제공자 일련번호 정보를 합쳐서 UUID로 암호화한다. 즉 동일 정보에 대해서는 같은값이, 서로 다른 정보에 대해서는 다른값이 나오게 된다.
        String serial = normalize(title.contentSerialNumber());
        String provider = normalize(title.providerCode());
        if (!serial.isBlank()) {
            // KIS가 제공한 식별자가 있으면 재조회에도 안정적인 기본 중복 키로 사용한다.
            return (provider.isBlank() ? "KIS" : provider) + ":" + serial;
        }
        // 식별자가 없는 예외 응답도 동일 내용이 다시 저장되지 않도록 결정적 UUID를 만든다.
        String fingerprint = source + "|" + normalizedTitle + "|" + disclosedAt;
        return "KIS:" + UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8));
    }

    private String digits(String value) {
        // 날짜와 시간 파싱에 필요한 숫자만 남긴다.
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private String normalize(String value) {
        // null을 빈 문자열로 바꾸고 연속 공백을 하나로 정리한다.
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeToNull(String value) {
        // 선택 입력값은 공백 문자열 대신 null로 저장한다.
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private void await(CompletableFuture<Void> refresh) {
        // 동일 종목의 선행 갱신이 끝날 때까지 현재 요청을 대기시킨다.
        try {
            refresh.join();
        } catch (CompletionException ignored) {
            // 갱신 실패는 호출 스레드가 이미 마지막 정상 DB 데이터로 복구한다.
        }
    }

    // KIS 원본을 정규화하고 중복 식별자를 부여한 FinBERT 분석 전 레코드이다.
    private record DisclosureCandidate(
            String externalKey,
            String contentSerialNumber,
            String providerCode,
            String title,
            String source,
            String categoryCode,
            LocalDateTime disclosedAt
    ) {
    }

    // 외부 호출·모델 추론 결과를 트랜잭션 저장 서비스로 전달하는 레코드이다.
    public record AnalyzedDisclosure(
            String externalKey,
            String contentSerialNumber,
            String providerCode,
            String title,
            String source,
            String categoryCode,
            LocalDateTime disclosedAt,
            NewsSentiment sentiment
    ) {
    }
}
