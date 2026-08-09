package com.finmate.service.stock.concept;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 개념정보를 업데이트했을때, 스케줄러가 호출될 때까지 대기하는 것이 아니라, 수동으로 즉시 반영하는 클래스
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "finmate.stock-concept.sync-on-startup",
        havingValue = "true"
)
public class StockConceptCardStartupSyncRunner implements ApplicationRunner {
    private final StockConceptCardSyncService syncService;

    @Override
    public void run(ApplicationArguments args) {
        // 주식 개념 카드를 즉시 동기화한다.
        StockConceptCardSyncService.SyncResult result = syncService.syncOfficialConceptCards();
        log.info(
                "애플리케이션 시작 시 주식 개념 카드를 동기화했습니다. created={}, updated={}, unchanged={}",
                result.createdCount(),
                result.updatedCount(),
                result.unchangedCount()
        );
    }
}
