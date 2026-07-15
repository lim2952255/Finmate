package com.finmate.service.stock.price;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.price.StockDailyPrice;
import com.finmate.infra.kis.stock.price.KisStockDailyPriceClient;
import com.finmate.infra.kis.stock.price.KisStockDailyPriceClient.DomesticDailyPriceItem;
import com.finmate.infra.kis.stock.price.KisStockDailyPriceClient.DomesticDailyPriceResponse;
import com.finmate.infra.kis.stock.price.KisStockDailyPriceClient.OverseasDailyPriceItem;
import com.finmate.infra.kis.stock.price.KisStockDailyPriceClient.OverseasDailyPriceResponse;
import com.finmate.repository.stock.StockRepository;
import com.finmate.repository.stock.price.StockDailyPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.finmate.global.validation.RequiredValidator.validateRequired;
import static com.finmate.infra.kis.parser.KisValueParser.parseNullableBigDecimal;
import static com.finmate.infra.kis.parser.KisValueParser.parseRequiredBigDecimal;
import static com.finmate.infra.kis.parser.KisValueParser.parseRequiredDate;
import static com.finmate.infra.kis.parser.KisValueParser.parseRequiredLong;
import static com.finmate.infra.kis.parser.KisValueParser.parseYesNoOrOne;

// KisStockDailyPriceClient를 통해 KIS API를 활용하여 받은 종목의 일봉 데이터들 서비스에 맞는 StockDailyPrice 엔티티로 변환하여 저장하는 서비스
@Service
@RequiredArgsConstructor
public class StockDailyPriceSyncService {
    private static final int DOMESTIC_FETCH_WINDOW_DAYS = 120; // 한번 조회시 가져올 데이터의 수

    private final StockRepository stockRepository;
    private final StockDailyPriceRepository stockDailyPriceRepository;
    private final KisStockDailyPriceClient kisStockDailyPriceClient; // KIS API를 활용하여 종목의 일봉 데이터를 받는 클라이언트 로직

    // 종목의 일봉데이터를 받아 엔티티에 저장하는 메서드
    @Transactional
    public int fetchAndSaveDailyPrices(Long stockId,
                                       LocalDate startDate,
                                       LocalDate endDate,
                                       boolean adjustedPrice) {
        validateRequired(stockId, "종목 ID는 필수입니다.");
        validateRequired(startDate, "조회 시작일자는 필수입니다.");
        validateRequired(endDate, "조회 종료일자는 필수입니다.");

        if (startDate.isAfter(endDate)) {
            throw new RuntimeException("조회 시작일자는 종료일자보다 늦을 수 없습니다.");
        }

        // 찾고자 하는 종목이 StockRepository에 저장되어 있지 않은 경우
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new RuntimeException("종목을 찾을 수 없습니다."));

