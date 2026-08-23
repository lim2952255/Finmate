package com.finmate.service.stock.price;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.dto.detail.StockChartInterval;
import com.finmate.domain.stock.price.StockPeriodPrice;
import com.finmate.infra.kis.stock.price.KisStockDailyPriceClient;
import com.finmate.infra.kis.stock.price.KisStockDailyPriceClient.DomesticDailyPriceItem;
import com.finmate.infra.kis.stock.price.KisStockDailyPriceClient.OverseasDailyPriceItem;
import com.finmate.repository.stock.StockRepository;
import com.finmate.repository.stock.price.StockPeriodPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.finmate.global.validation.RequiredValidator.validateRequired;
import static com.finmate.infra.kis.parser.KisValueParser.parseNullableBigDecimal;
import static com.finmate.infra.kis.parser.KisValueParser.parseRequiredBigDecimal;
import static com.finmate.infra.kis.parser.KisValueParser.parseRequiredDate;
import static com.finmate.infra.kis.parser.KisValueParser.parseRequiredLong;

// KIS API를 호출해서 주봉/월봉/연봉 데이터를 가져와서 StockPeriodPrice엔티티로 저장하는 동기화 서비스
@Service
@RequiredArgsConstructor
public class StockPeriodPriceSyncService {
    private final StockRepository stockRepository;
    private final StockPeriodPriceRepository stockPeriodPriceRepository;
    private final KisStockDailyPriceClient kisStockDailyPriceClient; // KIS API를 호출해서 데이터를 받아서 리턴하는 클라이언트

