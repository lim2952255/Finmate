package com.finmate.service.stock.price;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.dto.detail.StockChartCandleData;
import com.finmate.domain.stock.dto.detail.StockChartInterval;
import com.finmate.domain.stock.dto.detail.StockMinuteCandleData;
import com.finmate.domain.stock.dto.detail.StockMinuteChartCache;
import com.finmate.domain.stock.market.StockMarketSchedule;
import com.finmate.domain.stock.market.StockMarketSchedules;
import com.finmate.infra.kis.parser.KisValueParser;
import com.finmate.infra.kis.stock.price.KisStockMinutePriceClient;
import com.finmate.infra.kis.stock.price.KisStockMinutePriceClient.DomesticMinutePriceItem;
import com.finmate.infra.kis.stock.price.KisStockMinutePriceClient.OverseasMinutePriceItem;
import com.finmate.repository.stock.metadata.OverseasStockMetadataRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// React 프론트에 전달할 분봉 차트 데이터를 생성하는 서비스
// 원본 1분봉은 Redis에서 조회하고, 캐시가 없거나 오래된 경우에만 KIS API를 호출하는 온디맨드 방식으로 동작한다.
// 3분봉, 5분봉, 15분봉은 별도로 저장하지 않고 원본 1분봉을 묶어서 계산한다.
@Service
public class StockMinuteChartService {
    // KIS API가 내려주는 날짜(yyyyMMdd)와 시간(HHmmss)을 파싱하기 위한 포맷
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");
    // 프론트 차트에서 날짜와 시간을 함께 식별할 수 있도록 전달하는 봉 시작시각 포맷
    private static final DateTimeFormatter CANDLE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    // 주말과 휴장일을 건너뛰며 최근 거래일을 찾을 때 조회할 최대 날짜 범위
    private static final int PREVIOUS_DATE_SEARCH_DAYS = 10;
    // 장중에는 새로운 1분봉이 계속 만들어지므로 Redis 스냅샷을 최대 1분 동안만 최신 데이터로 취급한다.
    private static final Duration TRADING_CACHE_FRESHNESS = Duration.ofMinutes(1);

    private final KisStockMinutePriceClient minutePriceClient; // KIS API로부터 분봉 데이터를 조회하는 클라이언트
    private final StockMinuteChartCacheService cacheService; // 원본 1분봉을 Redis에 캐싱하고 조회하는 서비스
    private final OverseasStockMetadataRepository overseasStockMetadataRepository; // 해외 종목의 KIS 거래소 코드를 조회하는 저장소
    private final Clock clock; // 국내와 해외 시장의 현재 시각을 일관되게 계산하기 위한 기준 시계
    // 같은 종목에 캐시 미스 요청이 동시에 들어왔을 때 KIS API가 중복 호출되는 것을 방지하는 종목별 Lock
    private final ConcurrentHashMap<Long, Object> fetchLocks = new ConcurrentHashMap<>();

    public StockMinuteChartService(KisStockMinutePriceClient minutePriceClient,
                                   StockMinuteChartCacheService cacheService,
                                   OverseasStockMetadataRepository overseasStockMetadataRepository) {
        this.minutePriceClient = minutePriceClient;
        this.cacheService = cacheService;
        this.overseasStockMetadataRepository = overseasStockMetadataRepository;
        this.clock = Clock.systemDefaultZone();
    }

    public List<StockChartCandleData> getCandles(Stock stock, StockChartInterval interval) {
        if (!interval.isMinute()) {
            throw new IllegalArgumentException("분봉 주기만 조회할 수 있습니다.");
        }
        // Redis에서 캐시를 조회하고, 캐시가 없거나 현재 시장 상태에 비해 오래됐다면 KIS API로 다시 조회한다.
        StockMinuteChartCache cache = loadCache(stock);
        // 장중에는 직전 거래일과 오늘을 이어서 보여주고, 장 마감·주말·휴장일에는 마지막 거래일만 보여준다.
        List<StockMinuteCandleData> displayCandles = selectDisplayCandles(stock, cache.candles());
        // KIS에서 받은 원본은 모두 1분봉이므로 요청한 간격에 따라 1·3·5·15분 단위로 묶어서 반환한다.
        return aggregate(displayCandles, interval.getMinuteSize());
    }

