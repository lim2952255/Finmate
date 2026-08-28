package com.finmate.service.stock.master;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "finmate.stock-master.sync-on-startup",
        havingValue = "true"
)
public class StockMasterStartupSyncRunner implements ApplicationRunner {
    private final StockMasterSyncService stockMasterSyncService;

    @Override
    public void run(ApplicationArguments args) {
        stockMasterSyncService.syncDailyDomesticStockMasters();
        stockMasterSyncService.syncDailyNasdaqStockMaster();
    }
}
