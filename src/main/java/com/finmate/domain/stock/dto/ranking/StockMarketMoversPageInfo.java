package com.finmate.domain.stock.dto.ranking;

import com.finmate.domain.stock.StockMarketType;
import lombok.Getter;

import java.util.List;

// view에 전달할 정보들을 담은 dto
// 각 시장별 + 랭킹 목록별로의 RankingBoard들의 list들을 관리하고, 이를 view에 전달한다.
@Getter
public class StockMarketMoversPageInfo {
    private final List<StockMarketType> marketTypes;
    private final List<StockRankingType> rankingTypes;
    private final List<StockRankingBoard> rankingBoards;

    public StockMarketMoversPageInfo(List<StockMarketType> marketTypes,
                                     List<StockRankingType> rankingTypes,
                                     List<StockRankingBoard> rankingBoards) {
        this.marketTypes = marketTypes;
        this.rankingTypes = rankingTypes;
        this.rankingBoards = rankingBoards;
    }
}
