package com.finmate.infra.naver.news;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.domain.news.dto.NewsItem;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

// 네이버 뉴스 API를 호출하여 총 40개의 기사 후보를 조회하는 클라이언트
@Component
public class NaverNewsClient {
    private static final int NEWS_CANDIDATE_LIMIT = 40; // 후보 기사 수
    private static final String NEWS_PATH = "/search/v1/news";

    private final NaverNewsProperties properties; // 네이버 뉴스 API 호출을 위해 필요한 설정을 담은 프로퍼티
    private final ObjectMapper objectMapper; // Json 문자열 <-> 자바 객체 변환
    private final HttpClient httpClient; // http 통신용 client

    public NaverNewsClient(NaverNewsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public List<NewsItem> searchRelevantCandidates(String query) {
        properties.validateCredentials(); // client 키와 secret 키를 검사한다.

        // query를 기반으로 Http 요청을 생성ㅎ나다.
        HttpRequest request = HttpRequest.newBuilder()
                .uri(buildUri(query))
                .timeout(Duration.ofSeconds(10))
                .header("X-NCP-APIGW-API-KEY-ID", properties.getClientId().trim())
                .header("X-NCP-APIGW-API-KEY", properties.getClientSecret().trim())
                .GET()
                .build();

        try {
            // 네이버 뉴스 API를 호출하여 응답 결과를 받는다.
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("NAVER 뉴스 API 응답이 실패했습니다. status=" + response.statusCode());
            }

            // 응답 데이터에서 실제 본문을 꺼내 객체로 변환한다.
            SearchResponse responseBody = objectMapper.readValue(response.body(), SearchResponse.class);
            if (responseBody.items() == null) {
                return List.of();
            }

            // 40개의 후보 뉴스 기사만 NewsItem DTO로 변환하여 리턴한다.
            return responseBody.items().stream()
                    .limit(NEWS_CANDIDATE_LIMIT)
                    .map(item -> new NewsItem(
                            item.title(),
                            item.originallink(),
                            item.link(),
                            item.description(),
                            item.pubDate()))
                    .toList();
        } catch (InterruptedException e) {
            // 인터럽트 예외를 잡고난다음에는 다시 인터럽트를 발생시켜줘야 한다.
            Thread.currentThread().interrupt();
            throw new RuntimeException("NAVER 뉴스 API 호출이 중단되었습니다.", e);
        } catch (Exception e) {
            throw new RuntimeException("NAVER 뉴스 API 호출에 실패했습니다.", e);
        }
    }

    // API 호출을 위해 필요한 파라미터를 설정한다.
    private URI buildUri(String query) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return URI.create(properties.getNormalizedBaseUrl()
                + NEWS_PATH
                + "?query=" + encodedQuery
                + "&display=" + NEWS_CANDIDATE_LIMIT
                + "&start=1&sort=sim&format=json");
    }

    // API 응답 결과를 담는 레코드
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(List<SearchItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchItem(
            String title,
            String originallink,
            String link,
            String description,
            String pubDate
    ) {
    }
}
