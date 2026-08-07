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

// 국내 주식종목의 재무비율 이력을 저장한다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "domestic_stock_financial_ratio",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_domestic_stock_financial_ratio_period",
                columnNames = {"stock_id", "period_type", "fiscal_period"}
        )
)
@Entity
public class DomesticStockFinancialRatio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, updatable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 12, updatable = false)
    private DomesticFinancialPeriodType periodType; // 재무 비율 조회 기간(연간 or 분기)

    @Column(name = "fiscal_period", nullable = false, updatable = false)
    private LocalDate fiscalPeriod; // 기준 일자

    @Column(name = "sales_growth_rate", precision = 19, scale = 6)
    private BigDecimal salesGrowthRate; // 매출액 증가율

    @Column(name = "operating_profit_growth_rate", precision = 19, scale = 6)
    private BigDecimal operatingProfitGrowthRate; // 영업이익 증가율

    @Column(name = "net_income_growth_rate", precision = 19, scale = 6)
    private BigDecimal netIncomeGrowthRate; // 당기순이익 증가율

    @Column(precision = 19, scale = 6)
    private BigDecimal roe; // ROE: (당기순이익 / 자기자본) * 100

    @Column(precision = 19, scale = 6)
    private BigDecimal eps; // 주당 순이익 EPS: 당기순이익 / 주식 수

    @Column(precision = 19, scale = 6)
    private BigDecimal sps; // 주당 매출액 SPS: 매출액 / 주식 수

    @Column(precision = 19, scale = 6)
    private BigDecimal bps; // 주당 순자산 BPS: 자기자본 / 주식 수

    @Column(name = "reserve_rate", precision = 19, scale = 6)
    private BigDecimal reserveRate; // 유보율 (기업이 벌어들인 이익 중 회사 내부에 얼마나 쌓아놓고 있는지를 보는 지표)

    @Column(name = "debt_rate", precision = 19, scale = 6)
    private BigDecimal debtRate; // 부채 비율: (부채 / 자기자본) * 100

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static DomesticStockFinancialRatio create(Stock stock,
                                                      DomesticFinancialPeriodType periodType,
                                                      LocalDate fiscalPeriod,
                                                      BigDecimal salesGrowthRate,
                                                      BigDecimal operatingProfitGrowthRate,
                                                      BigDecimal netIncomeGrowthRate,
                                                      BigDecimal roe,
                                                      BigDecimal eps,
                                                      BigDecimal sps,
                                                      BigDecimal bps,
                                                      BigDecimal reserveRate,
                                                      BigDecimal debtRate) {
        validateKey(stock, periodType, fiscalPeriod);
        DomesticStockFinancialRatio ratio = new DomesticStockFinancialRatio();
        ratio.stock = stock;
        ratio.periodType = periodType;
        ratio.fiscalPeriod = fiscalPeriod;
        ratio.apply(salesGrowthRate, operatingProfitGrowthRate, netIncomeGrowthRate, roe, eps, sps, bps,
                reserveRate, debtRate);
        return ratio;
    }

    public void update(BigDecimal salesGrowthRate,
                       BigDecimal operatingProfitGrowthRate,
                       BigDecimal netIncomeGrowthRate,
                       BigDecimal roe,
                       BigDecimal eps,
                       BigDecimal sps,
                       BigDecimal bps,
                       BigDecimal reserveRate,
                       BigDecimal debtRate) {
        apply(salesGrowthRate, operatingProfitGrowthRate, netIncomeGrowthRate, roe, eps, sps, bps,
                reserveRate, debtRate);
        this.updatedAt = LocalDateTime.now();
    }

    private void apply(BigDecimal salesGrowthRate,
                       BigDecimal operatingProfitGrowthRate,
                       BigDecimal netIncomeGrowthRate,
                       BigDecimal roe,
                       BigDecimal eps,
                       BigDecimal sps,
                       BigDecimal bps,
                       BigDecimal reserveRate,
                       BigDecimal debtRate) {
        this.salesGrowthRate = salesGrowthRate;
        this.operatingProfitGrowthRate = operatingProfitGrowthRate;
        this.netIncomeGrowthRate = netIncomeGrowthRate;
        this.roe = roe;
        this.eps = eps;
        this.sps = sps;
        this.bps = bps;
        this.reserveRate = reserveRate;
        this.debtRate = debtRate;
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
