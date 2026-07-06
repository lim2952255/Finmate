package com.finmate.domain.stock.dto.search;

import com.finmate.domain.stock.Stock;
import com.finmate.global.pagination.PaginationInfo;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

// 종목 검색 화면에 필요한 정보 DTO
@Getter
public class StockSearchPageInfo {
    private final String keyword;
    private final Page<Stock> stockPage;
    private final List<Stock> stocks;
    private final PaginationInfo pagination;

    public StockSearchPageInfo(String keyword,
                               Page<Stock> stockPage) {
        this.keyword = keyword;
        this.stockPage = stockPage;
        this.stocks = stockPage.getContent();
        this.pagination = PaginationInfo.from(stockPage);
    }
}
