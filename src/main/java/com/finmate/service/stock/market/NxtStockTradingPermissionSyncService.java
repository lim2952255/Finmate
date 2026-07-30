package com.finmate.service.stock.market;

import com.finmate.infra.nxt.NxtMarketDataClient;
import com.finmate.infra.nxt.NxtMarketDataClient.NxtStockTradingPermission;
import com.finmate.service.stock.market.NxtStockTradingPermissionApplyService.ApplyResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "finmate.nxt.sync-enabled", havingValue = "true", matchIfMissing = true)
public class NxtStockTradingPermissionSyncService {
    private final NxtMarketDataClient nxtMarketDataClient;
    private final NxtStockTradingPermissionApplyService applyService;

    // 매일 NXT 장 시작전인 7시 50분에 동기화를 진행한다.
    @Scheduled(
            cron = "${finmate.nxt.sync-cron:0 50 7 * * MON-FRI}",
            zone = "${finmate.nxt.sync-zone:Asia/Seoul}")
    public void scheduledRefresh() {
        refreshSafely();
    }

    private void refreshSafely() {
        try {
            refresh();
        } catch (RuntimeException e) {
            log.warn("NXT 거래대상 종목 동기화에 실패했습니다. 기존 정보를 유지합니다.", e);
        }
    }

    private void refresh() {
        // NXT Permissions 정보를 패치한다.
        List<NxtStockTradingPermission> permissions = nxtMarketDataClient.fetchStockTradingPermissions();
        Map<String, Integer> permissionCodeBySymbol = new HashMap<>();
        permissions.forEach(permission ->
                // Permissions에서 각 종목명과 각 종목에 대한 NXT 허용코드를 Map에 담는다.
                permissionCodeBySymbol.put(permission.symbol(), permission.permissionCode()));

        // 외부 API 호출은 트랜잭션 밖에서 끝내고, DB 조회와 변경만 별도의 짧은 트랜잭션으로 처리한다.
        ApplyResult result = applyService.apply(permissionCodeBySymbol);
        log.info("NXT 거래대상 종목 정보를 동기화했습니다. fetched={}, matched={}, changed={}",
                permissions.size(),
                result.matchedCount(),
                result.changedCount());
    }
}
