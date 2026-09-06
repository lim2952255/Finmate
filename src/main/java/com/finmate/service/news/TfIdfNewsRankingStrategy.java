package com.finmate.service.news;

import com.finmate.domain.news.dto.NewsItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 한글·영문·숫자가 아닌 문자를 경계로 단순 토큰화하고 TF-IDF 유사도를 계산하는 기준 전략이다.
 * 형태소 분석을 하지 않아 실행 비용이 작으며 한국어 형태소 전략의 비교 기준으로 사용한다.
 */
@Service
public class TfIdfNewsRankingStrategy extends AbstractTfIdfNewsRankingStrategy {
    // 이미 선택된 기사 중 하나라도 이 값 이상으로 유사하면 중복 후보로 판단한다.
    private static final double SIMILARITY_THRESHOLD = 0.30;
    // 뉴스 API가 제목과 요약문에 포함한 HTML 태그와 엔티티를 토큰에서 제외한다.
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern HTML_ENTITY_PATTERN = Pattern.compile("&[a-zA-Z0-9#]+;");
    // 한글·영문·숫자를 제외한 문자를 토큰 구분자로 사용한다. 즉 문자나 숫자가 아닌 모든 문자를 토큰 구분자로 활용한다.
    private static final Pattern TOKEN_SEPARATOR_PATTERN = Pattern.compile("[^\\p{L}\\p{N}]+");

    @Autowired
    public TfIdfNewsRankingStrategy(KeywordNewsRankingStrategy keywordNewsRankingStrategy) {
        this(keywordNewsRankingStrategy, SIMILARITY_THRESHOLD);
    }

	// 전략을 평가할떄 유사도 임계치를 수정해가면서 평가를 진행하기 위해 활용한다.
    public TfIdfNewsRankingStrategy(
            KeywordNewsRankingStrategy keywordNewsRankingStrategy,
            double similarityThreshold) {
        super(keywordNewsRankingStrategy, similarityThreshold);
    }

    // 이 구현체가 TF-IDF 기반 중복 제거 전략임을 나타낸다.
    @Override
    public NewsRankingType type() {
        return NewsRankingType.TF_IDF;
    }

	// 뉴스를 어떻게 토큰화하는지를 정의한다.
    @Override
    protected List<String> tokenizeText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // 검색 결과의 강조 태그와 HTML 엔티티를 제거하고 영문 대소문자를 통일한다.
        String normalized = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");
        normalized = HTML_ENTITY_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = normalized.toLowerCase(Locale.ROOT).trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        // 문장부호와 공백을 경계로 토큰화하고, 빈 토큰은 필터링한다.
        return TOKEN_SEPARATOR_PATTERN.splitAsStream(normalized)
                .filter(token -> !token.isBlank())
                .toList();
    }
}
