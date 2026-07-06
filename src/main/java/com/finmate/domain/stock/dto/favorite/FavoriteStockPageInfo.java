package com.finmate.domain.stock.dto.favorite;

import com.finmate.domain.stock.FavoriteStock;
import com.finmate.global.pagination.PaginationInfo;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

// 관심종목 목록 화면에 필요한 정보 DTO
@Getter
public class FavoriteStockPageInfo {
    private final Page<FavoriteStock> favoriteStockPage;
    private final List<FavoriteStock> favoriteStocks;
    private final PaginationInfo pagination;

    public FavoriteStockPageInfo(Page<FavoriteStock> favoriteStockPage) {
        this.favoriteStockPage = favoriteStockPage;
        this.favoriteStocks = favoriteStockPage.getContent();
        this.pagination = PaginationInfo.from(favoriteStockPage);
    }
}
