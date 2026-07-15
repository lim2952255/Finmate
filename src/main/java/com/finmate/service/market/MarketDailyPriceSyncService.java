package com.finmate.service.market;

import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.domain.market.price.MarketDailyPrice;
import com.finmate.infra.kis.exchange.KisDomesticIndexDailyChartPriceClient;
import com.finmate.infra.kis.exchange.KisDomesticIndexDailyChartPriceClient.DailyIndexChartPriceItem;
import com.finmate.infra.kis.exchange.KisDomesticIndexDailyChartPriceClient.DailyIndexChartPriceResponse;
import com.finmate.infra.kis.exchange.KisOverseasMarketDailyChartPriceClient;
import com.finmate.infra.kis.exchange.KisOverseasMarketDailyChartPriceClient.DailyChartPriceItem;
import com.finmate.infra.kis.exchange.KisOverseasMarketDailyChartPriceClient.DailyChartPriceResponse;
import com.finmate.repository.market.price.MarketDailyPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.finmate.global.validation.RequiredValidator.validateRequired;
import static com.finmate.infra.kis.parser.KisValueParser.parseNullableLong;
import static com.finmate.infra.kis.parser.KisValueParser.parseRequiredBigDecimal;
import static com.finmate.infra.kis.parser.KisValueParser.parseRequiredDate;
import static com.finmate.infra.kis.parser.KisValueParser.parseYesNoOrOne;

// 온디멘드 방식으로 KIS에서 주가 지수 / 환율 시세 정보를 받아서, DB에 동기화하는 서비스 로직
@Service
@RequiredArgsConstructor
public class MarketDailyPriceSyncService {
    private static final int FETCH_WINDOW_DAYS = 120;
    private static final String DAILY_PERIOD_DIVISION_CODE = "D";

    private final MarketDailyPriceRepository marketDailyPriceRepository;
    private final KisOverseasMarketDailyChartPriceClient kisOverseasMarketDailyChartPriceClient; // KIS API를 통해 해외 주가지수 + 환율 데이터를 받는 Client
    private final KisDomesticIndexDailyChartPriceClient kisDomesticIndexDailyChartPriceClient; // KIS API를 통해 국내 주가지수 데이터를 받아온다.

    // 일봉 데이터를 받아서 DB에 저장하는 메서드
    @Transactional
    public int fetchAndSaveDailyPrices(MarketIndicatorSymbol indicator,
                                       LocalDate startDate,
                                       LocalDate endDate) {
        validateRequired(indicator, "지수/환율 정보는 필수입니다.");
        validateRequired(startDate, "조회 시작일자는 필수입니다.");
        validateRequired(endDate, "조회 종료일자는 필수입니다.");

        if (startDate.isAfter(endDate)) {
            throw new RuntimeException("조회 시작일자는 종료일자보다 늦을 수 없습니다.");
        }

        int savedCount = 0;
        LocalDate chunkStartDate = startDate;
        while (!chunkStartDate.isAfter(endDate)) {
            // 한번에 너무 오랜기간의 데이터를 요청하면 api 요청 실패가 발생할수도 있고, response body가 너무 무거워지기 떄문에 일정한 기간으로 나눠서 조회한다.
            LocalDate chunkEndDate = chunkStartDate.plusDays(FETCH_WINDOW_DAYS - 1);
            if (chunkEndDate.isAfter(endDate)) {
                chunkEndDate = endDate;
            }
            // chunk단위로 기간을 나눠서 KIS API로 일봉 데이터를 요청한다.
            List<DailyPriceItem> items = fetchDailyPrices(
                    indicator,
                    chunkStartDate,
                    chunkEndDate,
                    DAILY_PERIOD_DIVISION_CODE);
            // API를 통해 받은 일봉데이터를 DB에 update하고, 새로 저장한 일봉 데이터 수를 update
            savedCount += saveDailyPrices(indicator, chunkStartDate, chunkEndDate, items);
            chunkStartDate = chunkEndDate.plusDays(1);
        }

        return savedCount;
    }

