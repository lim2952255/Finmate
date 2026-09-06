package com.finmate.evaluation.news;

import com.finmate.domain.news.dto.NewsItem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 전략마다 같은 기사를 안정적으로 연결하기 위한 평가 전용 articleId를 생성한다.
 *
 * 각 전략은 같은 기사를 서로 다른 순서로 선택할 수 있다. 객체 위치나 제목만으로 연결하지 않고
 * 기사 링크를 기반으로 결정적인 ID를 만들면 전략 결과, Judge 라벨, 지표 계산을 같은 기사 단위로
 * 연결할 수 있다. 같은 입력은 평가를 다시 실행해도 같은 ID를 만든다.
 *
 * 즉 동일한 기사를 전략마다 서로 다른 순서로 보더라도, 같은 기사임을 식별할 수 있도록 기사 링크를 기반으로 고유한 ID를 생성한다.
 */
final class EvaluationArticleIds {

    // 상태가 없는 static 유틸리티이므로 인스턴스 생성을 막는다. 따라서 생성자를 private으로 설정한다.
    private EvaluationArticleIds() {
    }

    static String from(NewsItem item) {
        // 원문 링크가 가장 안정적이다. 없으면 네이버 링크를 사용하고, 둘 다 없을 때만
        // 제목과 발행시각을 합친 값을 fallback 식별 재료로 사용한다.
        String source = firstNonBlank(
                item.originalLink(), // 원문 링크
                item.link(), // 네이버 링크
                safe(item.title()) + "|" + safe(item.publishedAt())); // 둘다 없을경우를 대비해 제목과 발행시각을 합친값을 사용한다.
        try {
			// source 문자열을 UTF-8 바이트 배열로 변환하고(UTF-8로 인코딩), 이를 SHA-256 해시를 계산한다.
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            // 전체 해시 대신 충돌 가능성이 충분히 낮은 앞 16바이트만 사람이 읽을 식별자로 사용한다.
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 articleId를 생성할 수 없습니다.", e);
        }
    }

	// 여러 String 후보들 중, null + 빈 문자열이 아닌 첫번째 값을 리턴한다.
    private static String firstNonBlank(String... values) {
        // 전달된 후보 중 null·빈 문자열이 아닌 첫 값을 선택한다.
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String safe(String value) {
        // fallback 문자열을 만들 때 null이라는 글자가 섞이지 않도록 빈 문자열로 정규화한다.
        return value == null ? "" : value.trim();
    }
}
