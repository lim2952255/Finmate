package com.finmate.domain.stock.news;

import com.finmate.domain.stock.Stock;
import com.finmate.service.news.NewsRankingType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 각 종목별로 DB에 캐싱할 기사 엔티티
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "stock_news_cache",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_news_cache_stock",
                columnNames = "stock_id"
        )
)
@Entity
public class StockNewsCache {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, unique = true, updatable = false)
    private Stock stock;

    @Column(nullable = false, length = 300)
    private String query;

    @Lob
    @Column(name = "response_json", nullable = false, columnDefinition = "LONGTEXT")
    private String responseJson; // 선택된 단일 전략의 Top 10 뉴스 목록을 JSON으로 저장한다.

    // 전략을 변경했을 때 이전 전략으로 만든 캐시가 자동으로 무효화되도록 타입을 함께 저장한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "ranking_type", length = 30)
    private NewsRankingType rankingType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static StockNewsCache create(Stock stock,
                                        String query,
                                        String responseJson,
                                        NewsRankingType rankingType,
                                        LocalDateTime refreshedAt) {
        validateRequired(stock, "종목 정보는 필수입니다.");
        StockNewsCache cache = new StockNewsCache();
        cache.stock = stock;
        cache.refresh(query, responseJson, rankingType, refreshedAt);
        cache.createdAt = refreshedAt;
        return cache;
    }

    // 종목 기사정보를 update한다.
    public void refresh(String query,
                        String responseJson,
                        NewsRankingType rankingType,
                        LocalDateTime refreshedAt) {
        validateRequired(query, "뉴스 검색어는 필수입니다.");
        validateRequired(responseJson, "뉴스 검색 결과는 필수입니다.");
        validateRequired(rankingType, "뉴스 랭킹 전략은 필수입니다.");
        validateRequired(refreshedAt, "뉴스 갱신 시각은 필수입니다.");
        this.query = query;
        this.responseJson = responseJson;
        this.rankingType = rankingType;
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
