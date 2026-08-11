package com.finmate.domain.market.news;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "market_report_cache",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_market_report_cache_topic",
                columnNames = "topic"
        )
)
@Entity
public class MarketReportCache {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30, updatable = false)
    private MarketReportTopic topic; // 주제

    @Column(nullable = false, length = 300)
    private String query; // 검색어

    @Lob
    @Column(name = "response_json", nullable = false, columnDefinition = "LONGTEXT")
    private String responseJson; // 네이버 뉴스 API 응답 데이터(캐싱 용도이기 때문에 JSON 문자열을 그대로 저장한다)

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static MarketReportCache create(MarketReportTopic topic,
                                           String query,
                                           String responseJson,
                                           LocalDateTime refreshedAt) {
        validateRequired(topic, "시장 리포트 주제는 필수입니다.");
        MarketReportCache cache = new MarketReportCache();
        cache.topic = topic;
        cache.refresh(query, responseJson, refreshedAt);
        cache.createdAt = refreshedAt;
        return cache;
    }

    public void refresh(String query, String responseJson, LocalDateTime refreshedAt) {
        validateRequired(query, "시장 리포트 검색어는 필수입니다.");
        validateRequired(responseJson, "시장 리포트 검색 결과는 필수입니다.");
        validateRequired(refreshedAt, "시장 리포트 갱신 시각은 필수입니다.");
        this.query = query;
        this.responseJson = responseJson;
        this.updatedAt = refreshedAt;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
}