    private StockMinuteChartCache loadCache(Stock stock) {
        // Redis에 저장된 분봉 스냅샷이 현재 시장 상태에서도 사용할 수 있는지 먼저 검사한다.
        StockMinuteChartCache cached = cacheService.get(stock.getId())
                .filter(value -> isCacheUsable(stock, value))
                .orElse(null);
        if (cached != null) {
            return cached;
        }
        // 캐시 미스가 동시에 발생해도 같은 종목은 하나의 요청만 KIS API를 호출하도록 종목별로 동기화한다.
        synchronized (fetchLocks.computeIfAbsent(stock.getId(), ignored -> new Object())) {
            return cacheService.get(stock.getId())
                    // Lock을 기다리는 동안 다른 요청이 캐시를 갱신했을 수 있으므로 반드시 한 번 더 검사한다.
                    .filter(value -> isCacheUsable(stock, value))
                    // 두 번째 검사에서도 사용할 수 있는 캐시가 없을 때만 KIS API를 호출한다.
                    .orElseGet(() -> fetchAndCache(stock));
        }
    }

    // Redis에서 꺼낸 분봉을 그대로 사용해도 되는지 판단한다.
    // true면 KIS API를 호출하지 않고 캐시를 사용하고, false면 fetchAndCache()로 최신 분봉을 다시 받는다.
    // 검사 기준은 단순 TTL이 아니라 현재 시장이 장중인지, 마감됐는지, 장 시작 전·주말인지에 따라 달라진다.
    private boolean isCacheUsable(Stock stock, StockMinuteChartCache cache) {
        // 분봉 목록이나 KIS 조회시각이 없으면 캐시의 거래일과 최신성을 판단할 수 없다.
        if (cache == null || cache.candles() == null || cache.candles().isEmpty() || cache.fetchedAt() == null) {
            return false;
        }

        // 국내 종목은 서울, NASDAQ 종목은 뉴욕 현지시각을 기준으로 장 상태를 판단한다.
        StockMarketSchedule schedule = StockMarketSchedules.getSchedule(stock.getMarketType());
        ZonedDateTime marketNow = ZonedDateTime.now(clock.withZone(schedule.zoneId()));
        // fetchedAt은 서버 시간대로 저장되므로 시장 시간대로 변환해 해당 시장 기준으로 오늘 조회했는지 확인한다.
        ZonedDateTime fetchedAtMarket = cache.fetchedAt()
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(schedule.zoneId());
        // 캐시에 실제로 어떤 거래일의 분봉이 들어 있는지 날짜만 추출한다.
        // 장중이라면 보통 {직전 거래일, 오늘}, 장 마감 후라면 {오늘} 또는 최근 두 거래일이 들어 있다.
        Set<LocalDate> cachedDates = cache.candles().stream()
                .map(value -> value.startedAt().toLocalDate())
                .collect(java.util.stream.Collectors.toSet());
        LocalDate today = marketNow.toLocalDate();
        // fetchedToday: 오늘 KIS API를 호출해 만든 캐시인지 여부
        // hasToday: KIS 응답 안에 실제 오늘 분봉이 포함되어 있는지 여부
        // 휴장일이나 해외 프리마켓에는 fetchedToday=true, hasToday=false가 될 수 있다.
        boolean fetchedToday = fetchedAtMarket.toLocalDate().equals(today);
        boolean hasToday = cachedDates.contains(today);

        // 장중에는 시간이 지나면서 계속 새로운 1분봉이 생기므로 가장 엄격하게 검사한다.
        if (StockMarketSchedules.isTradingTime(stock, marketNow)) {
            // 오늘 봉이 없다면 휴장일 또는 해외 프리마켓일 수 있다.
            // 이 경우 오늘 KIS를 한 번 조회한 캐시라면 같은 데이터로 반복 호출하지 않는다.
            if (!hasToday) {
                return fetchedToday;
            }
            // 오늘 봉이 있다면 화면 정책상 직전 거래일 데이터도 함께 있어야 한다.
            boolean hasPreviousSession = cachedDates.stream().anyMatch(date -> date.isBefore(today));
            // Redis TTL이 남아 있어도 장중 스냅샷은 1분이 지나면 오래된 데이터로 보고 다시 조회한다.
            boolean recentlyFetched = cache.fetchedAt().plus(TRADING_CACHE_FRESHNESS)
                    .isAfter(LocalDateTime.now(clock));
            return hasPreviousSession && recentlyFetched;
        }

        // 평일이면서 애프터마켓 종료시각을 지났다면 당일 장이 최종 마감된 상태다.
        if (StockMarketSchedules.isWeekday(marketNow.getDayOfWeek())
                && !marketNow.toLocalTime().isBefore(schedule.afterHoursCloseTime())) {
            // 정상 거래일에는 오늘 봉이 있고 마감 후에 조회한 최종 스냅샷이어야 한다.
            // 오늘 봉이 없다면 휴장일로 보고, 오늘 KIS를 실제 조회한 결과인지 여부만 확인한다.
            return fetchedToday && (!hasToday
                    || !fetchedAtMarket.toLocalTime().isBefore(schedule.afterHoursCloseTime()));
        }

        // 위 조건에 해당하지 않으면 장 시작 전, 주말 또는 장중이 아닌 시간대다.
        // 장 시작 전이면 어제부터, 주말이면 오늘부터 직전 평일을 찾아 마지막 거래일 후보로 사용한다.
        LocalDate lastClosedSessionDate = previousWeekday(
                marketNow.toLocalTime().isBefore(schedule.regularOpenTime()) ? today.minusDays(1) : today);
        // 직전 평일 데이터가 있거나 오늘 이미 KIS를 조회했다면 기존 캐시를 재사용한다.
        // 실제 공휴일 정보는 없으므로 fetchedToday 조건으로 같은 휴장일의 반복 호출을 방지한다.
        return cachedDates.contains(lastClosedSessionDate) || fetchedToday;
    }

