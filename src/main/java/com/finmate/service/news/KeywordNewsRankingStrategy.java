package com.finmate.service.news;

import com.finmate.domain.news.dto.NewsItem;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * 뉴스 후보를 발행 날짜, 제목·요약문의 투자 키워드 점수, 발행 시각 순으로 정렬하는 기준 전략이다.
 * TF-IDF와 임베딩 전략도 먼저 이 결과를 만들고 그 순서를 유지한 채 novelty를 검사하므로,
 * 첫 번째 기사는 모든 전략에서 동일한 관련성·최신성 기준으로 선택된다.
 */
@Service
public class KeywordNewsRankingStrategy implements NewsRankingStrategy {
    // 제목의 핵심 정보를 요약문보다 더 중요하게 반영한다.
    private static final int TITLE_KEYWORD_WEIGHT = 2;
    // 제목에 없지만 요약문에서 확인되는 투자 정보를 보조 신호로 반영한다.
    private static final int DESCRIPTION_KEYWORD_WEIGHT = 1;
    // 네이버 뉴스 발행 시각을 한국 기준 날짜로 비교한다.
    private static final ZoneId NEWS_DATE_ZONE = ZoneId.of("Asia/Seoul");
    // 검색 API가 제목에 포함한 HTML 태그가 키워드 매칭을 방해하지 않도록 제거한다.
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    // 이 구현체가 제목 키워드 기반 랭킹 전략임을 나타낸다.
    @Override
    public NewsRankingType type() {
        return NewsRankingType.KEYWORD;
    }

    /**
     * 최신 날짜를 최우선으로 두고 같은 날짜 안에서는 키워드 점수와 발행 시각을 비교한다.
     * 모든 비교값이 같으면 네이버 API의 원래 순서를 유지해 결과가 불필요하게 흔들리지 않게 한다.
     */
    @Override
    public List<NewsItem> rank(List<NewsItem> candidates, // 네이버 API로부터 받은 뉴스 기사 후보들
                               List<String> keywords, // 가중치를 부여할 키워드 목록
                               int limit) { // 최종적으로 몇개를 반환할지
        return IntStream.range(0, candidates.size())
                .mapToObj(index -> {
                    NewsItem item = candidates.get(index);
                    PublishedAt publishedAt = parsePublishedAt(item.publishedAt());
                    return new RankedNews(
                            item,
                            calculateKeywordScore(item, keywords), // 제목과 요약문을 이용해 키워드 점수를 계산한다.
                            publishedAt.date(),
                            publishedAt.instant(),
                            index);
                })
                .sorted(Comparator.comparing(RankedNews::publishedDate, Comparator.reverseOrder()) // 뉴스의 발행일자를 최신순으로 정렬
                        .thenComparing(Comparator.comparingInt(RankedNews::score).reversed()) // 발행일자가 같은 경우에는 키워드점수를 내림차순으로 정렬
                        .thenComparing(RankedNews::publishedAt, Comparator.reverseOrder()) // 키워드점수까지 같다면 발행시각을 최신순으로 정렬
                        .thenComparingInt(RankedNews::originalOrder)) // 발행시각까지 같다면 네이버뉴스 조회순서대로 정렬한다.
                .limit(limit) // 정렬된 뉴스를 기준으로 top-n개를 선별한다.
                .map(RankedNews::item)
                .toList();
    }

    /**
     * 제목에서 발견한 키워드는 2점, 제목에는 없고 요약문에만 있는 키워드는 1점으로 계산한다.
     * 같은 키워드가 두 필드에 반복되어도 한 번만 점수화해 긴 요약문이나 반복 표현이 과대평가되지 않게 한다.
     */
    private int calculateKeywordScore(NewsItem item, List<String> keywords) {
        String normalizedTitle = normalizeText(item.title()); // 뉴스 제목
        String normalizedDescription = normalizeText(item.description()); // 뉴스 요약

        return keywords.stream()
                .map(this::normalizeText) // 각 키워드를 정규화한다.
                .filter(keyword -> !keyword.isBlank()) // 유효한 키워드만 남긴다.
                .distinct() // 중복된 키워드는 제거한다.
                .mapToInt(keyword -> {
					// 만약 키워드가 제목에 포함되면 +2점을 부여한다.
                    if (normalizedTitle.contains(keyword)) {
                        return TITLE_KEYWORD_WEIGHT;
                    }
					// 만약 제목에는 포함되지 않고 요약에만 포함되면 +1점을 부여한다.
                    if (normalizedDescription.contains(keyword)) {
                        return DESCRIPTION_KEYWORD_WEIGHT;
                    }
                    return 0;
                })
                .sum(); // 모든 키워드 점수를 모두 함친다.
    }

    // 네이버 검색 강조 태그를 제거하고 대소문자를 통일해 필드 간 키워드 비교 기준을 맞춘다.
    private String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return HTML_TAG_PATTERN.matcher(text)
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
    }

    // 기사 발행일자를 한국 날짜와 절대 시각으로 변환하고, 파싱할 수 없으면 정렬의 맨 뒤로 보낸다.
    private PublishedAt parsePublishedAt(String publishedAt) {
        if (publishedAt == null || publishedAt.isBlank()) {
            return PublishedAt.UNKNOWN;
        }

        try {
            Instant instant = ZonedDateTime.parse(publishedAt, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return new PublishedAt(instant.atZone(NEWS_DATE_ZONE).toLocalDate(), instant);
        } catch (DateTimeParseException e) {
            return PublishedAt.UNKNOWN;
        }
    }

    // 날짜 우선 정렬과 같은 날짜 안의 시각 정렬에 필요한 두 값을 함께 보관한다.
    private record PublishedAt(LocalDate date, Instant instant) {
        private static final PublishedAt UNKNOWN = new PublishedAt(LocalDate.MIN, Instant.MIN);
    }

    // 정렬에 필요한 키워드 점수, 날짜, 시각, 원본 순서를 기사와 함께 보관한다.
    private record RankedNews(
            NewsItem item,
            int score,
            LocalDate publishedDate,
            Instant publishedAt,
            int originalOrder
    ) {
    }
}
