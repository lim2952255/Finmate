package com.finmate.domain.stock.dto.search;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.global.pagination.PaginationInfo;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.finmate.domain.stock.dto.industry.StockIndustryDisplayUtils.unknownIfBlank;

// 종목 검색 화면에 필요한 정보 DTO
@Getter
public class StockSearchPageInfo {
    private final String keyword;
    private final StockSearchType searchType;
    private final StockMarketType marketType;
    private final Page<Stock> stockPage;
    private final List<Stock> stocks;
    private final PaginationInfo pagination;
    private final Map<Long, String> industryNamesByStockId;
    private final List<StockSearchType> searchTypes;
    private final List<StockMarketType> marketTypes;

    public StockSearchPageInfo(String keyword,
                               Page<Stock> stockPage) {
        this(keyword, StockSearchType.STOCK, null, stockPage, Map.of());
    }

    public StockSearchPageInfo(String keyword,
                               StockSearchType searchType,
                               Page<Stock> stockPage,
                               Map<Long, String> industryNamesByStockId) {
        this(keyword, searchType, null, stockPage, industryNamesByStockId);
    }

    public StockSearchPageInfo(String keyword,
                               StockSearchType searchType,
                               StockMarketType marketType,
                               Page<Stock> stockPage,
                               Map<Long, String> industryNamesByStockId) {
        this.keyword = keyword;
        this.searchType = searchType == null ? StockSearchType.STOCK : searchType;
        this.marketType = marketType;
        this.stockPage = stockPage;
        this.stocks = stockPage.getContent();
        this.pagination = PaginationInfo.from(stockPage);
        this.industryNamesByStockId = industryNamesByStockId == null ? Map.of() : Map.copyOf(industryNamesByStockId);
        this.searchTypes = List.of(StockSearchType.values());
        this.marketTypes = Arrays.asList(StockMarketType.values());
    }

    public String getIndustryName(Long stockId) {
        return unknownIfBlank(industryNamesByStockId.get(stockId));
    }

    public String getSearchPlaceholder() {
        return searchType.getPlaceholder();
    }

    public String getMarketTypeDisplayName() {
        return marketType == null ? "전체 시장" : marketType.name();
    }
}
