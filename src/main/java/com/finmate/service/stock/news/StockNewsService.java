package com.finmate.service.stock.news;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.domain.news.dto.NewsItem;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.dto.news.StockNewsResponse;
import com.finmate.domain.stock.news.StockNewsCache;
import com.finmate.infra.naver.news.NaverNewsClient;
import com.finmate.infra.naver.news.NaverNewsProperties;
import com.finmate.repository.stock.StockRepository;
import com.finmate.repository.stock.news.StockNewsCacheRepository;
import com.finmate.service.news.FinBertNewsSentimentAnalyzer;
import com.finmate.service.news.NewsRankingStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

// 특정 종목의 뉴스를 조회하되, 캐시가 있으면 캐시를 쓰고, 없거나 만료됐으면 네이버 뉴스 API를 호출하여 다시 업데이트하는 서비스
@Service
@RequiredArgsConstructor
public class StockNewsService {
    private static final int DISPLAY_NEWS_LIMIT = 10; // 최대 10개의 뉴스를 프론트엔드에 제공
    // ObjectMapper가 캐시 JSON을 단일 뉴스 목록으로 변환할 때 필요한 제네릭 타입정보다.
    private static final TypeReference<List<NewsItem>> NEWS_ITEMS_TYPE =
            new TypeReference<>() {
    };

    private final StockRepository stockRepository;
    private final StockNewsCacheRepository cacheRepository;
    private final NaverNewsClient naverNewsClient;
    // @Primary 전략 선택기가 환경설정과 일치하는 구현체 하나를 제공한다.
    private final NewsRankingStrategy newsRankingStrategy;
    // 모델 파일은 실제 뉴스 갱신이 필요할 때만 로딩하도록 ObjectProvider로 지연 조회한다.
    private final ObjectProvider<FinBertNewsSentimentAnalyzer> sentimentAnalyzerProvider; // 뉴스 감성분석 모델을 추가하여 뉴스감성을 분석한다.
    private final NaverNewsProperties newsProperties; // Naver API 호출을 위한 프로퍼티
    private final ObjectMapper objectMapper;
    // 어떤 종목의 뉴스를 누군가가 갱신하고 있는지를 기록하는 Map -> 여러 사용자가 동시에 뉴스 API를 호출하는 것을 방지한다.
    private final ConcurrentHashMap<Long, CompletableFuture<StockNewsResponse>> inFlightRefreshes =
            new ConcurrentHashMap<>();

    // 뉴스 조회요청을 받을 때 시작되는 메서드
    public StockNewsResponse getNews(Long stockId) {

        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다."));
        // 검색 키워드 생성
        String query = buildQuery(stock);
        LocalDateTime now = LocalDateTime.now();

        // DB에 해당 종목의 뉴스기사가 저장되어 있는지를 검사한다.
        StockNewsCache cached = cacheRepository.findByStock_Id(stockId).orElse(null);
        StockNewsResponse freshResponse = readFreshCache(stock, query, cached, now);
        // freshResponse가 null이 아니라면 DB에 저장되어 있는 뉴스기사가 유효하다는 의미이므로 해당 데이터를 리턴한다.
        if (freshResponse != null) {
            return freshResponse;
        }

        // freshResponse가 null이라면 DB에 저장되어 있는 뉴스기사가 유효하지 않다는 의미이므로 네이버 뉴스 APi를 호출하여 갱신해야 한다.

        CompletableFuture<StockNewsResponse> refresh = new CompletableFuture<>();
        CompletableFuture<StockNewsResponse> existingRefresh = inFlightRefreshes.putIfAbsent(stockId, refresh);
        // 만약 이미 다른 누군가가 해당 종목에 대해서 뉴스를 갱신중이라면 대기한다.
        if (existingRefresh != null) {
            return await(existingRefresh);
        }

        // 만약 뉴스를 갱신중이던 클라이언트가 없다면 뉴스를 갱신한다.
        try {
            StockNewsResponse refreshedResponse = refresh(stock, query);
            refresh.complete(refreshedResponse);
            return refreshedResponse;
        } catch (RuntimeException e) {
            refresh.completeExceptionally(e);
            throw e;
        } finally {
            // 갱신이 완료되면 Map에서 해당 정보를 제거한다.
            inFlightRefreshes.remove(stockId, refresh);
        }
    }

