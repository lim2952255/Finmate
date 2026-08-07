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

// 국내 주식 종목별 손익계산서 이력을 저장한다.
// DomesticStockFinancialRatio가 재무비율을 저장한다면, DomesticStockIncomeStatement는 매출액, 영업이익과 같은 실제 손익 금액을 저장한다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "domestic_stock_income_statement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_domestic_stock_income_statement_period",
                columnNames = {"stock_id", "period_type", "fiscal_period"}
        )
)
@Entity
public class DomesticStockIncomeStatement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, updatable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 12, updatable = false)
    private DomesticFinancialPeriodType periodType; // 손익계산서 이력 조회 기간(연간 / 분기)

    @Column(name = "fiscal_period", nullable = false, updatable = false)
    private LocalDate fiscalPeriod; // 기준일자

    @Column(precision = 24, scale = 4)
    private BigDecimal revenue; // 매출액

    @Column(name = "cost_of_sales", precision = 24, scale = 4)
    private BigDecimal costOfSales; // 매출원가

    @Column(name = "gross_profit", precision = 24, scale = 4)
    private BigDecimal grossProfit; // 매출 총이익: 매출액 - 매출원가

    @Column(precision = 24, scale = 4)
    private BigDecimal depreciation; // 감가상각비. 기계, 건물 같은 자산의 가치 감소를 회계상 비용으로 나누어 반영하는 것. 예를들어 1억원짜리 제빵 기계를 10년 사용 예정이라면, 10년간 매년 1천만원씩의 비용으로 계산한다.

    @Column(name = "selling_admin_expenses", precision = 24, scale = 4)
    private BigDecimal sellingAdminExpenses; // 판매비와 관리비(판관비): 제품 자체를 만드는 비용은 아니지만, 회사를 운영하면서 드는 비용을 나타낸다.

    @Column(name = "operating_profit", precision = 24, scale = 4)
    private BigDecimal operatingProfit; // 영업이익: 회사의 본업으로 얼마나 벌었는지를 나타낸다. 매출총이익 - 매출원가 - 판관비

    @Column(name = "non_operating_income", precision = 24, scale = 4)
    private BigDecimal nonOperatingIncome; // 영업외수익 (부동산 가격 상승 등)

    @Column(name = "non_operating_expense", precision = 24, scale = 4)
    private BigDecimal nonOperatingExpense; // 영업외비용 (부동산 가격 하락 등)

    @Column(name = "ordinary_profit", precision = 24, scale = 4)
    private BigDecimal ordinaryProfit; // 경상이익 (분업뿐 아니라 반복적으로 발생하는 영업외순익까지 고려한 순익)

    @Column(name = "extraordinary_profit", precision = 24, scale = 4)
    private BigDecimal extraordinaryProfit; // 특별이익

    @Column(name = "extraordinary_loss", precision = 24, scale = 4)
    private BigDecimal extraordinaryLoss; // 특별손실

    @Column(name = "net_income", precision = 24, scale = 4)
    private BigDecimal netIncome; // 당기순이익 (모든 수익과 비용등을 반영한 뒤 최종적으로 남은 이익)

    // 매출액 - 매출 원가 -> 매출 총이익
    // 매출 총이익 - 판관비 -> 영업이익
    // 영업이익에 세금/환율/기타손익을 반영 -> 당기순이익

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static DomesticStockIncomeStatement create(Stock stock,
                                                       DomesticFinancialPeriodType periodType,
                                                       LocalDate fiscalPeriod,
                                                       BigDecimal revenue,
                                                       BigDecimal costOfSales,
                                                       BigDecimal grossProfit,
                                                       BigDecimal depreciation,
                                                       BigDecimal sellingAdminExpenses,
                                                       BigDecimal operatingProfit,
                                                       BigDecimal nonOperatingIncome,
                                                       BigDecimal nonOperatingExpense,
                                                       BigDecimal ordinaryProfit,
                                                       BigDecimal extraordinaryProfit,
                                                       BigDecimal extraordinaryLoss,
                                                       BigDecimal netIncome) {
        validateKey(stock, periodType, fiscalPeriod);
        DomesticStockIncomeStatement statement = new DomesticStockIncomeStatement();
        statement.stock = stock;
        statement.periodType = periodType;
        statement.fiscalPeriod = fiscalPeriod;
        statement.apply(revenue, costOfSales, grossProfit, depreciation, sellingAdminExpenses,
                operatingProfit, nonOperatingIncome, nonOperatingExpense, ordinaryProfit,
                extraordinaryProfit, extraordinaryLoss, netIncome);
        return statement;
    }

    public void update(BigDecimal revenue,
                       BigDecimal costOfSales,
                       BigDecimal grossProfit,
                       BigDecimal depreciation,
                       BigDecimal sellingAdminExpenses,
                       BigDecimal operatingProfit,
                       BigDecimal nonOperatingIncome,
                       BigDecimal nonOperatingExpense,
                       BigDecimal ordinaryProfit,
                       BigDecimal extraordinaryProfit,
                       BigDecimal extraordinaryLoss,
                       BigDecimal netIncome) {
        apply(revenue, costOfSales, grossProfit, depreciation, sellingAdminExpenses, operatingProfit,
                nonOperatingIncome, nonOperatingExpense, ordinaryProfit, extraordinaryProfit,
                extraordinaryLoss, netIncome);
        this.updatedAt = LocalDateTime.now();
    }

    private void apply(BigDecimal revenue,
                       BigDecimal costOfSales,
                       BigDecimal grossProfit,
                       BigDecimal depreciation,
                       BigDecimal sellingAdminExpenses,
                       BigDecimal operatingProfit,
                       BigDecimal nonOperatingIncome,
                       BigDecimal nonOperatingExpense,
                       BigDecimal ordinaryProfit,
                       BigDecimal extraordinaryProfit,
                       BigDecimal extraordinaryLoss,
                       BigDecimal netIncome) {
        this.revenue = revenue;
        this.costOfSales = costOfSales;
        this.grossProfit = grossProfit;
        this.depreciation = depreciation;
        this.sellingAdminExpenses = sellingAdminExpenses;
        this.operatingProfit = operatingProfit;
        this.nonOperatingIncome = nonOperatingIncome;
        this.nonOperatingExpense = nonOperatingExpense;
        this.ordinaryProfit = ordinaryProfit;
        this.extraordinaryProfit = extraordinaryProfit;
        this.extraordinaryLoss = extraordinaryLoss;
        this.netIncome = netIncome;
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
