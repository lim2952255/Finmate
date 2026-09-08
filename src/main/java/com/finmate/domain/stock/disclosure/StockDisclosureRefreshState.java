package com.finmate.domain.stock.disclosure;

import com.finmate.domain.stock.Stock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 종목별 마지막 정상 갱신시각을 기록하여 특정 기간이 지난 종목들에 대해서만 시황/공시 정보를 API를 호출하여 받아온다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "stock_disclosure_refresh_state",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_disclosure_refresh_state_stock",
                columnNames = "stock_id"
        )
)
@Entity
public class StockDisclosureRefreshState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, unique = true, updatable = false)
    private Stock stock;

    @Column(name = "last_successful_refresh_at", nullable = false)
    private LocalDateTime lastSuccessfulRefreshAt; // 해당 종목의 시황/공시 정보를 마지막으로 불러온 시간을 기록한다.

    public static StockDisclosureRefreshState create(Stock stock, LocalDateTime refreshedAt) {
        validateRequired(stock, "시황/공시 갱신 종목은 필수입니다.");
        StockDisclosureRefreshState state = new StockDisclosureRefreshState();
        state.stock = stock;
        state.markSuccessful(refreshedAt);
        return state;
    }

    public void markSuccessful(LocalDateTime refreshedAt) {
        // 조회 결과가 0건이어도 KIS 요청과 후속 처리가 정상이면 성공으로 기록한다.
        validateRequired(refreshedAt, "시황/공시 갱신 시각은 필수입니다.");
        this.lastSuccessfulRefreshAt = refreshedAt;
    }
}