    // 네이버 뉴스 API를 호출해서 뉴스를 갱신하는 메서드
    private StockNewsResponse refresh(Stock stock, String query) {
        LocalDateTime refreshedAt = LocalDateTime.now();

        // DB에 저장되어 있는 뉴스기사가 유효한지를 다시한번 검사한다.
        StockNewsCache recheckedCache = cacheRepository.findByStock_Id(stock.getId()).orElse(null);
        StockNewsResponse recheckedResponse = readFreshCache(stock, query, recheckedCache, refreshedAt);
        // 만약 뉴스기사가 유효하다면 API를 호출하지 않고 해당 뉴스기사를 리턴한다.
        if (recheckedResponse != null) {
            return recheckedResponse;
        }

        // 운영 애플리케이션은 네이버 후보를 한 번 조회하고 설정으로 선택한 전략 하나만 실행한다.
        List<NewsItem> candidates = naverNewsClient.searchRelevantCandidates(query);
        List<NewsItem> items = newsRankingStrategy.rank(
                candidates,
                StockNewsRankingPolicy.importantTitleKeywords(),
                DISPLAY_NEWS_LIMIT);
        // 랭킹 전략이 최종 선별한 최대 10건만 한 배치로 분석해 불필요한 모델 추론을 피한다.
        items = sentimentAnalyzerProvider.getObject().analyze(items);
        String responseJson = serialize(items);

        // DB에 캐싱할 엔티티를 새로 생성
        StockNewsCache cacheToSave = recheckedCache == null
                ? StockNewsCache.create(
                        stock,
                        query,
                        responseJson,
                        newsRankingStrategy.type(),
                        refreshedAt)
                : recheckedCache;
        if (recheckedCache != null) {
            // 이미 엔티티가 존재했다면, 해당 엔티티를 update
            cacheToSave.refresh(query, responseJson, newsRankingStrategy.type(), refreshedAt);
        }
        // DB에 새로운 엔티티를 저장한다.
        cacheRepository.save(cacheToSave);

        // 프론트에 전달할 StockNewsResponse DTO를 생성하여 리턴한다.
        return response(stock, query, refreshedAt, items);
    }

    private StockNewsResponse await(CompletableFuture<StockNewsResponse> refresh) {
        try {
            return refresh.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    // 현재 DB에 저장되어 있는 뉴스기사가 유효한지를 검사한다.
    private StockNewsResponse readFreshCache(Stock stock,
                                             String query,
                                             StockNewsCache cache,
                                             LocalDateTime now) {
        // DB에 뉴스기사가 저장되어 있지 않거나, TTL이 만료된 경우에는 null을 리턴하여 유효하지 않음을 나타낸다.
        if (cache == null
                || !Objects.equals(cache.getQuery(), query)
                || cache.getRankingType() != newsRankingStrategy.type()
                || cache.getUpdatedAt() == null
                || !cache.getUpdatedAt().plus(cacheTtl()).isAfter(now)) {
            return null;
        }

        try {
            List<NewsItem> items = objectMapper.readValue(
                    cache.getResponseJson(),
                    NEWS_ITEMS_TYPE);
            // 감성 필드가 없던 이전 형식의 캐시는 즉시 갱신해 화면에 라벨 없는 기사가 남지 않게 한다.
            if (items.stream().anyMatch(item -> item.sentiment() == null)) {
                return null;
            }
            return response(stock, query, cache.getUpdatedAt(), items);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // 검색은 종목명 + 시장정보로 검색한다.
    private String buildQuery(Stock stock) {
        return stock.getNameKo().trim() + " 시장정보";
    }

    private Duration cacheTtl() {
        return Duration.ofHours(newsProperties.getSafeCacheTtlHours());
    }

    private String serialize(List<NewsItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("뉴스 검색 결과 직렬화에 실패했습니다.", e);
        }
    }

    // 프론트에 전달할 StockNewsResponse에 데이터를 담아서 리턴한다.
    private StockNewsResponse response(Stock stock,
                                       String query,
                                       LocalDateTime updatedAt,
                                       List<NewsItem> items) {
        return new StockNewsResponse(
                stock.getId(),
                stock.getNameKo(),
                stock.getMarketType().name(),
                query,
                updatedAt,
                StockNewsRankingPolicy.importantTitleKeywords(),
                newsRankingStrategy.type(),
                items);
    }

}
