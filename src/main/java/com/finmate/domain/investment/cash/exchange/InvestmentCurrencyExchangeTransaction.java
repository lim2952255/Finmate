package com.finmate.domain.investment.cash.exchange;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.finmate.global.validation.NumericValidator.validateNonNegative;
import static com.finmate.global.validation.NumericValidator.validatePositive;
import static com.finmate.global.validation.RequiredValidator.validateRequired;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "investment_currency_exchange_transaction")
@Entity
public class InvestmentCurrencyExchangeTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investment_id", nullable = false)
    private Investment investment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CurrencyCode fromCurrencyCode; // 환전 전 통화 (A 통화)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CurrencyCode toCurrencyCode; // 환전 후 통화 (B 통화)

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fromAmount; // 환전 기준 금액 (A 통화)

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal toAmount; // 환전 기준 금액 (B 통화)

    @Column(nullable = false, precision = 19, scale = 10)
    private BigDecimal exchangeRate; // 환율

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fromBalanceBeforeExchange; // 환전 전 잔액 (A 통화)

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fromBalanceAfterExchange; // 환전 후 잔액 (A 통화)

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal toBalanceBeforeExchange; // 환전 전 잔액 (B 통화)

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal toBalanceAfterExchange; // 환전 후 잔액 (B 통화)

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static InvestmentCurrencyExchangeTransaction create(Investment investment,
                                                               CurrencyCode fromCurrencyCode,
                                                               BigDecimal fromAmount,
                                                               CurrencyCode toCurrencyCode,
                                                               BigDecimal toAmount,
                                                               BigDecimal exchangeRate,
                                                               BigDecimal fromBalanceBeforeExchange,
                                                               BigDecimal fromBalanceAfterExchange,
                                                               BigDecimal toBalanceBeforeExchange,
                                                               BigDecimal toBalanceAfterExchange) {
        validateRequired(investment, "증권 계좌는 필수입니다.");
        validateRequired(fromCurrencyCode, "환전 전 통화는 필수입니다.");
        validateRequired(toCurrencyCode, "환전 후 통화는 필수입니다.");
        validateDifferentCurrency(fromCurrencyCode, toCurrencyCode);
        validatePositive(exchangeRate, "환율은 필수입니다.", "환율은 0보다 커야 합니다.");
        validateCurrencyAmount(fromCurrencyCode, fromAmount, "환전 전 금액은 0보다 커야 합니다.");
        validateCurrencyAmount(toCurrencyCode, toAmount, "환전 후 금액은 0보다 커야 합니다.");
        validateCurrencyBalance(fromCurrencyCode, fromBalanceBeforeExchange, "환전 전 통화의 거래 전 잔고는 0 이상이어야 합니다.");
        validateCurrencyBalance(fromCurrencyCode, fromBalanceAfterExchange, "환전 전 통화의 거래 후 잔고는 0 이상이어야 합니다.");
        validateCurrencyBalance(toCurrencyCode, toBalanceBeforeExchange, "환전 후 통화의 거래 전 잔고는 0 이상이어야 합니다.");
        validateCurrencyBalance(toCurrencyCode, toBalanceAfterExchange, "환전 후 통화의 거래 후 잔고는 0 이상이어야 합니다.");
        validateBalanceSnapshot(
                fromBalanceBeforeExchange,
                fromAmount,
                fromBalanceAfterExchange,
                "환전 전 통화 잔고 스냅샷이 일치하지 않습니다.");
        validateBalanceSnapshot(
                toBalanceAfterExchange,
                toAmount,
                toBalanceBeforeExchange,
                "환전 후 통화 잔고 스냅샷이 일치하지 않습니다.");

        InvestmentCurrencyExchangeTransaction transaction = new InvestmentCurrencyExchangeTransaction();
        transaction.investment = investment;
        transaction.fromCurrencyCode = fromCurrencyCode;
        transaction.fromAmount = fromAmount;
        transaction.toCurrencyCode = toCurrencyCode;
        transaction.toAmount = toAmount;
        transaction.exchangeRate = exchangeRate;
        transaction.fromBalanceBeforeExchange = fromBalanceBeforeExchange;
        transaction.fromBalanceAfterExchange = fromBalanceAfterExchange;
        transaction.toBalanceBeforeExchange = toBalanceBeforeExchange;
        transaction.toBalanceAfterExchange = toBalanceAfterExchange;
        return transaction;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    private static void validateDifferentCurrency(CurrencyCode fromCurrencyCode, CurrencyCode toCurrencyCode) {
        if (fromCurrencyCode == toCurrencyCode) {
            throw new RuntimeException("같은 통화로는 환전할 수 없습니다.");
        }
    }

    private static void validateCurrencyAmount(CurrencyCode currencyCode, BigDecimal amount, String errorMessage) {
        currencyCode.validateAmountScale(amount);
        validatePositive(amount, errorMessage);
    }

    private static void validateCurrencyBalance(CurrencyCode currencyCode, BigDecimal amount, String errorMessage) {
        currencyCode.validateAmountScale(amount);
        validateNonNegative(amount, errorMessage);
    }

    private static void validateBalanceSnapshot(BigDecimal balanceBefore,
                                                BigDecimal amount,
                                                BigDecimal balanceAfter,
                                                String errorMessage) {
        if (balanceBefore.subtract(amount).compareTo(balanceAfter) != 0) {
            throw new RuntimeException(errorMessage);
        }
    }
}
