package com.finmate.domain.stock.dto.ranking;

import com.finmate.domain.stock.StockMarketType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// 개별 랭킹 아이템들을 모아서 랭킹 게시판 전체를 표현하는 DTO
// 개별 랭킹 아이템정보인 StockRankingItem의 list를 저장하고 관리한다.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockRankingBoard {
    private static final DateTimeFormatter REFRESHED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private StockMarketType marketType;
    private StockRankingType rankingType;
    private boolean marketOpen;
    private LocalDateTime refreshedAt;

    private List<StockRankingItem> items = new ArrayList<>();

    public boolean hasItems() {
        return items != null && !items.isEmpty();
    }

    public String getDisplayRefreshedAt() {
        if (refreshedAt == null) {
            return "-";
        }

        return refreshedAt.format(REFRESHED_AT_FORMATTER);
    }
}
