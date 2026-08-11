package com.finmate.service.news;

import com.finmate.domain.news.dto.NewsItem;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

// 뉴스 기사 후보들중, 키워드 / 최신순 으로 정렬해서 상위 N개를 뽑는 서비스
@Service
public class NewsRankingService {
    // HTML 태그 제거용 Pattern
    // Pattern은 특정 정규표현식을 미리 저장해두는 객체이다.
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");


    public List<NewsItem> rankByTitleKeywords(List<NewsItem> candidates, // 네이버 API로부터 받은 뉴스 기사 후보들
                                              List<String> keywords, // 가중치를 부여할 키워드 목록
                                              int limit) { // 최종적으로 몇개를 반환할지
        return IntStream.range(0, candidates.size())
                .mapToObj(index -> {
                    NewsItem item = candidates.get(index); // 각 뉴스후보들을 뽑는다.
                    return new RankedNews(
                            item,
                            calculateTitleScore(item.title(), keywords), // 각 뉴스 후보들 별로 키워드 기반 score를 메긴다.
                            parsePublishedAt(item.publishedAt()),
                            index); // 원래 순서를 함께 저장
                })
                // 이후 1. score가 높은 순서 2. score가 같다면 최신 기사 우선 3. 기본 조회 순서대로 기사 우선순위를 부여하여 정렬하고, Top 10개를 뽑는다.
                .sorted(Comparator.comparingInt(RankedNews::score).reversed()
                        .thenComparing(RankedNews::publishedAt, Comparator.reverseOrder())
                        .thenComparingInt(RankedNews::originalOrder))
                .limit(limit)
                .map(RankedNews::item)
                .toList();
    }

    // 키워드 기반으로 각 뉴스 기사별로 점수를 산정한다.
    private int calculateTitleScore(String title, List<String> keywords) {
        // 기사 제목을 기반으로 점수를 산정하기 때문에, 제목이 비어있으면 문제가 된다. 따라서 기사 제목이 비어있지는 않은지를 검사한다.
        if (title == null || title.isBlank()) {
            return 0;
        }

        // PATTERN을 사용하여 HTML 태그를 제거한 기사 제목을 얻는다.
        String normalizedTitle = HTML_TAG_PATTERN.matcher(title)
                .replaceAll("")
                .toLowerCase(Locale.ROOT);

        // 기사 제목에 키워드가 포함된 횟수를 기반으로 score를 산정한다.
        return (int) keywords.stream()
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .filter(normalizedTitle::contains)
                .count();
    }

    // 기사 발행일자를 Instant로 변환한다.
    // Instant는 자바에서 특정 시점을 UTC 기준의 절대 시간으로 표현하는 객체이다.
    private Instant parsePublishedAt(String publishedAt) {
        if (publishedAt == null || publishedAt.isBlank()) {
            return Instant.MIN;
        }

        try {
            return ZonedDateTime.parse(publishedAt, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            return Instant.MIN;
        }
    }

    // 각 후보 뉴스 기사 별로 score를 부여한다.
    private record RankedNews(
            NewsItem item,
            int score,
            Instant publishedAt,
            int originalOrder // 원래 순서
    ) {
    }
}
