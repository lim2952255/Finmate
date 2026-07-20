package com.finmate.service.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.domain.market.dto.MarketRealtimeMessage;
import com.finmate.infra.kis.exchange.KisOverseasMarketMinuteChartPriceClient;
import com.finmate.infra.kis.exchange.KisOverseasMarketMinuteChartPriceClient.MinuteChartPriceResponse;
import com.finmate.infra.kis.stock.realtime.KisRealtimeApi;
import com.finmate.infra.kis.stock.realtime.KisRealtimePayload;
import com.finmate.infra.kis.stock.realtime.KisRealtimeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static com.finmate.infra.kis.parser.KisValueParser.applyChangeSign;
import static com.finmate.infra.kis.parser.KisValueParser.firstObject;
import static com.finmate.infra.kis.parser.KisValueParser.firstPresentText;
import static com.finmate.infra.kis.parser.KisValueParser.firstPresentValue;
import static com.finmate.infra.kis.parser.KisValueParser.parseNullableBigDecimalOrNull;
import static com.finmate.infra.kis.parser.KisValueParser.parseNullableLongOrNull;

// 국내 주가지수 / 해외 주가지수 / 환율 데이터를 MarketRealtimeMessage 형식으로 통합하여 관리하는 서비스
// 국내 주가지수는 WebSocket을 통해서 받고, 해외 주가지수 및 환율 데이터는 REST API를 통해서 받기 때문에 응답데이터의 구조와 형식이 모두 다르다.
// 따라서 이를 MarketRealtimeMessage라는 공통된 형식으로 통합하여 변환 및 관리한다.
@Service
@RequiredArgsConstructor
public class MarketRealtimeQuoteService {
    private static final String MARKET_REALTIME_MESSAGE_TYPE = "MARKET_REALTIME";
    private static final String KIS_WEBSOCKET_SOURCE = "KIS_WEBSOCKET";
    private static final String KIS_REST_REDIS_SOURCE = "KIS_REST_REDIS";

    private final KisOverseasMarketMinuteChartPriceClient minuteChartPriceClient; // 환율 / 해외 주가지수의 분봉 데이터를 받는 클라이언트
    private final MarketRealtimeCacheService marketRealtimeCacheService; // 환율 / 해외 주가지수를 Redis에 캐싱하는 서비스
    private final KisRealtimeStore realtimeStore; // KIS WebSocket으로 받은 실시간 국내 주가지수 데이터를 저장하는 저장소

    @Value("${finmate.market-realtime.cache-ttl-seconds:120}")
    private long cacheTtlSeconds; // Redis에 데이터를 캐싱할 TTL을 설정한다.

    // indicator에 따라 최신 데이터를 꺼내는 메서드
    public Optional<MarketRealtimeMessage> getLatest(MarketIndicatorSymbol indicator) {
        if (indicator == null) {
            return Optional.empty();
        }

        // indicator가 국내 주가지수라면 realtimeStore에서 실시간 주가지수 데이터를 꺼낸다.
        if (indicator.isDomesticRealtimeIndex()) {
            return realtimeStore.get(KisRealtimeApi.DOMESTIC_INDEX_TRADE, indicator.getKisSymbol())
                    // 응답받은 데이터를 toDomesticRealtimeMessage 메서드를 호출하여 MarketRealtimeMessage 객체로 변환한다.
                    .map(payload -> toDomesticRealtimeMessage(indicator, payload));
        }

        // indicator가 해외 주가지수 or 환율이라면 marketRealtimeCacheService를 통해 Redis에서 데이터를 꺼낸다.
        if (indicator.isPollingRealtimeIndicator()) {
            return marketRealtimeCacheService.get(indicator)
                    // 기본적으로는 1분마다 스케줄러가 호출되면서 API를 호출하여 해외 주가지수 및 환율 데이터를 받아서 Redis에 캐싱하지만, 만약 현재 Redis에 데이터가 없으면 명시적으로 refreshPollingIndicator를 호출해서 데이터를 가져와 Redis에 캐싱한다.
                    .or(() -> refreshPollingIndicator(indicator)); // 만약 Redis에 저장된 데이터가 없으면, RefreshPollingIndicator를 호출해서 KIS API를 호출해 데이터를 가져와 Redis에 캐싱한다.
        }

        return Optional.empty();
    }

