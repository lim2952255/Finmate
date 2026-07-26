package com.finmate.service.stock.trading;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        // 설정값에 따라 해당 스케줄러 bean을 등록할지 여부를 결정한다.(테스트환경에서는 스케줄러를 끄기 위해 사용한다)
        name = "finmate.trading.expiration-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class StockOrderExpirationScheduler {
    private final StockOrderExpirationService expirationService; // 주문 만료 여부를 검사하고 만료처리를 수행한다.

    @Scheduled(
            // 애플리케이션이 시작되면 바로 실행되며, 이후 10초마다 작업을 수행한다.
            fixedDelayString = "${finmate.trading.expiration-interval-millis:10000}",
            initialDelayString = "${finmate.trading.expiration-initial-delay-millis:0}"
    )
    // 애플리케이션이 종료되거나 대기상태일때 만료기한이 지나버린 경우, 해당 주문들을 만료시키는 메서드를 호출한다.
    public void expireOverdueOrdersAndReservations() {
        expirationService.expireOverdueOrdersAndReservations(LocalDateTime.now());
    }
}
