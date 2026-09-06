package com.finmate.service.news;

import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


// Lucene Nori의 KoreanAnalyzer로 한국어 문장을 형태소 단위로 토큰화하는 TF-IDF 전략이다.
@Service
public class KoreanTfIdfNewsRankingStrategy extends AbstractTfIdfNewsRankingStrategy implements AutoCloseable {
    // 이미 선택된 기사 중 하나라도 이 값 이상으로 유사하면 중복 후보로 판단한다.
    // 오프라인 평가에서 품질·novelty·운영 비용의 균형이 가장 좋았던 값을 운영 기본값으로 사용한다.
    private static final double SIMILARITY_THRESHOLD = 0.20;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>"); // HTML 태그를 제거한다.
    private static final Pattern HTML_ENTITY_PATTERN = Pattern.compile("&[a-zA-Z0-9#]+;"); // HTML 엔티티를 제거한다.

    // Analyzer는 토큰화 요청마다 새로 만들지 않고 Spring 서비스 생명주기 동안 재사용한다.
    private final KoreanAnalyzer koreanAnalyzer = new KoreanAnalyzer();

    @Autowired
    public KoreanTfIdfNewsRankingStrategy(KeywordNewsRankingStrategy keywordNewsRankingStrategy) {
        this(keywordNewsRankingStrategy, SIMILARITY_THRESHOLD);
    }

	// 전략을 평가할떄 유사도 임계치를 수정해가면서 평가를 진행하기 위해 활용한다.
    public KoreanTfIdfNewsRankingStrategy(
            KeywordNewsRankingStrategy keywordNewsRankingStrategy,
            double similarityThreshold) {
        super(keywordNewsRankingStrategy, similarityThreshold);
    }

    @Override
    public NewsRankingType type() {
        return NewsRankingType.KOREAN_TF_IDF;
    }

	// 뉴스를 어떻게 토큰화하는지를 정의한다.
    @Override
    protected List<String> tokenizeText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // 네이버 뉴스 강조 태그와 HTML 엔티티가 형태소로 잘못 인식되지 않도록 먼저 제거한다.
        String normalized = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");
        normalized = HTML_ENTITY_PATTERN.matcher(normalized).replaceAll(" ");

        List<String> tokens = new ArrayList<>();

        // TokenStream은 재사용 자원을 보유할 수 있으므로 요청마다 try-with-resources로 닫는다.
		// Nori KoreanAnalyzer를 활용하여 뉴스를 한국어 형태소단위로 분석해서 토큰화한 다음, 토큰을 하나씩 순차적으로 꺼낼 수 있는 Stream 형태로 제공한다.
        try (TokenStream tokenStream = koreanAnalyzer.tokenStream("news", normalized)) {
            CharTermAttribute termAttribute = tokenStream.addAttribute(CharTermAttribute.class); // 토큰에는 시작위치나 문자열등 다양한 정보가 담겨 있는데, 이때 실제 문자열 속성을 나타내는 속성객체를 정의한다.
            tokenStream.reset(); // TokenStream을 순회하기 전에 반드시 초기화를 수행해야 한다.
            while (tokenStream.incrementToken()) {
                String token = termAttribute.toString().trim(); // 토큰 문자열을 읽고, 공백을 제거한 다음, 문자열이 비어있지 않으면 List에 추가한다.
                if (!token.isEmpty()) {
                    tokens.add(token);
                }
            }
            tokenStream.end();
            return List.copyOf(tokens);
        } catch (IOException e) {
            throw new IllegalStateException("한국어 뉴스 형태소 분석에 실패했습니다.", e);
        }
    }

    // 애플리케이션 종료 시 Nori 분석기가 보유한 재사용 자원을 정리한다.
    @Override
    @PreDestroy
    public void close() {
        koreanAnalyzer.close();
    }
}
