package com.finmate.domain.stock.disclosure;

import com.finmate.domain.news.NewsSentiment;
import com.finmate.domain.stock.Stock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// KIS 종합 시황/공시 API에서 받은 제목 한 건과 FinBERT 감성 결과를 보관한다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "stock_disclosure",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_disclosure_external_key",
                columnNames = {"stock_id", "external_key"}
        )
)
@Entity
public class StockDisclosure {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이 시황/공시 항목과 관련된 종목이다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, updatable = false)
    private Stock stock;

    // 서버가 생성하는 중복 방지용 식별자다.
    @Column(name = "external_key", nullable = false, length = 100, updatable = false)
    private String externalKey;

    // KIS가 제공하는 내용 조회용 일련번호다.
    @Column(name = "content_serial_number", length = 40, updatable = false)
    private String contentSerialNumber;

    // KIS가 제공하는 뉴스 제공업체 코드다.
    @Column(name = "provider_code", length = 20, updatable = false)
    private String providerCode;

    // KIS가 제공하는 시황/공시 제목이다.
    @Column(nullable = false, length = 500, updatable = false)
    private String title;

    // KIS가 제공하는 자료원 이름이다.
    @Column(nullable = false, length = 100, updatable = false)
    private String source;

    // KIS가 제공하는 뉴스 대구분 코드다.
    @Column(name = "category_code", length = 30, updatable = false)
    private String categoryCode;

    // KIS가 제공하는 제목 작성시각이다.
    @Column(name = "disclosed_at", nullable = false, updatable = false)
    private LocalDateTime disclosedAt;

    // FinBERT가 제목을 분석한 호재·보통·악재 결과다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NewsSentiment sentiment;

    // FinBERT 분석을 수행한 시각이다.
    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    // 이 항목을 DB에 처음 저장한 시각이다.
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static StockDisclosure create(
            Stock stock,
            String externalKey,
            String contentSerialNumber,
            String providerCode,
            String title,
            String source,
            String categoryCode,
            LocalDateTime disclosedAt,
            NewsSentiment sentiment,
            LocalDateTime analyzedAt) {
        validateRequired(stock, "시황/공시 종목은 필수입니다.");
        validateRequired(externalKey, "시황/공시 외부 식별자는 필수입니다.");
        validateRequired(title, "시황/공시 제목은 필수입니다.");
        validateRequired(source, "시황/공시 자료원은 필수입니다.");
        validateRequired(disclosedAt, "시황/공시 작성 시각은 필수입니다.");
        validateRequired(sentiment, "시황/공시 감성 결과는 필수입니다.");
        validateRequired(analyzedAt, "시황/공시 감성 분석 시각은 필수입니다.");

        StockDisclosure disclosure = new StockDisclosure();
        disclosure.stock = stock;
        disclosure.externalKey = externalKey.trim();
        disclosure.contentSerialNumber = trimToNull(contentSerialNumber);
        disclosure.providerCode = trimToNull(providerCode);
        disclosure.title = title.trim();
        disclosure.source = source.trim();
        disclosure.categoryCode = trimToNull(categoryCode);
        disclosure.disclosedAt = disclosedAt;
        disclosure.sentiment = sentiment;
        disclosure.analyzedAt = analyzedAt;
        return disclosure;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @PrePersist
    void prePersist() {
        // 외부 데이터의 작성시각과 우리 DB에 처음 저장된 시각을 분리한다.
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