    // 기준일이 주말이면 가장 가까운 직전 평일까지 이동한다.
    private LocalDate previousWeekday(LocalDate date) {
        LocalDate candidate = date;
        while (!StockMarketSchedules.isWeekday(candidate.getDayOfWeek())) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    // 시장에 맞는 KIS API로 원본 1분봉을 조회한 뒤 Redis와 JVM fallback 캐시에 저장한다.
    private StockMinuteChartCache fetchAndCache(Stock stock) {
        List<StockMinuteCandleData> candles = stock.getMarketType() == StockMarketType.NASDAQ
                ? fetchOverseas(stock)
                : fetchDomestic(stock);
        // 장중 표시 정책에 필요한 최대 범위가 오늘과 직전 거래일이므로 최근 두 거래일만 보관한다.
        candles = retainLatestTwoTradingDates(candles);
        // fetchedAt은 단순 TTL과 별개로 현재 시장 상태에서 캐시가 유효한지 판단하는 기준시각이다.
        StockMinuteChartCache cache = new StockMinuteChartCache(LocalDateTime.now(clock), candles);
        cacheService.put(stock.getId(), cache);
        return cache;
    }

    // 국내 일자별 분봉 API를 사용하여 화면에 필요한 최근 거래 세션을 조회한다.
    private List<StockMinuteCandleData> fetchDomestic(Stock stock) {
        StockMarketSchedule schedule = StockMarketSchedules.getSchedule(stock.getMarketType());
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(schedule.zoneId()));
        boolean trading = StockMarketSchedules.isTradingTime(stock, now);
        LocalDate candidate = now.toLocalDate();
        // 장 시작 전에는 아직 오늘 분봉이 없으므로 전날부터 최근 거래일을 찾는다.
        if (!trading && now.toLocalTime().isBefore(schedule.regularOpenTime())) {
            candidate = candidate.minusDays(1);
        }

        // 장중에는 직전 거래일과 오늘, 그 외에는 마지막 거래일 한 세션만 확보한다.
        // 평일이라도 공휴일일 수 있으므로 빈 응답이면 날짜를 하루씩 뒤로 이동하며 다시 조회한다.
        List<List<StockMinuteCandleData>> sessions = new ArrayList<>();
        LocalDate cursor = candidate;
        for (int attempts = 0; attempts < PREVIOUS_DATE_SEARCH_DAYS && sessions.size() < (trading ? 2 : 1); attempts++) {
            if (StockMarketSchedules.isWeekday(cursor.getDayOfWeek())) {
                List<StockMinuteCandleData> session = mapDomestic(
                        minutePriceClient.fetchDomesticMinutePrices(stock.getSymbol(), cursor), now.toLocalDateTime());
                if (!session.isEmpty()) {
                    sessions.add(session);
                }
            }
            cursor = cursor.minusDays(1);
        }
        // 날짜를 거꾸로 탐색했으므로 프론트에는 오래된 거래일부터 보이도록 순서를 다시 뒤집는다.
        List<StockMinuteCandleData> result = new ArrayList<>();
        for (int index = sessions.size() - 1; index >= 0; index--) {
            result.addAll(sessions.get(index));
        }
        return List.copyOf(result);
    }

