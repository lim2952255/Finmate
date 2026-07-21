package com.finmate.domain.stock.industry;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 국내 종목 업종 코드정보를 저장하는 엔티티
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "domestic_stock_sector_code",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_domestic_stock_sector_code", columnNames = "code")
        }
)
@Entity
public class DomesticStockSectorCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 4, updatable = false)
    private String code;

    @Column(name = "name_ko", nullable = false, length = 80)
    private String nameKo;

    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static DomesticStockSectorCode create(String code,
                                                 String nameKo,
                                                 LocalDateTime lastSyncedAt) {
        validateRequired(code, "국내 업종코드는 필수입니다.");
        validateRequired(nameKo, "국내 업종명은 필수입니다.");
        validateRequired(lastSyncedAt, "동기화 시각은 필수입니다.");

        DomesticStockSectorCode sectorCode = new DomesticStockSectorCode();
        sectorCode.code = code;
        sectorCode.nameKo = nameKo;
        sectorCode.lastSyncedAt = lastSyncedAt;
        return sectorCode;
    }

    public void updateName(String nameKo, LocalDateTime lastSyncedAt) {
        validateRequired(nameKo, "국내 업종명은 필수입니다.");
        validateRequired(lastSyncedAt, "동기화 시각은 필수입니다.");

        this.nameKo = nameKo;
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
