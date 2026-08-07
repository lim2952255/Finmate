package com.finmate.domain.stock.dto.detail;

import com.finmate.domain.stock.metadata.domestic.DomesticStockCurrentQuote;
import com.finmate.infra.kis.parser.KisValueParser;
import com.finmate.infra.kis.stock.detail.KisCurrentPriceResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Redis에 캐싱할 DTO객체 생성
// 해당 DTO에는 주식 현재가, 거래량, 시가/고가/종가/저가, PER, PBR, EPS, BPS등의 정보를 저장한다.
public record DomesticStockCurrentQuoteSnapshot(
        BigDecimal currentPrice, // 현재 시장가
        BigDecimal changeAmount, // 전일 종가 대비 가격변화량
        String changeSign, // 상승/하락 여부를 나타내는 코드
        BigDecimal changeRate, // 전일 종가 대비 등락률
        BigDecimal openPrice, // 시가
        BigDecimal highPrice, // 고가
        BigDecimal lowPrice, // 저가
        Long accumulatedVolume, // 금일 누적 거래량
        BigDecimal accumulatedTradeAmount, // 금일 누적 거래대금
        BigDecimal per, // PER = 주가 / EPS 또는 시가총액 / 당기순이익
        BigDecimal pbr, // PBR = 주가 / BPS 또는 시가총액 / 자기자본
        BigDecimal eps, // EPS = 주당순이익 = 당기순이익 / 주식 수 (한주당 얼마의 순이익이 났는지)
        BigDecimal bps, // BPS = 주당순자산 = 자기자본 / 주식 수
        BigDecimal marketCap, // 기업의 시가총액 = 상장 주식 수 * 주가
        Long listedShares, // 기업의 상장 주식수
        BigDecimal w52HighPrice, // 52주 최고가
        LocalDate w52HighDate, // 52주 최고가가 기록된 날짜
        BigDecimal w52HighRate, // 52주 최고가와 비교하여 현재 가격의 비율/등락률
        BigDecimal w52LowPrice, // 52주 최저가
        LocalDate w52LowDate, // 52주 최저가가 기록된 날짜
        BigDecimal w52LowRate, // 52주 최저가와 비교하여 현재 가격의 비율/등락률
        Long foreignHoldingQuantity, // 외국인이 현재 보유하고 있는 주식 수
        BigDecimal foreignExhaustionRate, // 외국인 보유 가능 한도 대비 실제 외국인 보유 비율
        Long foreignNetBuyQuantity, // 외국인 순매수 수량 (순매수: 외국인 매수량 - 외국인 매도량)
        BigDecimal totalLoanBalanceRate, // 신용 잔고율 비율
        LocalDateTime fetchedAt // 마지막으로 fetch한 시점
) {
    // KIS API로부터 데이터(KisCurrentPriceResponse)를 받으면, 이를 Redis에 저장할 DTO로 변환해서 저장한다.
    public static DomesticStockCurrentQuoteSnapshot from(KisCurrentPriceResponse response, LocalDateTime fetchedAt) {
        KisCurrentPriceResponse.CurrentPrice item = response == null ? null : response.output();
        if (item == null) {
            return null;
        }
        return new DomesticStockCurrentQuoteSnapshot(
                decimal(item.currentPrice()),
                KisValueParser.applyChangeSign(decimal(item.changeAmount()), item.changeSign()),
                item.changeSign(),
                KisValueParser.applyChangeSign(decimal(item.changeRate()), item.changeSign()),
                decimal(item.openPrice()), decimal(item.highPrice()), decimal(item.lowPrice()),
                longValue(item.accumulatedVolume()), decimal(item.accumulatedTradeAmount()),
                decimal(item.per()), decimal(item.pbr()), decimal(item.eps()), decimal(item.bps()),
                decimal(item.marketCapitalization()), longValue(item.listedShareCount()),
                decimal(item.fiftyTwoWeekHighPrice()), date(item.fiftyTwoWeekHighDate()),
                decimal(item.fiftyTwoWeekHighToCurrentRate()), decimal(item.fiftyTwoWeekLowPrice()),
                date(item.fiftyTwoWeekLowDate()), decimal(item.fiftyTwoWeekLowToCurrentRate()),
                longValue(item.foreignHoldingQuantity()), decimal(item.foreignExhaustionRate()),
                longValue(item.foreignNetBuyQuantity()), decimal(item.totalLoanBalanceRate()), fetchedAt);
    }

    public static DomesticStockCurrentQuoteSnapshot from(DomesticStockCurrentQuote quote) {
        if (quote == null) {
            return null;
        }
        return new DomesticStockCurrentQuoteSnapshot(
                quote.getCurrentPrice(), quote.getChangeAmount(), quote.getChangeSign(), quote.getChangeRate(),
                quote.getOpenPrice(), quote.getHighPrice(), quote.getLowPrice(), quote.getAccumulatedVolume(),
                quote.getAccumulatedTradeAmount(), quote.getPer(), quote.getPbr(), quote.getEps(), quote.getBps(),
                quote.getMarketCap(), quote.getListedShares(), quote.getW52HighPrice(), quote.getW52HighDate(),
                quote.getW52HighRate(), quote.getW52LowPrice(), quote.getW52LowDate(), quote.getW52LowRate(),
                quote.getForeignHoldingQuantity(), quote.getForeignExhaustionRate(), quote.getForeignNetBuyQuantity(),
                quote.getTotalLoanBalanceRate(), quote.getUpdatedAt());
    }

    private static BigDecimal decimal(String value) {
        return KisValueParser.parseNullableBigDecimalOrNull(value);
    }

    private static Long longValue(String value) {
        return KisValueParser.parseNullableLongOrNull(value);
    }

    private static LocalDate date(String value) {
        return KisValueParser.parseNullableDate(value);
    }
}