    // MinuteChartPriceClient를 통해 KIS API를 호출하여 해외 주가지수 및 환율 분봉 데이터를 받아 Redis에 캐싱하는 메서드
    // refreshPollingIndicator는 1분마다 스케줄러가 호출하면서 데이터를 Redis에 캐싱한다.
    public Optional<MarketRealtimeMessage> refreshPollingIndicator(MarketIndicatorSymbol indicator) {
        if (indicator == null || !indicator.isPollingRealtimeIndicator()) {
            return Optional.empty();
        }

        // MinuteChartPriceClient를 통해 해외 주가지수 및 환율 분봉데이터를 받는다.
        MinuteChartPriceResponse response = minuteChartPriceClient.fetchMinuteChartPrice(
                indicator.getKisMarketDivisionCode(),
                indicator.getKisSymbol());
        // KIS API를 호출하여 받은 응답 데이터를 MarketRealtimeMessage 객체로 변환한다.
        MarketRealtimeMessage message = toPollingRealtimeMessage(indicator, response);
        // 변환한 MarketRealtimeMessage 객체를 Redis에 캐싱한다.
        marketRealtimeCacheService.put(message, Duration.ofSeconds(cacheTtlSeconds));
        return Optional.of(message);
    }

    // KIS WebSocket payload를 국내 주가지수 MarketRealtimeMessage 객체로 변환한다.
    public MarketRealtimeMessage toDomesticRealtimeMessage(MarketIndicatorSymbol indicator,
                                                           KisRealtimePayload payload) {
        Map<String, String> values = payload.values();
        String changeSign = firstPresentValue(values, "prdy_vrss_sign");
        BigDecimal currentPrice = parseNullableBigDecimalOrNull(payload.price());
        BigDecimal change = applyChangeSign(parseNullableBigDecimalOrNull(payload.change()), changeSign);
        BigDecimal changeRate = applyChangeSign(parseNullableBigDecimalOrNull(payload.changeRate()), changeSign);
        BigDecimal openPrice = parseNullableBigDecimalOrNull(firstPresentValue(values, "oprc_nmix"));

        // WebSocket 응답 데이터를 MarketRealtimeMessage 객체로 매핑한다.
        return new MarketRealtimeMessage(
                MARKET_REALTIME_MESSAGE_TYPE,
                indicator,
                indicator.getType(),
                indicator.getDisplayName(),
                indicator.getNameKo(),
                indicator.getUnit(),
                indicator.getFractionDigits(),
                KIS_WEBSOCKET_SOURCE,
                currentPrice,
                openPrice,
                parseNullableBigDecimalOrNull(firstPresentValue(values, "nmix_hgpr")),
                parseNullableBigDecimalOrNull(firstPresentValue(values, "nmix_lwpr")),
                change,
                changeRate,
                changeSign,
                parseNullableLongOrNull(firstPresentValue(values, "acml_vol")),
                parseNullableBigDecimalOrNull(firstPresentValue(values, "acml_tr_pbmn")),
                null,
                payload.tradeTime(),
                priceChangeClass(change, changeSign),
                payload.receivedAt());
    }

