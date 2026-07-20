package com.finmate.domain.investment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import static com.finmate.global.validation.NumericValidator.validatePositive;
import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 각 증권 계좌별로 여러 종류의 통화를 관리하는 엔티티
// 각 증권 계좌가 보유중인 통화별 잔고를 관리하며, 매수 주문에 의해 예수금에 lock을 걸거나 해제하는 로직을 담당한다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_investment_cash_balance_account_currency",
                        columnNames = {"investment_account_id", "currency_code"}
                )
        }
)
public class InvestmentCashBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investment_account_id", nullable = false)
    private Investment investmentAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", nullable = false)
    private CurrencyCode currencyCode;

    // 실제로 주식을 구매하거나 투자금을 이체할 수 있는 금액
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    // 주식 매수 예약으로 인해 예약금으로 묶여 있는 금액
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal lockedBalance = BigDecimal.ZERO;

    public static InvestmentCashBalance create(Investment investmentAccount, CurrencyCode currencyCode) {
        validateRequired(investmentAccount, "증권 계좌는 필수입니다.");
        validateRequired(currencyCode, "통화는 필수입니다.");

        InvestmentCashBalance cashBalance = new InvestmentCashBalance();
        cashBalance.investmentAccount = investmentAccount;
        cashBalance.currencyCode = currencyCode;
        cashBalance.availableBalance = BigDecimal.ZERO;
        cashBalance.lockedBalance = BigDecimal.ZERO;
        return cashBalance;
    }

    // 일반 계좌 -> 증권 계좌로 예수금 입금
    public void deposit(BigDecimal amount) {
        this.currencyCode.validateAmountScale(amount);
        validatePositive(amount, "입금 금액은 0보다 커야 합니다.");
        this.availableBalance = this.availableBalance.add(amount);
    }

    // 증권 계좌 -> 일반 계좌로 투자금 출금
    public void withdraw(BigDecimal amount) {
        this.currencyCode.validateAmountScale(amount);
        validatePositive(amount, "출금 금액은 0보다 커야 합니다.");

        if (this.availableBalance.compareTo(amount) < 0) {
            throw new RuntimeException("예수금이 부족합니다.");
        }

        this.availableBalance = this.availableBalance.subtract(amount);
    }

    // 주식 매수 주문/예약을 위해 예수금에 lock을 거는 메서드.
    public void lock(BigDecimal amount) {
        this.currencyCode.validateAmountScale(amount);
        validatePositive(amount, "잠금 금액은 0보다 커야 합니다.");

        if (this.availableBalance.compareTo(amount) < 0) {
            throw new RuntimeException("주문 가능 예수금이 부족합니다.");
        }

        this.availableBalance = this.availableBalance.subtract(amount);
        this.lockedBalance = this.lockedBalance.add(amount);
    }

    // 매수 주문/예약이 취소되거나 만료된 경우, lock이 걸려있는 예수금의 lock을 해제하는 메서드
    public void releaseLocked(BigDecimal amount) {
        this.currencyCode.validateAmountScale(amount);
        validatePositive(amount, "잠금 해제 금액은 0보다 커야 합니다.");

        if (this.lockedBalance.compareTo(amount) < 0) {
            throw new RuntimeException("잠금 예수금이 부족합니다.");
        }

        this.lockedBalance = this.lockedBalance.subtract(amount);
        this.availableBalance = this.availableBalance.add(amount);
    }

    // 매수 체결 시 잠금 예수금에서 실제 정산금액을 차감하고 남은 금액은 사용 가능 예수금으로 돌려놓는다.(매수 부분 체결시)
    public void settleBuyFromLocked(BigDecimal lockedAmount, BigDecimal settlementAmount) {
        this.currencyCode.validateAmountScale(lockedAmount);
        this.currencyCode.validateAmountScale(settlementAmount);
        validatePositive(lockedAmount, "잠금 금액은 0보다 커야 합니다.");
        validatePositive(settlementAmount, "정산 금액은 0보다 커야 합니다.");

        if (this.lockedBalance.compareTo(lockedAmount) < 0) {
            throw new RuntimeException("잠금 예수금이 부족합니다.");
        }
        // lockedAmount: 지정가, settlementAmount: 실제 거래체결가
        this.lockedBalance = this.lockedBalance.subtract(lockedAmount);

        if (lockedAmount.compareTo(settlementAmount) > 0) {
            this.availableBalance = this.availableBalance.add(lockedAmount.subtract(settlementAmount));
            return;
        }

        BigDecimal additionalAmount = settlementAmount.subtract(lockedAmount);
        if (additionalAmount.compareTo(BigDecimal.ZERO) > 0) {
            if (this.availableBalance.compareTo(additionalAmount) < 0) {
                throw new RuntimeException("추가 정산 예수금이 부족합니다.");
            }

            this.availableBalance = this.availableBalance.subtract(additionalAmount);
        }
    }

    // 총 금액은 availableBalance와 lockedBalance를 더해서 계산한다.
    public BigDecimal getTotalBalance() {
        return this.availableBalance.add(this.lockedBalance);
    }

}
