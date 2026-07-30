package com.finmate.service.stock.market;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.repository.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NxtStockTradingPermissionApplyService {
    private final StockRepository stockRepository;

    // 국내종목들을 조회하고, 기존 종목정보와, 새로 조회한 NXT 허용코드 정보가 다를경우에는 해당 종목정보를 update한다.
    @Transactional
    public ApplyResult apply(Map<String, Integer> permissionCodeBySymbol) {
        List<Stock> domesticStocks = new ArrayList<>();
        domesticStocks.addAll(stockRepository.findByMarketType(StockMarketType.KOSPI));
        domesticStocks.addAll(stockRepository.findByMarketType(StockMarketType.KOSDAQ));

        int changedCount = 0;
        for (Stock stock : domesticStocks) {
            Integer permissionCode = permissionCodeBySymbol.get(stock.getSymbol());
            if (!Objects.equals(stock.getNxtTradingPermissionCode(), permissionCode)) {
                stock.updateNxtTradingPermissionCode(permissionCode);
                changedCount++;
            }
        }

        long matchedCount = domesticStocks.stream()
                .filter(stock -> stock.getNxtTradingPermissionCode() != null)
                .count();
        return new ApplyResult(matchedCount, changedCount);
    }

    public record ApplyResult(long matchedCount, int changedCount) {
    }
}