	// 주/월/연봉 데이터를 조회하고 리턴하는 메서드
    @Transactional
    public int fetchAndSavePeriodPrices(Long stockId,
                                        StockChartInterval interval,
                                        LocalDate startDate,
                                        LocalDate endDate,
                                        boolean adjustedPrice) {
        validateRequired(stockId, "종목 ID는 필수입니다.");
        validateRequired(interval, "봉 주기는 필수입니다.");
        validateRequired(startDate, "조회 시작일자는 필수입니다.");
        validateRequired(endDate, "조회 종료일자는 필수입니다.");
		// 일봉데이터 조회는 별도의 서비스를 활용
        if (interval == StockChartInterval.DAY) {
            throw new IllegalArgumentException("일봉 동기화는 StockDailyPriceSyncService를 사용해야 합니다.");
        }
        if (startDate.isAfter(endDate)) {
            return 0;
        }

        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new RuntimeException("종목을 찾을 수 없습니다."));
		// 종목이 나스닥종목이고, 연봉 데이터를 조회하는 경우, KIS API에서 연봉 데이터를 조회하는 API는 제공하지 않기 때문에 월봉 데이터를 조회하고, 이를 기반으로 연봉 데이터를 계산한다.
        if (stock.getMarketType() == StockMarketType.NASDAQ && interval == StockChartInterval.YEAR) {
            return fetchAndSaveOverseasYearPrices(stock, startDate, endDate, adjustedPrice);
        }
		// 종목이 나스닥종목이고, 주봉/월봉 데이터를 조회하는 경우, fetchAndSaveOverseasPrices 메서드를 호출해서 데이터를 동기화한다.
        if (stock.getMarketType() == StockMarketType.NASDAQ) {
            return fetchAndSaveOverseasPrices(stock, interval, startDate, endDate, adjustedPrice);
        }
		// 국내종목인 경우에는 fetchAndSaveDomesticPrices 메서드를 호출해서 데이터를 동기화한다.
        return fetchAndSaveDomesticPrices(stock, interval, startDate, endDate, adjustedPrice);
    }

	// 국내 종목의 주봉/월봉/연봉데이터를 동기화하는 메서드
    private int fetchAndSaveDomesticPrices(Stock stock,
                                           StockChartInterval interval,
                                           LocalDate startDate,
                                           LocalDate endDate,
                                           boolean adjustedPrice) {
        int savedCount = 0; // 이번 조회에서 동기화한 데이터 건수를 나타내는 counter
        LocalDate chunkStart = startDate; // 한번에 너무 많은 데이터를 조회하는것이 아니라, chunk단위로 나눠서 조회한다.
        while (!chunkStart.isAfter(endDate)) {
            LocalDate chunkEnd = domesticChunkEnd(chunkStart, interval); // chunk 종료일을 계산한다.
            if (chunkEnd.isAfter(endDate)) {
                chunkEnd = endDate; // 만약 chunk 종료일이 endDate를 벗어나면 endDate값을 사용한다.
            }

			// KisStockDailyPriceClient의 fetchDomesticPrices 메서드를 호출해서 KIS API를 호출하고, 결과 데이터를 리스트로 받는다.
            List<DomesticDailyPriceItem> items = safeList(kisStockDailyPriceClient.fetchDomesticPrices(
                    stock.getSymbol(), chunkStart, chunkEnd, interval, adjustedPrice).output2());
			// KIS API로부터 받은 DTO를 엔티티로 변환하여 동기화하고, 동기화된 데이터 건수를 업데이트한다.
            savedCount += saveDomesticItems(stock, interval, chunkStart, chunkEnd, adjustedPrice, items);
            chunkStart = chunkEnd.plusDays(1); // chunk 시작일을 update한다.
        }
        return savedCount;
    }

	// 기간별 chunk의 범위를 결정한다. (WEEK: 90주, MONTH: 90개월, YEAR: 90년)
    private LocalDate domesticChunkEnd(LocalDate startDate, StockChartInterval interval) {
        return switch (interval) {
            case MINUTE_1, MINUTE_3, MINUTE_5, MINUTE_15 ->
                    throw new IllegalArgumentException("분봉 구간은 기간봉 동기화에서 지원하지 않습니다.");
            case WEEK -> startDate.plusWeeks(90).minusDays(1);
            case MONTH -> startDate.plusMonths(90).minusDays(1);
            case YEAR -> startDate.plusYears(90).minusDays(1);
            case DAY -> throw new IllegalArgumentException("일봉 구간은 지원하지 않습니다.");
        };
    }

	// KIS API로부터 받은 DTO 데이터를 엔티티로 변환하여 저장하고 동기화하는 메서드
    private int saveDomesticItems(Stock stock,
                                  StockChartInterval interval,
                                  LocalDate startDate,
                                  LocalDate endDate,
                                  boolean adjustedPrice,
                                  List<DomesticDailyPriceItem> items) {
        LocalDateTime fetchedAt = LocalDateTime.now(); // 기간봉 데이터를 KIS API로부터 받아온 시점을 저장.
        List<StockPeriodPrice> prices = new ArrayList<>(); // DB에 저장할 엔티티리스트 생성
		// KIS API로부터 전달받은 DTO 데이터를 하나씩 순회하면서, 이를 기반으로 엔티티를 생성해서 리스트에 추가한다.
        for (DomesticDailyPriceItem item : items) {
            LocalDate candleDate = interval.periodStart( // candleDate는 각 기간봉의 기준일(시작일)을 저장한다.
                    parseRequiredDate(item.tradeDate(), "봉 기준일은 필수입니다."));
            if (candleDate.isBefore(startDate) || candleDate.isAfter(endDate)
                    || alreadyExists(stock, interval, candleDate, adjustedPrice)) {
                continue;
            }
            prices.add(StockPeriodPrice.create(
                    stock,
                    interval,
                    candleDate,
                    parseRequiredBigDecimal(item.openPrice(), "가격은 필수입니다."),
                    parseRequiredBigDecimal(item.highPrice(), "가격은 필수입니다."),
                    parseRequiredBigDecimal(item.lowPrice(), "가격은 필수입니다."),
                    parseRequiredBigDecimal(item.closePrice(), "가격은 필수입니다."),
                    parseRequiredLong(item.accumulatedVolume(), "거래량은 필수입니다."),
                    parseNullableBigDecimal(item.accumulatedTradeAmount()),
                    adjustedPrice,
                    fetchedAt));
        }
		// 새로 생성한 엔티티 리스트를 레파지터리에 저장한다.
        stockPeriodPriceRepository.saveAll(prices);
        return prices.size();
    }

	// 해외 종목(나스닥)의 주봉 / 월봉 데이터를 받아서 저장한다.
    private int fetchAndSaveOverseasPrices(Stock stock,
                                           StockChartInterval interval,
                                           LocalDate startDate,
                                           LocalDate endDate,
                                           boolean adjustedPrice) {
        int savedCount = 0;
		// 해외종목의 경우에는 국내 종목과는 반대로 최신데이터 -> 과거데이터 순서로 조회한다.
        LocalDate baseDate = endDate;
        while (!baseDate.isBefore(startDate)) {
			// KIS API를 호출해서 해외 주봉/월봉 DTO를 받는다.
            List<OverseasDailyPriceItem> items = safeList(kisStockDailyPriceClient.fetchOverseasPrices(
                    stock.getExchangeCode(), stock.getSymbol(), baseDate, interval, adjustedPrice).output2());
            if (items.isEmpty()) {
                break;
            }
			// KIS API로부터 받은 해외 주봉/월봉 DTO를 엔티티로 변환하여 저장한다.
            savedCount += saveOverseasItems(stock, interval, startDate, endDate, adjustedPrice, items);
            LocalDate oldestDate = items.stream()
                    .map(item -> parseRequiredDate(item.tradeDate(), "봉 기준일은 필수입니다."))
                    .min(LocalDate::compareTo)
                    .orElseThrow();
            if (!oldestDate.isAfter(startDate)) {
                break;
            }
            LocalDate nextBaseDate = oldestDate.minusDays(1);
            if (!nextBaseDate.isBefore(baseDate)) {
                break;
            }
            baseDate = nextBaseDate;
        }
        return savedCount;
    }

	// KIS API로부터 받은 해외 주봉/월봉 DTO를 엔티티로 변환하여 저장한다.
    private int saveOverseasItems(Stock stock,
                                  StockChartInterval interval,
                                  LocalDate startDate,
                                  LocalDate endDate,
                                  boolean adjustedPrice,
                                  List<OverseasDailyPriceItem> items) {
        LocalDateTime fetchedAt = LocalDateTime.now();
        List<StockPeriodPrice> prices = new ArrayList<>();
        for (OverseasDailyPriceItem item : items) {
            LocalDate candleDate = interval.periodStart(
                    parseRequiredDate(item.tradeDate(), "봉 기준일은 필수입니다."));
            if (candleDate.isBefore(startDate) || candleDate.isAfter(endDate)
                    || alreadyExists(stock, interval, candleDate, adjustedPrice)) {
                continue;
            }
            prices.add(StockPeriodPrice.create(
                    stock,
                    interval,
                    candleDate,
                    parseRequiredBigDecimal(item.openPrice(), "가격은 필수입니다."),
                    parseRequiredBigDecimal(item.highPrice(), "가격은 필수입니다."),
                    parseRequiredBigDecimal(item.lowPrice(), "가격은 필수입니다."),
                    parseRequiredBigDecimal(item.closePrice(), "가격은 필수입니다."),
                    parseRequiredLong(item.accumulatedVolume(), "거래량은 필수입니다."),
                    parseNullableBigDecimal(item.accumulatedTradeAmount()),
                    adjustedPrice,
                    fetchedAt));
        }
        stockPeriodPriceRepository.saveAll(prices);
        return prices.size();
    }

	// KIS API에서 해외 연봉 데이터를 직접 제공받을 수 없기 때문에, 이는 월봉데이터를 조회하고, 월봉데이터를 기반으로 연봉 데이터를 생성해서 저장한다.
    private int fetchAndSaveOverseasYearPrices(Stock stock,
                                               LocalDate startDate,
                                               LocalDate endDate,
                                               boolean adjustedPrice) {
		// 우선 해외종목에 대해서 월봉데이터를 우선적으로 조회하고 저장한다.
        fetchAndSaveOverseasPrices(stock, StockChartInterval.MONTH, startDate, endDate, adjustedPrice);
		// 레파지터리에 저장된 월봉 데이터를 조회한다.
        List<StockPeriodPrice> monthlyPrices = stockPeriodPriceRepository
                .findByStock_IdAndIntervalAndAdjustedPriceAndCandleDateBetweenOrderByCandleDateAsc(
                        stock.getId(), StockChartInterval.MONTH, adjustedPrice, startDate, endDate);
		// 월봉을 연도별로 그룹핑한다.
        Map<Integer, List<StockPeriodPrice>> byYear = monthlyPrices.stream()
				// 월봉 데이터를 연도별로 그룹핑한다.
                .collect(Collectors.groupingBy(price -> price.getCandleDate().getYear()));
        LocalDateTime fetchedAt = LocalDateTime.now();
        List<StockPeriodPrice> yearlyPrices = new ArrayList<>();

		// 연도별로 그룹핑된 월봉데이터에 대해서 연도별로 정렬을 수행한 뒤, 각 연도별 월봉데이터를 통합해서 연봉데이터를 생성한후 DB에 동기화한다.
        byYear.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
			// 연봉기준날짜를 매년 1월 1일로 설정한다.
            LocalDate candleDate = LocalDate.of(entry.getKey(), 1, 1);
			// 이미 해당 연봉데이터가 DB에 저장되어 있으면 리턴한다.
            if (alreadyExists(stock, StockChartInterval.YEAR, candleDate, adjustedPrice)) {
                return;
            }
			// 월봉을 날짜순으로 정렬한다.
            List<StockPeriodPrice> months = entry.getValue().stream()
                    .sorted((left, right) -> left.getCandleDate().compareTo(right.getCandleDate()))
                    .toList();
			// 월봉데이터 12개를 합쳐서 연봉데이터를 생성한다.
            yearlyPrices.add(StockPeriodPrice.create(
                    stock,
                    StockChartInterval.YEAR,
                    candleDate,
                    months.get(0).getOpenPrice(), // 1월봉의 시가를 연봉데이터의 시가로 설정
                    months.stream().map(StockPeriodPrice::getHighPrice).max(BigDecimal::compareTo).orElseThrow(), // 각 월봉데이터의 고가중 최댓값을 고가로 설정
                    months.stream().map(StockPeriodPrice::getLowPrice).min(BigDecimal::compareTo).orElseThrow(), // 각 월봉데이터의 저가중 최솟값을 저가로 설정
                    months.get(months.size() - 1).getClosePrice(), // 가장 최신달의 종가를 연봉데이터의 종가로 설정(12월)
                    months.stream().mapToLong(StockPeriodPrice::getAccumulatedVolume).sum(), // 각 월봉데이터의 거래량을 통합
                    sumTradeAmounts(months), // 각 월봉데이터의 거래대금을 통합
                    adjustedPrice,
                    fetchedAt));
        });
		// 연봉데이터를 DB에 저장한다.
        stockPeriodPriceRepository.saveAll(yearlyPrices);
        return yearlyPrices.size();
    }

	// 월봉데이터를 기반으로 연봉데이터를 계산할때, 거래대금을 총합하는 메서드
    private BigDecimal sumTradeAmounts(List<StockPeriodPrice> prices) {
		// 만약 특정 월봉데이터의 거래대금값이 null이면, 이는 부정확하다는 의미이기 때문에, 연봉데이터의 거래대금도 null로 처리한다.
        if (prices.stream().anyMatch(price -> price.getAccumulatedTradeAmount() == null)) {
            return null;
        }
		// 각 월봉데이터의 거래대금들을 모두 총합한다.
        List<BigDecimal> amounts = prices.stream()
                .map(StockPeriodPrice::getAccumulatedTradeAmount)
                .toList();
		// reduce 메서드는 여러값을 하나로 합치는 메서드이다.
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

	// 조회하고자 하는 데이터가 이미 DB에 저장되어있는지를 검사한다.
    private boolean alreadyExists(Stock stock,
                                  StockChartInterval interval,
                                  LocalDate candleDate,
                                  boolean adjustedPrice) {
        return stockPeriodPriceRepository.existsByStock_IdAndIntervalAndCandleDateAndAdjustedPrice(
                stock.getId(), interval, candleDate, adjustedPrice);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
