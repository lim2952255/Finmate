package com.finmate.controller.stock;

import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.dto.StockCatalogResponse.SearchResponse;
import com.finmate.domain.stock.dto.StockCatalogResponse.WatchlistResponse;
import com.finmate.domain.stock.dto.favorite.FavoriteStockPageInfo;
import com.finmate.domain.stock.dto.ranking.StockMarketMoversPageInfo;
import com.finmate.domain.stock.dto.search.StockSearchPageInfo;
import com.finmate.domain.stock.dto.search.StockSearchType;
import com.finmate.global.security.FinMateAuthenticatedPrincipal;
import com.finmate.service.stock.StockService;
import com.finmate.service.stock.ranking.StockMarketMoverService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 기존 StockService를 종목 검색·관심종목 React 화면의 JSON API로 연결한다.
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/stocks")
public class StockCatalogController {

    private final StockService stockService;
    private final StockMarketMoverService stockMarketMoverService;

    @GetMapping("/market-movers")
    public StockMarketMoversPageInfo marketMovers() {
        return stockMarketMoverService.getStockMarketMoversPageInfo();
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "STOCK") StockSearchType searchType,
            @RequestParam(required = false) StockMarketType marketType,
            @RequestParam(required = false, defaultValue = "0") int page,
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal sessionUser) {
        StockSearchPageInfo pageInfo = stockService.getStockSearchPageInfo(
                sessionUser.getId(), keyword, searchType, marketType, page);
        return SearchResponse.from(pageInfo, stockService.findFavoriteStockIds(sessionUser.getId()));
    }

    @GetMapping("/watchlist")
    public WatchlistResponse watchlist(
            @RequestParam(required = false, defaultValue = "0") int page,
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal sessionUser) {
        FavoriteStockPageInfo pageInfo = stockService.getFavoriteStockPageInfo(sessionUser.getId(), page);
        return WatchlistResponse.from(pageInfo);
    }

    @PostMapping("/favorite")
    public ResponseEntity<Void> toggleFavorite(
            @Valid @RequestBody FavoriteRequest request,
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal sessionUser) {
        stockService.toggleFavoriteStock(sessionUser.getId(), request.stockId());
        return ResponseEntity.noContent().build();
    }

    public record FavoriteRequest(@NotNull(message = "종목은 필수입니다.") Long stockId) {
    }
}