        return switch (stock.getMarketType()) {
            // KOSPI /KOSDAQ의 경우에는 국내 주식의 일봉데이터를 저장한다.
            case KOSPI, KOSDAQ -> fetchAndSaveDomesticDailyPrices(stock, startDate, endDate, adjustedPrice);
            // NASDAQ의 경우에는 해외 주식의 일봉데이터를 저장한다.
            case NASDAQ -> fetchAndSaveOverseasDailyPrices(stock, startDate, endDate, adjustedPrice);
        };
    }

    // 국내 주식의 일봉데이터 저장
    private int fetchAndSaveDomesticDailyPrices(Stock stock,
                                                LocalDate startDate,
                                                LocalDate endDate,
                                                boolean adjustedPrice) {
        int savedCount = 0; // 새로 저장하 일봉 데이터 개수를 저장하는 변수
        LocalDate chunkStartDate = startDate; // 처음 조회할 구간의 시작을 설정

        // 한번에 너무 오랜기간의 데이터를 요청하면 api 요청 실패가 발생할수도 있고, response body가 너무 무거워지기 떄문에 일정한 기간으로 나눠서 조회한다.
        while (!chunkStartDate.isAfter(endDate)) { // 조회기간을 여러 조각으로 나누기 (120일씩 나눠서 데이터를 가져온다)
            LocalDate chunkEndDate = chunkStartDate.plusDays(DOMESTIC_FETCH_WINDOW_DAYS - 1); // 120일 단위로 기간을 나눈다.
            if (chunkEndDate.isAfter(endDate)) {
                chunkEndDate = endDate; // 종료기간을 초과하면 종료기간으로 설정
            }

            // 작은 조각으로 나눈 조회 기간을 기반으로 데이터 조회 및 엔티티 저장
            savedCount += fetchAndSaveDomesticDailyPricesChunk(stock, chunkStartDate, chunkEndDate, adjustedPrice);
            chunkStartDate = chunkEndDate.plusDays(1); // 다음 조회시작기간 설정
        }

        return savedCount;
    }

    // 설정된 조회기간을 기반으로 일봉데이터 조회 및 엔티티 저장
    private int fetchAndSaveDomesticDailyPricesChunk(Stock stock,
                                                     LocalDate startDate,
                                                     LocalDate endDate,
                                                     boolean adjustedPrice) {
        // KisStockDailyPriceClient를 통해 조회한 일봉 데이터(레코드) 반환
        DomesticDailyPriceResponse response = kisStockDailyPriceClient.fetchDomesticDailyPrices(
                stock.getSymbol(),
                startDate,
                endDate,
                adjustedPrice);

        // 데이터를 fetch해온 시점 기록
        LocalDateTime fetchedAt = LocalDateTime.now();
        // 새로 조회한 엔티티들을 저장할 list
        List<StockDailyPrice> dailyPrices = new ArrayList<>();

        // response를 통해 조회한 데이터가 비어있는지 안전하게 확인하기 위해 safeList 사용
        for (DomesticDailyPriceItem item : safeList(response.output2())) {
            LocalDate tradeDate = parseRequiredDate(item.tradeDate(), "거래일은 필수입니다.");
            if (alreadyExists(stock, tradeDate, adjustedPrice)) {
                // 이미 조회한 데이터가 레포지터리에 존재하면 스킵
                continue;
            }

            // 조회한 레코드를 기반으로 새로운 StockDailyPrice 엔티티를 생성한 다음, list에 저장
            dailyPrices.add(StockDailyPrice.create(
                    stock,
                    tradeDate,
                    parseRequiredBigDecimal(item.openPrice(), "가격은 필수입니다."),
                    parseRequiredBigDecimal(item.highPrice(), "가격은 필수입니다."),
                    parseRequiredBigDecimal(item.lowPrice(), "가격은 필수입니다."),
                    parseRequiredBigDecimal(item.closePrice(), "가격은 필수입니다."),
                    parseRequiredLong(item.accumulatedVolume(), "거래량은 필수입니다."),
                    parseNullableBigDecimal(item.accumulatedTradeAmount()),
                    adjustedPrice,
                    parseYesNoOrOne(item.modified()),
                    fetchedAt));
        }

        // 리스트내의 모든 엔티티를 한번에 저장
        stockDailyPriceRepository.saveAll(dailyPrices);
        return dailyPrices.size();
    }

    // 해외 주식(NASDAQ)의 일봉데이터 저장
    // 해외 api는 기준일자(baseDate)만 받기 때문에 국내 주식 api와는 구조가 다르다.
    // 해외 api는 기준일자를 기준으로 과거의 몇건의 데이터를 반환해준다.
    // 따라서 반환된 데이터의 가장 오래된 일자를 기반으로 점점 기준일자를 수정해가면서 데이터를 조회해야 한다.
    private int fetchAndSaveOverseasDailyPrices(Stock stock,
                                                LocalDate startDate,
                                                LocalDate endDate,
                                                boolean adjustedPrice) {
        int savedCount = 0;
        LocalDate baseDate = endDate;

        while (!baseDate.isBefore(startDate)) { //start date 이전까지 데이터를 조회
            OverseasDailyPriceResponse response = kisStockDailyPriceClient.fetchOverseasDailyPrices(
                    stock.getExchangeCode(),
                    stock.getSymbol(),
                    baseDate,
                    adjustedPrice);

            List<OverseasDailyPriceItem> items = safeList(response.output2());
            if (items.isEmpty()) {
                break;
            }

            savedCount += saveOverseasDailyPrices(stock, startDate, endDate, adjustedPrice, items);

            // 조회한 일봉 데이터들중 가장 오래된 tradeDate를 찾고, 이를 통해 nextBaseDate를 설정한다.
            LocalDate oldestTradeDate = findOldestTradeDate(items);
            if (!oldestTradeDate.isAfter(startDate)) {
                break;
            }

            LocalDate nextBaseDate = oldestTradeDate.minusDays(1);
            if (!nextBaseDate.isBefore(baseDate)) {
                break;
            }

            baseDate = nextBaseDate;
        }

        return savedCount;
    }

    // 조회한 레코드 정보를 기반으로 엔티티에 값을 채워서 레파지터리에 저장한다.
    private int saveOverseasDailyPrices(Stock stock,
                                        LocalDate startDate,
                                        LocalDate endDate,
                                        boolean adjustedPrice,
                                        List<OverseasDailyPriceItem> items) {
        LocalDateTime fetchedAt = LocalDateTime.now();
        List<StockDailyPrice> dailyPrices = new ArrayList<>();

        for (OverseasDailyPriceItem item : items) {
            LocalDate tradeDate = parseRequiredDate(item.tradeDate(), "거래일은 필수입니다.");
            if (tradeDate.isBefore(startDate) || tradeDate.isAfter(endDate)) {
                continue;
            }

            if (alreadyExists(stock, tradeDate, adjustedPrice)) {
                continue;
            }

            dailyPrices.add(StockDailyPrice.create(
                    stock,
                    tradeDate,
                    parseRequiredBigDecimal(item.openPrice(), "가격은 필수입니다."),
                    parseRequiredBigDecimal(item.highPrice(), "가격은 필수입니다."),
                    parseRequiredBigDecimal(item.lowPrice(), "가격은 필수입니다."),
                    parseRequiredBigDecimal(item.closePrice(), "가격은 필수입니다."),
                    parseRequiredLong(item.accumulatedVolume(), "거래량은 필수입니다."),
                    parseNullableBigDecimal(item.accumulatedTradeAmount()),
                    adjustedPrice,
                    false,
                    fetchedAt));
        }

        stockDailyPriceRepository.saveAll(dailyPrices);
        return dailyPrices.size();
    }
    // 조회한 일봉 데이터들중, TradeDate가 가장 오래된 일자를 찾는다.
    private LocalDate findOldestTradeDate(List<OverseasDailyPriceItem> items) {
        return items.stream()
                .map(item -> parseRequiredDate(item.tradeDate(), "거래일은 필수입니다."))
                .min(LocalDate::compareTo)
                .orElseThrow(() -> new RuntimeException("해외 일봉 응답에 거래일이 없습니다."));
    }

    // 이미 StockDailyPriceRepository에 해당 tradeDate의 일봉 데이터가 존재하는지 확인
    private boolean alreadyExists(Stock stock, LocalDate tradeDate, boolean adjustedPrice) {
        return stockDailyPriceRepository.existsByStock_IdAndTradeDateAndAdjustedPrice(
                stock.getId(),
                tradeDate,
                adjustedPrice);
    }

    // 리스트를 안전하게 처리하기 위해서 빈 리스트 처리
    private <T> List<T> safeList(List<T> items) {
        if (items == null) {
            return List.of();
        }

        return items;
    }
}
