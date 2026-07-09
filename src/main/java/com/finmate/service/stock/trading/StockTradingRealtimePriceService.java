package com.finmate.service.stock.trading;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.infra.kis.stock.realtime.KisRealtimeApi;
import com.finmate.infra.kis.stock.realtime.KisRealtimePayload;
import com.finmate.infra.kis.stock.realtime.KisRealtimeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static com.finmate.infra.kis.parser.KisValueParser.parsePositiveBigDecimal;

// 주식 주문을 체결할 때 사용할 실제 체결 기준 가격을 실시간 체결가/호가 데이터에서 찾는 서비스
// 즉 시장가 매수/매도 가격 결정 + 지정가 / 예약 주문 체결 조건 만족 여부 판단 등을 수행하는 서비스
@Service
@RequiredArgsConstructor
public class StockTradingRealtimePriceService {
    private static final String NASDAQ_REALTIME_PREFIX = "DNAS";

    private final KisRealtimeStore realtimeStore; // KIS WebSocket으로부터 받은 실시간 체결가 + 실시간 호가 데이터 저장소

    // 체결가능한 가격을 반드시 찾아서 리턴하는 메서드. 만약 체결가능한 가격을 찾지 못하면 예외를 발생시킨다.
    public BigDecimal getExecutablePrice(Stock stock, StockOrderSide side) {
        return findExecutablePrice(stock, side)
                .orElseThrow(() -> new RuntimeException("실시간 호가 수신 전이라 시장가 주문을 처리할 수 없습니다."));
    }

    // 현재 체결가능한 가격을 찾는다.
    public Optional<BigDecimal> findExecutablePrice(Stock stock, StockOrderSide side) {
        KisRealtimeApi orderbookApi = orderbookApi(stock); // 실시간 호가 api를 꺼낸다.
        String trKey = realtimeKey(stock);

        // 실시간 호가 데이터에서 매수 1호가 / 매도 1호가 데이터 정보를 읽는다.
        Optional<BigDecimal> orderbookPrice = realtimeStore.get(orderbookApi, trKey)
                .map(KisRealtimePayload::values)
                .flatMap(values -> parsePositiveBigDecimal(side == StockOrderSide.BUY
                        ? value(values, "ASKP1", "pask1") // 현재 주문이 매수면 매도 1호가 가격을 리턴
                        : value(values, "BIDP1", "pbid1"))); // 현재 주문이 매도면 매수 1호가 가격을 리턴
        if (orderbookPrice.isPresent()) {
            return orderbookPrice;
        }
        // 호가 데이터가 없다면, 실시간 체결 데이터를 가져와서 실시간 체결 데이터 내의 호가성 필드를 찾는다.
        Optional<KisRealtimePayload> tradePayload = realtimeStore.get(tradeApi(stock), trKey);
        Optional<BigDecimal> tradeBestPrice = tradePayload
                .map(KisRealtimePayload::values)
                .flatMap(values -> parsePositiveBigDecimal(side == StockOrderSide.BUY
                        ? value(values, "ASKP1", "PASK")
                        : value(values, "BIDP1", "PBID")));
        if (tradeBestPrice.isPresent()) {
            return tradeBestPrice;
        }

        // 호가 데이터도 없고, 실시간 체결 데이터 내의 호가성 필드도 찾지 못하면, 실시간 체결가를 리턴한다.
        return tradePayload
                .map(KisRealtimePayload::price)
                .flatMap(value -> parsePositiveBigDecimal(value));
    }

    // 현재 실시간 체결가를 찾아서 리턴하는 메서드
    public Optional<BigDecimal> findCurrentTradePrice(Stock stock) {
        return realtimeStore.get(tradeApi(stock), realtimeKey(stock))
                .map(KisRealtimePayload::price)
                .flatMap(value -> parsePositiveBigDecimal(value));
    }

    // 실시간 체결가 api
    private KisRealtimeApi tradeApi(Stock stock) {
        if (stock.getMarketType() == StockMarketType.NASDAQ) {
            return KisRealtimeApi.OVERSEAS_STOCK_TRADE;
        }

        return KisRealtimeApi.DOMESTIC_STOCK_TRADE;
    }

    // 실시간 호가 api
    private KisRealtimeApi orderbookApi(Stock stock) {
        if (stock.getMarketType() == StockMarketType.NASDAQ) {
            return KisRealtimeApi.OVERSEAS_STOCK_ORDERBOOK;
        }

        return KisRealtimeApi.DOMESTIC_STOCK_ORDERBOOK;
    }

    private String realtimeKey(Stock stock) {
        if (stock.getRealtimeSymbol() != null && !stock.getRealtimeSymbol().isBlank()) {
            return stock.getRealtimeSymbol().trim();
        }

        if (stock.getMarketType() == StockMarketType.NASDAQ) {
            return NASDAQ_REALTIME_PREFIX + stock.getSymbol().trim();
        }

        return stock.getSymbol().trim();
    }

    private String value(Map<String, String> values, String firstKey, String secondKey) {
        String firstValue = values.get(firstKey);
        if (firstValue != null && !firstValue.isBlank()) {
            return firstValue;
        }

        return values.get(secondKey);
    }
}
