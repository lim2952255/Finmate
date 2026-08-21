package com.finmate.service.market.news;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.domain.market.dto.MarketReportResponse;
import com.finmate.domain.market.news.MarketReportCache;
import com.finmate.domain.market.news.MarketReportTopic;
import com.finmate.domain.news.dto.NewsItem;
import com.finmate.infra.naver.news.NaverNewsClient;
import com.finmate.infra.naver.news.NaverNewsProperties;
import com.finmate.repository.market.news.MarketReportCacheRepository;
import com.finmate.service.news.NewsRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

// 시장/테마별 뉴스 레포트를 조회하는 서비스
@Service
@RequiredArgsConstructor
public class MarketReportService {
    private static final int DISPLAY_NEWS_LIMIT = 10; // 화면에 최대 10개의 뉴스기사를 제공한다.
    // Json 문자열을 ObjectMapper를 활용하여 List<NewsItem> 객체로 변환하기 위한 타입정보를 저장한다.
    private static final TypeReference<List<NewsItem>> NEWS_ITEMS_TYPE = new TypeReference<>() {
    };

    private final MarketReportCacheRepository cacheRepository;
    private final NaverNewsClient naverNewsClient;
    private final NewsRankingService newsRankingService; // 각 뉴스 기사별로 score를 부여하는 서비스
    private final NaverNewsProperties newsProperties;
    private final ObjectMapper objectMapper;
    // 동일한 주제에 대해서 다른 사용자와 동시에 뉴스 API를 호출하지 않도록 동시성을 제어하는 위한 Map
    private final ConcurrentHashMap<MarketReportTopic, CompletableFuture<MarketReportResponse>> inFlightRefreshes =
            new ConcurrentHashMap<>();

    // 실제 주제별로 뉴스 기사를 조회하는 메서드
    public MarketReportResponse getReport(String topicCode) {
        MarketReportTopic topic = MarketReportTopic.fromCode(topicCode);
        LocalDateTime now = LocalDateTime.now();

        // DB에서 해당 주제에 대해 캐싱된 기사를 조회한다.
        MarketReportCache cached = cacheRepository.findByTopic(topic).orElse(null);
        // 캐싱된 데이터가 유효한지를 검사한다.
        MarketReportResponse freshResponse = readFreshCache(topic, cached, now);
        // 캐싱된 데이터가 유효하다면 캐싱된 데이터를 바로 리턴한다.
        if (freshResponse != null) {
            return freshResponse;
        }

        // 만약 캐싱된 데이터가 유효하지 않다면 네이버 뉴스 API를 호출하여 이를 갱신한다.
        CompletableFuture<MarketReportResponse> refresh = new CompletableFuture<>();
        CompletableFuture<MarketReportResponse> existingRefresh = inFlightRefreshes.putIfAbsent(topic, refresh);

        // 만약 이미 해당 주제에 대해서 갱신중인 다른 클라이언트가 있다면 작업을 대기한다.
        if (existingRefresh != null) {
            return await(existingRefresh);
        }

        // 만약 해당 주제에 대해서 갱신중인 다른 클라이언트가 없다면 갱신작업을 수행한다.
        try {
            MarketReportResponse refreshedResponse = refresh(topic);
            refresh.complete(refreshedResponse);
            return refreshedResponse;
        } catch (RuntimeException e) {
            refresh.completeExceptionally(e);
            throw e;
        } finally {
            // 갱신작업을 마치고 나면 Map에서 작업정보를 제거한다.
            inFlightRefreshes.remove(topic, refresh);
        }
    }

    // 실제 네이버 뉴스 API를 호출하여 캐시를 갱신하는 메서드
    private MarketReportResponse refresh(MarketReportTopic topic) {
        // 뉴스를 갱신하기 전에, 다른클라이언트가 그사이에 갱신했을 수도 있기 때문에, 캐시가 유효한지를 다시한번 검사한다.
        LocalDateTime refreshedAt = LocalDateTime.now();
        MarketReportCache recheckedCache = cacheRepository.findByTopic(topic).orElse(null);
        MarketReportResponse recheckedResponse = readFreshCache(topic, recheckedCache, refreshedAt);
        if (recheckedResponse != null) {
            return recheckedResponse;
        }

        // 캐시가 유효하지 않다면 네이버 뉴스 API를 호출하여 뉴스기사데이터를 받고, score를 부여하여 최종적으로 10개의 뉴스기사를 선별한다.
        List<NewsItem> items = newsRankingService.rankByTitleKeywords(
                naverNewsClient.searchRelevantCandidates(topic.getQuery()),
                topic.getTitleKeywords(),
                DISPLAY_NEWS_LIMIT);
        String responseJson = serialize(items);
        // 만약 캐시가 아예 비어있었다면 엔티티를 새로 생성한다.
        MarketReportCache cacheToSave = recheckedCache == null
                ? MarketReportCache.create(
                        topic,
                        topic.getQuery(),
                        responseJson,
                        newsRankingService.policyVersion(),
                        refreshedAt)
                : recheckedCache;
        if (recheckedCache != null) {
            // 만약 캐시가 존재는 했지만 TTL이 만료된 경우였다면 데이터를 update해준다.
            cacheToSave.refresh(
                    topic.getQuery(),
                    responseJson,
                    newsRankingService.policyVersion(),
                    refreshedAt);
        }
        cacheRepository.save(cacheToSave);

        return response(topic, refreshedAt, items);
    }

    // 캐싱된 기사 데이터가 유효한지를 검사한다.
    private MarketReportResponse readFreshCache(MarketReportTopic topic,
                                                MarketReportCache cache,
                                                LocalDateTime now) {
        // 캐싱되어있는 데이터가 없거나, TTL이 만료된 경우 null을 리턴하여 캐싱된 데이터가 유효하지 않음을 나타낸다.
        if (cache == null
                || !Objects.equals(cache.getQuery(), topic.getQuery())
				// 기사 정렬 버전을 검사한다.
                || !Objects.equals(cache.getRankingPolicyVersion(), newsRankingService.policyVersion())
                || cache.getUpdatedAt() == null
                || !cache.getUpdatedAt().plus(cacheTtl()).isAfter(now)) {
            return null;
        }

        try {
            // 캐싱된 데이터가 유효하다면 이를 객체로 변환하고, 프론트에 전달하기 위한 DTO에 담아서 리턴한다.
            List<NewsItem> items = objectMapper.readValue(cache.getResponseJson(), NEWS_ITEMS_TYPE);
            return response(topic, cache.getUpdatedAt(), items);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private MarketReportResponse await(CompletableFuture<MarketReportResponse> refresh) {
        try {
            return refresh.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private Duration cacheTtl() {
        return Duration.ofHours(newsProperties.getSafeCacheTtlHours());
    }

    private String serialize(List<NewsItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("시장 리포트 검색 결과 직렬화에 실패했습니다.", e);
        }
    }

    // 주제별 기사 데이터를 프론트에 전달하기 위한 데이터를 담은 DTO를 생성하고 리턴한다.
    private MarketReportResponse response(MarketReportTopic topic,
                                          LocalDateTime updatedAt,
                                          List<NewsItem> items) {
        return new MarketReportResponse(
                topic.getCode(),
                topic.getDisplayName(),
                topic.getSymbol(),
                topic.getCategory(),
                topic.getDescription(),
                topic.getQuery(),
                updatedAt,
                topic.getTitleKeywords(),
                items);
    }
}