    private List<DailyPriceItem> fetchDailyPrices(MarketIndicatorSymbol indicator,
                                                  LocalDate startDate,
                                                  LocalDate endDate,
                                                  String periodDivisionCode) {
        return switch (indicator.getKisApiType()) {
            // indicator type에 따라 해외 주가지수 또는 국내 주가지수 정보를 받는다.
            case OVERSEAS_DAILY_CHART_PRICE -> fetchOverseasDailyPrices(
                    indicator,
                    startDate,
                    endDate,
                    periodDivisionCode);
            case DOMESTIC_DAILY_INDEX_CHART_PRICE -> fetchDomesticIndexDailyPrices(
                    indicator,
                    startDate,
                    endDate,
                    periodDivisionCode);
        };
    }
    // 해외 주가지수 정보를 받아온다.
    private List<DailyPriceItem> fetchOverseasDailyPrices(MarketIndicatorSymbol indicator,
                                                          LocalDate startDate,
                                                          LocalDate endDate,
                                                          String periodDivisionCode) {
        DailyChartPriceResponse response = kisOverseasMarketDailyChartPriceClient.fetchDailyChartPrices(
                indicator.getKisMarketDivisionCode(),
                indicator.getKisSymbol(),
                startDate,
                endDate,
                periodDivisionCode);

        return safeList(response.output2()).stream()
                .map(this::toDailyPriceItem)
                .toList();
    }
    // 국내 주가지수 정보를 받아온다
    private List<DailyPriceItem> fetchDomesticIndexDailyPrices(MarketIndicatorSymbol indicator,
                                                               LocalDate startDate,
                                                               LocalDate endDate,
                                                               String periodDivisionCode) {
        DailyIndexChartPriceResponse response = kisDomesticIndexDailyChartPriceClient.fetchDailyIndexChartPrices(
                indicator.getKisMarketDivisionCode(),
                indicator.getKisSymbol(),
                startDate,
                endDate,
                periodDivisionCode);

        return safeList(response.output2()).stream()
                .map(this::toDailyPriceItem)
                .toList();
    }

    private DailyPriceItem toDailyPriceItem(DailyChartPriceItem item) {
        return new DailyPriceItem(
                item.tradeDate(),
                item.openPrice(),
                item.highPrice(),
                item.lowPrice(),
                item.closePrice(),
                item.accumulatedVolume(),
                item.modified());
    }

    private DailyPriceItem toDailyPriceItem(DailyIndexChartPriceItem item) {
        return new DailyPriceItem(
                item.tradeDate(),
                item.openPrice(),
                item.highPrice(),
                item.lowPrice(),
                item.closePrice(),
                item.accumulatedVolume(),
                item.modified());
    }

    // 새로 받은 일봉 데이터를 DB에 저장한다.
    private int saveDailyPrices(MarketIndicatorSymbol indicator,
                                LocalDate startDate,
                                LocalDate endDate,
                                List<DailyPriceItem> items) {
        LocalDateTime fetchedAt = LocalDateTime.now();
        List<MarketDailyPrice> dailyPrices = new ArrayList<>();

        for (DailyPriceItem item : items) {
            LocalDate tradeDate = parseRequiredDate(item.tradeDate(), "거래일은 필수입니다.");
            if (tradeDate.isBefore(startDate) || tradeDate.isAfter(endDate)) {
                continue; // 응답받은 데이터의 거래일이 기간에 포함되는지를 확인
            }

            if (alreadyExists(indicator, tradeDate)) {
                continue; // 이미 해당 일자의 데이터가 DB에 저장되어 있는지를 확인
            }
            // 각 환율 / 주가지수 데이터를 기반으로 MarketDailyPrice 엔티티 생성 후 list에 담는다.
            dailyPrices.add(MarketDailyPrice.create(
                    indicator,
                    tradeDate,
                    parseRequiredBigDecimal(item.openPrice(), "시가는 필수입니다."),
                    parseRequiredBigDecimal(item.highPrice(), "고가는 필수입니다."),
                    parseRequiredBigDecimal(item.lowPrice(), "저가는 필수입니다."),
                    parseRequiredBigDecimal(item.closePrice(), "종가는 필수입니다."),
                    parseNullableLong(item.accumulatedVolume()),
                    parseYesNoOrOne(item.modified()),
                    fetchedAt));
        }
        // List에 담은 모든 MarketDailyPrice 엔티티들을 저장한다.
        marketDailyPriceRepository.saveAll(dailyPrices);
        return dailyPrices.size(); // DB에 추가로 저장한 종목 수를 리턴한다.
    }

    // 이미 해당 일자의 데이터가 DB에 저장되어있는지를 확인한다.
    private boolean alreadyExists(MarketIndicatorSymbol indicator, LocalDate tradeDate) {
        return marketDailyPriceRepository.existsByIndicatorTypeAndIndicatorSymbolAndTradeDate(
                indicator.getType(),
                indicator.getKisSymbol(),
                tradeDate);
    }

    private <T> List<T> safeList(List<T> items) {
        if (items == null) {
            return List.of();
        }

        return items;
    }

    private record DailyPriceItem(
            String tradeDate,
            String openPrice,
            String highPrice,
            String lowPrice,
            String closePrice,
            String accumulatedVolume,
            String modified
    ) {
    }
}
