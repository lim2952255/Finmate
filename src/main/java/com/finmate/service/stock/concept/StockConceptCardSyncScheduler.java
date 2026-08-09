package com.finmate.service.stock.concept;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "finmate.stock-concept.sync-enabled",
        havingValue = "true"
)
public class StockConceptCardSyncScheduler {
    private final StockConceptCardSyncService syncService;

    // 매주 월요일 오전 8시마다 개념카드를 update
    // 개념카드는 자주 업데이트할 필요가 없기 때문에 일주일에 한번만 수행
    @Scheduled(
            cron = "${finmate.stock-concept.sync-cron:0 0 8 * * MON}",
            zone = "${finmate.stock-concept.sync-zone:Asia/Seoul}"
    )
    public void syncWeekly() {
        try {
            StockConceptCardSyncService.SyncResult result = syncService.syncOfficialConceptCards();
            log.info(
                    "주식 개념 카드를 동기화했습니다. created={}, updated={}, unchanged={}",
                    result.createdCount(),
                    result.updatedCount(),
                    result.unchangedCount()
            );
        } catch (RuntimeException e) {
            log.warn("주식 개념 카드 동기화에 실패했습니다. 기존 개념 카드를 유지합니다.", e);
        }
    }
}
