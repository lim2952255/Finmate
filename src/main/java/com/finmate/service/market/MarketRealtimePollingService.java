package com.finmate.service.market;

import com.finmate.domain.market.MarketIndicatorSymbol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;

// 1분마다 해외 주가지수 및 환율 데이터를 REST API를 호출해서 받아서 저장하는 메서드
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketRealtimePollingService {
    private final MarketRealtimeQuoteService marketRealtimeQuoteService; // KIS API를 통해서 실시간 주가지수 및 환율 데이터를 받아서 MarketRealtimeMessage 객체로 변환한 다음, Redis에 저장하는 서비스

    // 1분마다 실행되는 스케줄러 -> 1분마다 KIS API를 통해서 실시간 주가지수 및 환율 데이터를 Redis에 캐싱한다.
    @Scheduled(
            fixedDelayString = "${finmate.market-realtime.refresh-interval-millis:60000}",
            initialDelayString = "${finmate.market-realtime.initial-delay-millis:1000}"
    )
    public void refreshPollingIndicators() {
        Arrays.stream(MarketIndicatorSymbol.values())
                .filter(MarketIndicatorSymbol::isPollingRealtimeIndicator)
                .forEach(this::refreshPollingIndicator);
    }

    private void refreshPollingIndicator(MarketIndicatorSymbol indicator) {
        try {
            // MarketRealtimeQuoteService를 통해서 실시간 주가지수 및 환율 데이터를 Redis에 캐싱한다.
            marketRealtimeQuoteService.refreshPollingIndicator(indicator);
        } catch (Exception e) {
            log.warn("시장지표 실시간 조회 갱신에 실패했습니다. indicator={}", indicator, e);
        }
    }
}
