package com.finmate.domain.stock.industry;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 해외 종목 업종 코드정보를 저장하는 엔티티
// 해외 업종 코드 정보는 거래소마다 다르기 때문에 거래소 정보도 함께 저장해야 한다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "overseas_stock_industry_code",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_overseas_stock_industry_exchange_code",
                        columnNames = {"exchange_code", "industry_code"}
                )
        }
)
@Entity
public class OverseasStockIndustryCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 거래소명
    @Column(name = "exchange_code", nullable = false, length = 10, updatable = false)
    private String exchangeCode;

    // 업종 코드명
    @Column(name = "industry_code", nullable = false, length = 20, updatable = false)
    private String industryCode;

    // 업종명
    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static OverseasStockIndustryCode create(String exchangeCode,
                                                   String industryCode,
                                                   String name,
                                                   LocalDateTime lastSyncedAt) {
        validateRequired(exchangeCode, "해외 거래소코드는 필수입니다.");
        validateRequired(industryCode, "해외 업종코드는 필수입니다.");
        validateRequired(name, "해외 업종명은 필수입니다.");
        validateRequired(lastSyncedAt, "동기화 시각은 필수입니다.");

        OverseasStockIndustryCode code = new OverseasStockIndustryCode();
        code.exchangeCode = exchangeCode;
        code.industryCode = industryCode;
        code.name = name;
        code.lastSyncedAt = lastSyncedAt;
        return code;
    }

    public void updateName(String name, LocalDateTime lastSyncedAt) {
        validateRequired(name, "해외 업종명은 필수입니다.");
        validateRequired(lastSyncedAt, "동기화 시각은 필수입니다.");

        this.name = name;
        this.lastSyncedAt = lastSyncedAt;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
