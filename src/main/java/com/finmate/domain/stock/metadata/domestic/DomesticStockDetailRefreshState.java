package com.finmate.domain.stock.metadata.domestic;

import com.finmate.domain.stock.Stock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 국내 주식 종목의 각 상세정보의 마지막 갱신시각을 관리한다.
// 이렇게 각 상세정보의 마지막 갱신시각을 하나의 엔티티에서 별도로 관리하는 이유는, 각 상세종목의 갱신시각을 조회하기 위해 각각을 DB에서 조회하게 되면 쿼리가 너무 많이 발생하기 때문에, 하나의 엔티티만 조회해서 갱신 필요여부를 검사하기 위함이다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "domestic_stock_detail_refresh_state",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_domestic_stock_detail_refresh_state_stock",
                columnNames = "stock_id"
        )
)
@Entity
public class DomesticStockDetailRefreshState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, unique = true, updatable = false)
    private Stock stock;

    @Column(name = "current_quote_updated_at")
    private LocalDateTime currentQuoteUpdatedAt; // 현재가 마지막 갱신시각

    @Column(name = "financial_ratio_updated_at")
    private LocalDateTime financialRatioUpdatedAt; // 재무비율 마지막 갱신시각

    @Column(name = "income_statement_updated_at")
    private LocalDateTime incomeStatementUpdatedAt; // 손익계산서 마지막 갱신시각

    @Column(name = "balance_sheet_updated_at")
    private LocalDateTime balanceSheetUpdatedAt; // 재무상태표 마지막 갱신시각

    @Column(name = "investor_trade_updated_at")
    private LocalDateTime investorTradeUpdatedAt; // 투자자 수급 마지막 갱신시각

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static DomesticStockDetailRefreshState create(Stock stock) {
        validateRequired(stock, "종목 정보는 필수입니다.");
        DomesticStockDetailRefreshState state = new DomesticStockDetailRefreshState();
        state.stock = stock;
        return state;
    }

    public void markCurrentQuoteUpdated(LocalDateTime refreshedAt) {
        this.currentQuoteUpdatedAt = requireRefreshTime(refreshedAt);
    }

    public void markFinancialRatioUpdated(LocalDateTime refreshedAt) {
        this.financialRatioUpdatedAt = requireRefreshTime(refreshedAt);
    }

    public void markIncomeStatementUpdated(LocalDateTime refreshedAt) {
        this.incomeStatementUpdatedAt = requireRefreshTime(refreshedAt);
    }

    public void markBalanceSheetUpdated(LocalDateTime refreshedAt) {
        this.balanceSheetUpdatedAt = requireRefreshTime(refreshedAt);
    }

    public void markInvestorTradeUpdated(LocalDateTime refreshedAt) {
        this.investorTradeUpdatedAt = requireRefreshTime(refreshedAt);
    }

    private LocalDateTime requireRefreshTime(LocalDateTime refreshedAt) {
        validateRequired(refreshedAt, "갱신 시각은 필수입니다.");
        return refreshedAt;
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
