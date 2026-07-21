package com.finmate.domain.stock.dto.favorite;

import com.finmate.domain.stock.FavoriteStock;
import com.finmate.global.pagination.PaginationInfo;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

import static com.finmate.domain.stock.dto.industry.StockIndustryDisplayUtils.unknownIfBlank;

// 관심종목 목록 화면에 필요한 정보 DTO
@Getter
public class FavoriteStockPageInfo {
    private final Page<FavoriteStock> favoriteStockPage;
    private final List<FavoriteStock> favoriteStocks;
    private final PaginationInfo pagination;
    private final Map<Long, String> industryNamesByStockId;

    public FavoriteStockPageInfo(Page<FavoriteStock> favoriteStockPage) {
        this(favoriteStockPage, Map.of());
    }

    public FavoriteStockPageInfo(Page<FavoriteStock> favoriteStockPage,
                                 Map<Long, String> industryNamesByStockId) {
        this.favoriteStockPage = favoriteStockPage;
        this.favoriteStocks = favoriteStockPage.getContent();
        this.pagination = PaginationInfo.from(favoriteStockPage);
        this.industryNamesByStockId = industryNamesByStockId == null ? Map.of() : Map.copyOf(industryNamesByStockId);
    }

    public String getIndustryName(Long stockId) {
        return unknownIfBlank(industryNamesByStockId.get(stockId));
    }
}
