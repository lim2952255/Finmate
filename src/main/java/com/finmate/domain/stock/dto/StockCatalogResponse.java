package com.finmate.domain.stock.dto;

import com.finmate.domain.stock.FavoriteStock;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.dto.favorite.FavoriteStockPageInfo;
import com.finmate.domain.stock.dto.search.StockSearchPageInfo;
import com.finmate.domain.stock.market.StockMarketSchedules;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

// 종목 검색과 관심종목 화면에서 공통으로 사용하는 React JSON 응답 구조다.
public final class StockCatalogResponse {

    private StockCatalogResponse() {
    }

    public record SearchResponse(
            String keyword,
            String searchType,
            String marketType,
            String searchPlaceholder,
            List<OptionResponse> searchTypes,
            List<OptionResponse> marketTypes,
            List<StockResponse> stocks,
            PageResponse page
    ) {
        public static SearchResponse from(StockSearchPageInfo pageInfo, List<Long> favoriteStockIds) {
            Set<Long> favorites = Set.copyOf(favoriteStockIds);
            return new SearchResponse(
                    pageInfo.getKeyword(),
                    pageInfo.getSearchType().name(),
                    pageInfo.getMarketType() == null ? null : pageInfo.getMarketType().name(),
                    pageInfo.getSearchPlaceholder(),
                    pageInfo.getSearchTypes().stream()
                            .map(type -> new OptionResponse(type.name(), type.getDisplayName()))
                            .toList(),
                    pageInfo.getMarketTypes().stream()
                            .map(type -> new OptionResponse(type.name(), type.name()))
                            .toList(),
                    pageInfo.getStocks().stream()
                            .map(stock -> StockResponse.from(
                                    stock,
                                    pageInfo.getIndustryName(stock.getId()),
                                    favorites.contains(stock.getId()),
                                    null))
                            .toList(),
                    PageResponse.from(
                            pageInfo.getStockPage().getNumber(),
                            pageInfo.getStockPage().getTotalPages(),
                            pageInfo.getStockPage().getTotalElements(),
                            pageInfo.getPagination().getPageNumbers()));
        }
    }

    public record WatchlistResponse(List<StockResponse> stocks, PageResponse page) {
        public static WatchlistResponse from(FavoriteStockPageInfo pageInfo) {
            return new WatchlistResponse(
                    pageInfo.getFavoriteStocks().stream()
                            .map(favorite -> StockResponse.from(
                                    favorite.getStock(),
                                    pageInfo.getIndustryName(favorite.getStock().getId()),
                                    true,
                                    favorite.getCreatedAt()))
                            .toList(),
                    PageResponse.from(
                            pageInfo.getFavoriteStockPage().getNumber(),
                            pageInfo.getFavoriteStockPage().getTotalPages(),
                            pageInfo.getFavoriteStockPage().getTotalElements(),
                            pageInfo.getPagination().getPageNumbers()));
        }
    }

    public record StockResponse(
            Long id,
            String symbol,
            String nameKo,
            String nameEn,
            String marketType,
            String industryName,
            String securityType,
            String currency,
            boolean tradable,
            boolean tradingAvailable,
            String tradingTimeDescription,
            boolean favorite,
            LocalDateTime favoriteCreatedAt
    ) {
        private static StockResponse from(
                Stock stock,
                String industryName,
                boolean favorite,
                LocalDateTime favoriteCreatedAt) {
            boolean tradable = stock.isActive() && stock.isTradable() && !stock.isTradingHalted();
            return new StockResponse(
                    stock.getId(),
                    stock.getSymbol(),
                    stock.getNameKo(),
                    stock.getNameEn(),
                    stock.getMarketType().name(),
                    industryName,
                    stock.getSecurityType().name(),
                    stock.getCurrency(),
                    tradable,
                    tradable && StockMarketSchedules.isTradingTimeNow(stock),
                    StockMarketSchedules.describeTradingHours(stock),
                    favorite,
                    favoriteCreatedAt);
        }
    }

    public record OptionResponse(String value, String label) {
    }

    public record PageResponse(
            int number,
            int totalPages,
            long totalElements,
            List<Integer> pageNumbers,
            boolean first,
            boolean last
    ) {
        private static PageResponse from(
                int number,
                int totalPages,
                long totalElements,
                List<Integer> pageNumbers) {
            return new PageResponse(
                    number,
                    totalPages,
                    totalElements,
                    pageNumbers,
                    number == 0,
                    totalPages == 0 || number >= totalPages - 1);
        }
    }
}
