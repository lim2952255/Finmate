package com.finmate.domain.stock.metadata.domestic;

import com.finmate.domain.stock.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 국내 종목 주식의 재무상태표를 저장하는 엔티티
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "domestic_stock_balance_sheet",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_domestic_stock_balance_sheet_period",
                columnNames = {"stock_id", "period_type", "fiscal_period"}
        )
)
@Entity
public class DomesticStockBalanceSheet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, updatable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 12, updatable = false)
    private DomesticFinancialPeriodType periodType; // 재무상태표를 저장할 기간(연간 / 분기)

    @Column(name = "fiscal_period", nullable = false, updatable = false)
    private LocalDate fiscalPeriod;

    @Column(name = "current_assets", precision = 24, scale = 4)
    private BigDecimal currentAssets; // 유동자산: 1년안에 현금화되거나 사용될 가능성이 높은 자산

    @Column(name = "fixed_assets", precision = 24, scale = 4)
    private BigDecimal fixedAssets; // 고정자산: 공장/설비/기계/부동산 등의 자산

    @Column(name = "total_assets", precision = 24, scale = 4)
    private BigDecimal totalAssets; // 회사가 가지고 있는 총자산. 자산은 회사 자본 + 부채를 의미한다.


    @Column(name = "current_liabilities", precision = 24, scale = 4)
    private BigDecimal currentLiabilities; // 유동부채: 회사가 1년안에 갚아야하는 부채(단기차입금,미지급금 등)

    @Column(name = "fixed_liabilities", precision = 24, scale = 4)
    private BigDecimal fixedLiabilities; // 고정부채: 회사가 오랫동안 갚아도 되는 부채(장기차입금, 장기 회사채 등)

    @Column(name = "total_liabilities", precision = 24, scale = 4)
    private BigDecimal totalLiabilities; // 회사가 갚아야 하는 총 부채

    // 자기자본에는 자본금 + 이익잉여금 + 자본잉여 + 기타 자본항목등이 포함된다.

    @Column(precision = 24, scale = 4)
    private BigDecimal capital; // 자본금 (주식을 발행해서 회사에 들어온 기본 출자금)

    @Column(name = "capital_surplus", precision = 24, scale = 4)
    private BigDecimal capitalSurplus; // 자본 잉여금: 회사가 영업을 통해 벌어들인 수익이 아니라, 주주와의 자본거래등에서 발생한 잉여금

    @Column(name = "retained_earnings", precision = 24, scale = 4)
    private BigDecimal retainedEarnings; // 이익 잉여금: 회사가 과거에 벌었던 이익 중 배당 등으로 다 내보내지 않고 회사 내부에 누적해 둔 금액

    @Column(name = "total_equity", precision = 24, scale = 4)
    private BigDecimal totalEquity; // 총자본 또는 자기자본(회사 자산 중 빚을 제외하고 주주에게 귀속되는 몫)

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static DomesticStockBalanceSheet create(Stock stock,
                                                    DomesticFinancialPeriodType periodType,
                                                    LocalDate fiscalPeriod,
                                                    BigDecimal currentAssets,
                                                    BigDecimal fixedAssets,
                                                    BigDecimal totalAssets,
                                                    BigDecimal currentLiabilities,
                                                    BigDecimal fixedLiabilities,
                                                    BigDecimal totalLiabilities,
                                                    BigDecimal capital,
                                                    BigDecimal capitalSurplus,
                                                    BigDecimal retainedEarnings,
                                                    BigDecimal totalEquity) {
        validateKey(stock, periodType, fiscalPeriod);
        DomesticStockBalanceSheet balanceSheet = new DomesticStockBalanceSheet();
        balanceSheet.stock = stock;
        balanceSheet.periodType = periodType;
        balanceSheet.fiscalPeriod = fiscalPeriod;
        balanceSheet.apply(currentAssets, fixedAssets, totalAssets, currentLiabilities, fixedLiabilities,
                totalLiabilities, capital, capitalSurplus, retainedEarnings, totalEquity);
        return balanceSheet;
    }

    public void update(BigDecimal currentAssets,
                       BigDecimal fixedAssets,
                       BigDecimal totalAssets,
                       BigDecimal currentLiabilities,
                       BigDecimal fixedLiabilities,
                       BigDecimal totalLiabilities,
                       BigDecimal capital,
                       BigDecimal capitalSurplus,
                       BigDecimal retainedEarnings,
                       BigDecimal totalEquity) {
        apply(currentAssets, fixedAssets, totalAssets, currentLiabilities, fixedLiabilities,
                totalLiabilities, capital, capitalSurplus, retainedEarnings, totalEquity);
        this.updatedAt = LocalDateTime.now();
    }

    private void apply(BigDecimal currentAssets,
                       BigDecimal fixedAssets,
                       BigDecimal totalAssets,
                       BigDecimal currentLiabilities,
                       BigDecimal fixedLiabilities,
                       BigDecimal totalLiabilities,
                       BigDecimal capital,
                       BigDecimal capitalSurplus,
                       BigDecimal retainedEarnings,
                       BigDecimal totalEquity) {
        this.currentAssets = currentAssets;
        this.fixedAssets = fixedAssets;
        this.totalAssets = totalAssets;
        this.currentLiabilities = currentLiabilities;
        this.fixedLiabilities = fixedLiabilities;
        this.totalLiabilities = totalLiabilities;
        this.capital = capital;
        this.capitalSurplus = capitalSurplus;
        this.retainedEarnings = retainedEarnings;
        this.totalEquity = totalEquity;
    }

    private static void validateKey(Stock stock,
                                    DomesticFinancialPeriodType periodType,
                                    LocalDate fiscalPeriod) {
        validateRequired(stock, "종목 정보는 필수입니다.");
        validateRequired(periodType, "재무 기간 유형은 필수입니다.");
        validateRequired(fiscalPeriod, "결산 기간은 필수입니다.");
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