    // 해외주식 분봉 API를 호출하고 KIS 응답을 시장 현지시각 기준의 원본 1분봉으로 변환한다.
    private List<StockMinuteCandleData> fetchOverseas(Stock stock) {
        // 해외 분봉 API는 종목코드뿐 아니라 KIS 거래소 코드도 필요하다.
        String exchangeCode = overseasStockMetadataRepository.findByStock_Id(stock.getId())
                .map(metadata -> metadata.getExchangeCode())
                .filter(value -> value != null && !value.isBlank())
                .orElse(stock.getExchangeCode());
        LocalDateTime marketNow = LocalDateTime.now(clock.withZone(
                StockMarketSchedules.getSchedule(stock.getMarketType()).zoneId()));
        // 페이지 사이에서 같은 봉이 중복될 수 있으므로 변환 후 중복 제거하고 시간순으로 정렬한다.
        return minutePriceClient.fetchOverseasMinutePrices(exchangeCode, stock.getSymbol()).stream()
                .map(item -> mapOverseas(item, marketNow))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(StockMinuteCandleData::startedAt))
                .toList();
    }

    // KIS 국내 분봉 DTO를 애플리케이션 공통 1분봉 객체로 변환한다.
    private List<StockMinuteCandleData> mapDomestic(List<DomesticMinutePriceItem> items, LocalDateTime marketNow) {
        // 여러 페이지의 경계에서 동일한 1분봉이 중복될 수 있으므로 봉 시작시각을 키로 하나만 남긴다.
        Map<LocalDateTime, DomesticMinutePriceItem> unique = new LinkedHashMap<>();
        for (DomesticMinutePriceItem item : items) {
            LocalDateTime startedAt = parseDateTime(item.tradeDate(), item.tradeTime());
            if (startedAt != null) {
                unique.put(startedAt.truncatedTo(ChronoUnit.MINUTES), item);
            }
        }
        // 누적 거래대금을 분별 거래대금으로 바꾸려면 반드시 시간순으로 계산해야 한다.
        List<Map.Entry<LocalDateTime, DomesticMinutePriceItem>> sorted = unique.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        List<StockMinuteCandleData> result = new ArrayList<>();
        BigDecimal previousCumulativeAmount = BigDecimal.ZERO;
        LocalDate previousDate = null;
        for (Map.Entry<LocalDateTime, DomesticMinutePriceItem> entry : sorted) {
            DomesticMinutePriceItem item = entry.getValue();
            BigDecimal cumulativeAmount = KisValueParser.parseNullableBigDecimalOrNull(item.accumulatedTradeAmount());
            // 누적 거래대금은 거래일이 바뀌면 다시 0부터 시작한다.
            if (!entry.getKey().toLocalDate().equals(previousDate)) {
                previousCumulativeAmount = BigDecimal.ZERO;
                previousDate = entry.getKey().toLocalDate();
            }
            // KIS 국내 분봉의 거래대금은 당일 누적값이므로 이전 1분봉 누적값과의 차이를 구한다.
            BigDecimal tradeAmount = cumulativeAmount == null ? null
                    : cumulativeAmount.subtract(previousCumulativeAmount).max(BigDecimal.ZERO);
            if (cumulativeAmount != null) {
                previousCumulativeAmount = cumulativeAmount;
            }
            StockMinuteCandleData candle = createCandle(
                    entry.getKey(), item.openPrice(), item.highPrice(), item.lowPrice(), item.closePrice(),
                    item.volume(), tradeAmount, marketNow);
            if (candle != null) {
                result.add(candle);
            }
        }
        return List.copyOf(result);
    }

    // KIS 해외 분봉 DTO를 국내와 동일한 애플리케이션 공통 1분봉 객체로 변환한다.
    private StockMinuteCandleData mapOverseas(OverseasMinutePriceItem item, LocalDateTime marketNow) {
        LocalDateTime startedAt = parseDateTime(item.localDate(), item.localTime());
        return createCandle(startedAt, item.openPrice(), item.highPrice(), item.lowPrice(), item.closePrice(),
                item.volume(), KisValueParser.parseNullableBigDecimalOrNull(item.tradeAmount()), marketNow);
    }

    // 문자열로 받은 OHLCV를 검증·파싱하고 해당 봉의 확정 여부까지 계산한다.
    private StockMinuteCandleData createCandle(LocalDateTime startedAt,
                                               String open,
                                               String high,
                                               String low,
                                               String close,
                                               String volume,
                                               BigDecimal tradeAmount,
                                               LocalDateTime marketNow) {
        BigDecimal openPrice = KisValueParser.parseNullableBigDecimalOrNull(open);
        BigDecimal highPrice = KisValueParser.parseNullableBigDecimalOrNull(high);
        BigDecimal lowPrice = KisValueParser.parseNullableBigDecimalOrNull(low);
        BigDecimal closePrice = KisValueParser.parseNullableBigDecimalOrNull(close);
        // 차트에 사용할 수 없는 시각 또는 0 이하 가격이 포함된 KIS 응답은 제외한다.
        if (startedAt == null || openPrice == null || highPrice == null || lowPrice == null || closePrice == null
                || openPrice.signum() <= 0 || highPrice.signum() <= 0 || lowPrice.signum() <= 0 || closePrice.signum() <= 0) {
            return null;
        }
        return new StockMinuteCandleData(
                startedAt,
                openPrice,
                highPrice,
                lowPrice,
                closePrice,
                KisValueParser.parseNullableLong(volume),
                tradeAmount,
                // 봉 시작 후 1분이 완전히 지난 경우에만 확정 봉으로 표시한다.
                !startedAt.plusMinutes(1).isAfter(marketNow));
    }

    // 화면에 어떤 거래일의 분봉 데이터를 보여줄지 결정한다.
    // 장중: 직전 거래일 + 오늘 / 장 마감·주말·휴장일: 가장 최근 거래일
    private List<StockMinuteCandleData> selectDisplayCandles(Stock stock, List<StockMinuteCandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }
        StockMarketSchedule schedule = StockMarketSchedules.getSchedule(stock.getMarketType());
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(schedule.zoneId()));
        Set<LocalDate> dates = candles.stream()
                .map(value -> value.startedAt().toLocalDate())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<LocalDate> sortedDates = dates.stream().sorted().toList();
        LocalDate latest = sortedDates.get(sortedDates.size() - 1);
        Set<LocalDate> selected = new LinkedHashSet<>();
        // 오늘 장중이고 오늘 봉이 실제로 존재할 때만 직전 거래일을 함께 선택한다.
        if (StockMarketSchedules.isTradingTime(stock, now) && latest.equals(now.toLocalDate()) && sortedDates.size() > 1) {
            selected.add(sortedDates.get(sortedDates.size() - 2));
        }
        selected.add(latest);
        return candles.stream()
                .filter(value -> selected.contains(value.startedAt().toLocalDate()))
                .sorted(Comparator.comparing(StockMinuteCandleData::startedAt))
                .toList();
    }

    // 원본 1분봉을 요청한 분 단위로 묶는다. 1분봉 요청도 같은 경로를 사용하며 봉 하나가 한 그룹이 된다.
    private List<StockChartCandleData> aggregate(List<StockMinuteCandleData> candles, int minuteSize) {
        Map<LocalDateTime, List<StockMinuteCandleData>> groups = new LinkedHashMap<>();
        for (StockMinuteCandleData candle : candles) {
            // 예: 10:07 봉은 3분봉이면 10:06, 5분봉이면 10:05 시작 그룹에 포함된다.
            LocalDateTime bucket = candle.startedAt()
                    .withMinute(candle.startedAt().getMinute() - candle.startedAt().getMinute() % minuteSize)
                    .withSecond(0)
                    .withNano(0);
            groups.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(candle);
        }
        return groups.entrySet().stream()
                .map(entry -> aggregateBucket(entry.getKey(), entry.getValue()))
                .toList();
    }

    // Redis에 최근 두 거래일의 원본 1분봉만 유지한다.
    private List<StockMinuteCandleData> retainLatestTwoTradingDates(List<StockMinuteCandleData> candles) {
        List<LocalDate> dates = candles.stream()
                .map(value -> value.startedAt().toLocalDate())
                .distinct()
                .sorted()
                .toList();
        if (dates.size() <= 2) {
            return List.copyOf(candles);
        }
        Set<LocalDate> retainedDates = Set.of(dates.get(dates.size() - 2), dates.get(dates.size() - 1));
        return candles.stream()
                .filter(value -> retainedDates.contains(value.startedAt().toLocalDate()))
                .sorted(Comparator.comparing(StockMinuteCandleData::startedAt))
                .toList();
    }

    // 같은 시간 구간에 속한 원본 1분봉들을 하나의 1·3·5·15분봉으로 합친다.
    private StockChartCandleData aggregateBucket(LocalDateTime bucket, List<StockMinuteCandleData> values) {
        List<StockMinuteCandleData> sorted = values.stream()
                .sorted(Comparator.comparing(StockMinuteCandleData::startedAt))
                .toList();
        // 일부 원본 봉의 거래대금이 누락되면 합계가 실제 값처럼 보이지 않도록 전체 거래대금을 null로 반환한다.
        boolean amountKnown = sorted.stream().allMatch(value -> value.tradeAmount() != null);
        return new StockChartCandleData(
                bucket.format(CANDLE_TIMESTAMP),
                // 시가: 첫 1분봉, 고가·저가: 구간 최댓값·최솟값, 종가: 마지막 1분봉
                sorted.get(0).openPrice(),
                sorted.stream().map(StockMinuteCandleData::highPrice).max(BigDecimal::compareTo).orElseThrow(),
                sorted.stream().map(StockMinuteCandleData::lowPrice).min(BigDecimal::compareTo).orElseThrow(),
                sorted.get(sorted.size() - 1).closePrice(),
                sorted.stream().map(StockMinuteCandleData::volume).filter(java.util.Objects::nonNull).mapToLong(Long::longValue).sum(),
                amountKnown ? sorted.stream().map(StockMinuteCandleData::tradeAmount).reduce(BigDecimal.ZERO, BigDecimal::add) : null,
                // 묶음 안의 모든 원본 1분봉이 확정된 경우에만 집계 봉도 확정된 것으로 본다.
                sorted.stream().allMatch(StockMinuteCandleData::completed));
    }

    // KIS의 yyyyMMdd, HHmmss 문자열을 시장 현지시각의 LocalDateTime으로 변환한다.
    // 형식이 잘못된 개별 응답은 전체 조회를 실패시키지 않고 null로 제외한다.
    private LocalDateTime parseDateTime(String date, String time) {
        try {
            if (date == null || date.length() < 8 || time == null || time.length() < 6) {
                return null;
            }
            return LocalDateTime.of(
                    LocalDate.parse(date.substring(0, 8), DATE),
                    LocalTime.parse(time.substring(0, 6), TIME));
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
