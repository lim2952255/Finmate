package com.finmate.service.stock.ranking;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.dto.ranking.StockMarketMoversPageInfo;
import com.finmate.domain.stock.dto.ranking.StockRankingBoard;
import com.finmate.domain.stock.dto.ranking.StockRankingItem;
import com.finmate.domain.stock.dto.ranking.StockRankingType;
import com.finmate.infra.kis.stock.ranking.KisStockRankingClient;
import com.finmate.infra.kis.stock.ranking.KisStockRankingClient.DomesticRankingItem;
import com.finmate.infra.kis.stock.ranking.KisStockRankingClient.DomesticRankingResponse;
import com.finmate.infra.kis.stock.ranking.KisStockRankingClient.OverseasRankingItem;
import com.finmate.infra.kis.stock.ranking.KisStockRankingClient.OverseasRankingResponse;
import com.finmate.repository.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.finmate.infra.kis.parser.KisValueParser.parseNullableBigDecimal;
import static com.finmate.infra.kis.parser.KisValueParser.parseNullableLong;

// 랭킹 갱신/조회 전체 흐름을 조율하는 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class StockMarketMoverService {
    // 랭킹 수 , 시장 종류, 랭킹 타입을 저장하는 상수
    private static final int RANKING_LIMIT = 10;
    private static final List<StockMarketType> MARKET_TYPES =
            List.of(StockMarketType.KOSPI, StockMarketType.KOSDAQ, StockMarketType.NASDAQ);
    private static final List<StockRankingType> RANKING_TYPES =
            List.of(StockRankingType.TRADE_AMOUNT, StockRankingType.VOLUME);

    private final KisStockRankingClient kisStockRankingClient; // KIS API 호출을 통해 랭킹 데이터를 받아서 레코드로 리턴하는 클라이언트
    private final StockRankingCacheService stockRankingCacheService; // 랭킹 데이터를 Redis에 캐싱하는 서비스
    private final StockMarketSessionService stockMarketSessionService; // 현재 장이 열려있는 판단하는 서비스
    private final StockRepository stockRepository; // stock을 저장하는 repository

    // Redis TTL 설정값 -> 설정파일에 있는 값으로 설정하며, 설정파일에 값이 따로 없다면 기본값으로 30초를 사용
    @Value("${finmate.stock-ranking.open-cache-ttl-seconds:30}")
    private long openCacheTtlSeconds;

    // 장이 마감했을때 Redis 캐시를 몇초동안 유지할지, 설정파일에 값이 따로 없다면 기본값으로 86400초(2시간)을 사용한다.
    @Value("${finmate.stock-ranking.closed-cache-ttl-seconds:86400}")
    private long closedCacheTtlSeconds;

    // Redis에서 캐시를 조회해서 해당 정보를 RankingBoard에 담아서 리턴한다.(즉 매번 API호출을 통해 랭킹 데이터를 받는 것이 아니라, Redis 캐시에서 데이터를 받아온다)
    public StockMarketMoversPageInfo getStockMarketMoversPageInfo() {
        List<StockRankingBoard> rankingBoards = new ArrayList<>();
        for (StockMarketType marketType : MARKET_TYPES) {
            for (StockRankingType rankingType : RANKING_TYPES) {

                // 아직 Redis 캐시에 데이터가 없는경우에, 오류가 발생하는 것을 방지하기 위해 empty RankingBoard를 추가한다.
                rankingBoards.add(stockRankingCacheService.get(marketType, rankingType)
                        .orElseGet(() -> emptyBoard(marketType, rankingType)));
            }
        }

        // 이후 이 RankingBoard 리스트를 StockMarketMoversPageInfo에 담아서 리턴한다.
        return new StockMarketMoversPageInfo(MARKET_TYPES, RANKING_TYPES, rankingBoards);
    }

    // initialDelay: 서버 시작 직후 첫 실행
    // fixedDelay: 첫 실행 이후 설정된 주기마다 반복 실행
    @Scheduled(
            fixedDelayString = "${finmate.stock-ranking.refresh-interval-millis:10000}",
            initialDelayString = "${finmate.stock-ranking.initial-delay-millis:100}"
    )
    // 장이 열려있으면 랭킹을 일정 주기마다 갱신하는 메서드
    public void refreshRankingsIfMarketOpen() {
        for (StockMarketType marketType : MARKET_TYPES) {
            // 현재 갱신하고자 하는 시장이 마감이 되어있는지를 확인하고, 장이 마감된 시장은 건너뛴다.
            if (!stockMarketSessionService.shouldRefreshRanking(marketType)) {
                continue;
            }

            for (StockRankingType rankingType : RANKING_TYPES) {
                try {
                    // 장이 열려있는 경우에는 랭킹을 갱신한다.
                    refreshRanking(marketType, rankingType);
                } catch (Exception e) {
                    // 장을 갱신하는데 오류가 발생하면 오류 로그 출력
                    log.warn("거래량/거래대금 TOP10 갱신에 실패했습니다. market={}, type={}",
                            marketType,
                            rankingType,
                            e);
                }
            }
        }
    }

    // 랭킹 API를 호출하여 랭킹을 갱신하고, Redis에 캐싱하는 메서드
    public StockRankingBoard refreshRanking(StockMarketType marketType, StockRankingType rankingType) {
        // 랭킹 API를 호출하여 랭킹 데이터를 받고, 이를 Redis에 캐싱한다.
        StockRankingBoard rankingBoard = fetchRankingBoard(marketType, rankingType);
        stockRankingCacheService.put(rankingBoard, cacheTtl(marketType));
        return rankingBoard;
    }

    // KisStockRankingClient를 활용해서 랭킹 API를 활용하여 랭킹 데이터를 받고, 이를 StockRankingBoard에 담아서 반환하는 메서드
    private StockRankingBoard fetchRankingBoard(StockMarketType marketType, StockRankingType rankingType) {
        if (marketType == StockMarketType.NASDAQ) {
            // 나스닥 시장의 랭킹 데이터를 받아서 StockRankingBoard를 리턴한다.
            OverseasRankingResponse response = kisStockRankingClient.fetchOverseasRanking(rankingType);
            return new StockRankingBoard(
                    marketType,
                    rankingType,
                    stockMarketSessionService.isMarketOpen(marketType),
                    LocalDateTime.now(),
                    toOverseasRankingItems(rankingType, response.output2()));
        }

        // 국내 시장의 랭킹데이터를 받아서 StockRankingBoard(랭킹 TOP 10 보드)를 리턴한다.
        DomesticRankingResponse response = kisStockRankingClient.fetchDomesticRanking(marketType, rankingType);
        return new StockRankingBoard(
                marketType,
                rankingType,
                stockMarketSessionService.isMarketOpen(marketType),
                LocalDateTime.now(),
                toDomesticRankingItems(marketType, rankingType, response.output()));
    }

    // 국내 랭킹 API 호출 결과 Record의 데이터를 서비스에서 사용할 DTO 형식에 맞게 변환하여 리턴하는 메서드
    private List<StockRankingItem> toDomesticRankingItems(StockMarketType marketType,
                                                          StockRankingType rankingType,
                                                          List<DomesticRankingItem> items) {
        if (items == null) {
            return List.of();
        }

        return items.stream()
                .limit(RANKING_LIMIT)
                .map(item -> new StockRankingItem(
                        parseRank(item.rank()),
                        findStockId(marketType, item.symbol()),
                        marketType,
                        rankingType,
                        item.symbol(),
                        item.nameKo(),
                        null,
                        parseNullableBigDecimal(item.currentPrice()),
                        parseNullableBigDecimal(item.changeAmount()),
                        parseNullableBigDecimal(item.changeRate()),
                        parseNullableLong(item.accumulatedVolume()),
                        parseNullableBigDecimal(item.accumulatedTradeAmount()),
                        "KRW"))
                .toList();
    }

    // 해외 랭킹 API 호출 결과 Record의 데이터를 서비스에서 사용할 DTO 형식에 맞게 변환하여 리턴하는 메서드
    private List<StockRankingItem> toOverseasRankingItems(StockRankingType rankingType,
                                                          List<OverseasRankingItem> items) {
        if (items == null) {
            return List.of();
        }

        return items.stream()
                .limit(RANKING_LIMIT)
                .map(item -> new StockRankingItem(
                        parseRank(item.rank()),
                        findStockId(StockMarketType.NASDAQ, item.symbol()),
                        StockMarketType.NASDAQ,
                        rankingType,
                        item.symbol(),
                        item.name(),
                        item.nameEn(),
                        parseNullableBigDecimal(item.currentPrice()),
                        parseNullableBigDecimal(item.changeAmount()),
                        parseNullableBigDecimal(item.changeRate()),
                        parseNullableLong(item.accumulatedVolume()),
                        parseNullableBigDecimal(item.accumulatedTradeAmount()),
                        "USD"))
                .toList();
    }

    // Stock 데이터를 레파지터리에서 찾아서 리턴하는 메서드
    private Long findStockId(StockMarketType marketType, String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        return stockRepository.findByMarketTypeAndSymbol(marketType, symbol.trim())
                .map(Stock::getId)
                .orElse(null);
    }

    // 빈 board 처리를 위해 빈 Board를 생성 후 리턴하는 메서드
    private StockRankingBoard emptyBoard(StockMarketType marketType, StockRankingType rankingType) {
        return new StockRankingBoard(
                marketType,
                rankingType,
                stockMarketSessionService.isMarketOpen(marketType),
                null,
                List.of());
    }

    // Redis에 저장할 캐시 유효기간 TTL 정하는 코드
    private Duration cacheTtl(StockMarketType marketType) {
        if (stockMarketSessionService.isMarketOpen(marketType)) {
            return Duration.ofSeconds(openCacheTtlSeconds); // 장이 열려있으면 openCacheTtlSeconds로 설정
        }

        return Duration.ofSeconds(closedCacheTtlSeconds); // 장이 마감되면 closedCacheTtlSeconds로 설정
    }

    // 랭킹 순위를 문자열에서 int로 바꾸는 코드
    private int parseRank(String value) {
        Long parsedValue = parseNullableLong(value);
        if (parsedValue == null) {
            return 0;
        }

        return parsedValue.intValue();
    }
}