    // KIS API를 통해 응답받은 해외 주가지수 및 환율 데이터를 Redis에 캐싱하기 전에, 이를 MarketRealtimeMessage 객체로 변환하는 메서드
    private MarketRealtimeMessage toPollingRealtimeMessage(MarketIndicatorSymbol indicator,
                                                           MinuteChartPriceResponse response) {
        JsonNode summary = firstObject(response.output1());
        JsonNode firstMinute = firstObject(response.output2());
        JsonNode values = summary == null ? firstMinute : summary;
        if (values == null) {
            throw new RuntimeException("KIS 해외지수분봉조회 응답에 시세 데이터가 없습니다. indicator=" + indicator);
        }

        BigDecimal currentPrice = parseNullableBigDecimalOrNull(firstPresentText(values,
                "ovrs_nmix_prpr", "ovrs_prod_prpr", "last", "LAST", "prpr", "stck_prpr"));
        BigDecimal previousClosePrice = parseNullableBigDecimalOrNull(firstPresentText(values,
                "ovrs_nmix_prdy_clpr", "prdy_clpr", "base", "BASE"));
        BigDecimal change = parseNullableBigDecimalOrNull(firstPresentText(values,
                "ovrs_nmix_prdy_vrss", "prdy_vrss", "diff", "DIFF"));
        BigDecimal changeRate = parseNullableBigDecimalOrNull(firstPresentText(values,
                "prdy_ctrt", "rate", "RATE"));
        String changeSign = firstPresentText(values, "prdy_vrss_sign", "sign", "SIGN");

        if (change == null && currentPrice != null && previousClosePrice != null) {
            change = currentPrice.subtract(previousClosePrice);
        }
        if (changeRate == null && change != null
                && previousClosePrice != null
                && previousClosePrice.compareTo(BigDecimal.ZERO) != 0) {
            changeRate = change.multiply(BigDecimal.valueOf(100))
                    .divide(previousClosePrice, 2, RoundingMode.HALF_UP);
        }

        change = applyChangeSign(change, changeSign);
        changeRate = applyChangeSign(changeRate, changeSign);

        // KIS API를 통해 응답받은 해외 주가지수 및 환율 데이터를 MarketRealtimeMessage 객체로 변환한다.
        return new MarketRealtimeMessage(
                MARKET_REALTIME_MESSAGE_TYPE,
                indicator,
                indicator.getType(),
                indicator.getDisplayName(),
                indicator.getNameKo(),
                indicator.getUnit(),
                indicator.getFractionDigits(),
                KIS_REST_REDIS_SOURCE,
                currentPrice,
                parseNullableBigDecimalOrNull(firstPresentText(values, "ovrs_prod_oprc", "ovrs_nmix_oprc", "open", "OPEN")),
                parseNullableBigDecimalOrNull(firstPresentText(values, "ovrs_prod_hgpr", "ovrs_nmix_hgpr", "high", "HIGH")),
                parseNullableBigDecimalOrNull(firstPresentText(values, "ovrs_prod_lwpr", "ovrs_nmix_lwpr", "low", "LOW")),
                change,
                changeRate,
                changeSign,
                parseNullableLongOrNull(firstPresentText(values, "acml_vol", "tvol", "TVOL", "evol", "EVOL")),
                parseNullableBigDecimalOrNull(firstPresentText(values, "acml_tr_pbmn", "tamt", "TAMT")),
                firstPresentText(firstMinute, "stck_bsop_date", "xymd", "XYMD", "kymd", "KYMD", "tymd", "TYMD"),
                firstPresentText(firstMinute, "stck_cntg_hour", "xhms", "XHMS", "khms", "KHMS"),
                priceChangeClass(change, changeSign),
                LocalDateTime.now());
    }

    private String priceChangeClass(BigDecimal change, String changeSign) {
        String sign = changeSign == null ? "" : changeSign.trim();
        if ("1".equals(sign) || "2".equals(sign)) {
            return "bullish";
        }

        if ("4".equals(sign) || "5".equals(sign)) {
            return "bearish";
        }

        if (change == null || change.compareTo(BigDecimal.ZERO) == 0) {
            return "flat";
        }

        return change.signum() > 0 ? "bullish" : "bearish";
    }
}
