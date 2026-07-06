package com.finmate.domain.investment.cash.transaction;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.normal.account.BankCode;
import com.finmate.domain.normal.transfer.Transfer;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 일반 계좌 - 증권 계좌간의 투자금 입출금 내역을 저장하기 위한 엔티티
// 이때 증권 계좌간에는 계좌이체가 불가능하므로 상대방은 항상 본인 명의의 일반 계좌가 된다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "securities_cash_transaction",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_securities_cash_transaction_transfer_type",
                        columnNames = {"transfer_id", "type"}
                )
        }
)
@Entity
public class SecuritiesCashTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investment_id", nullable = false)
    private Investment investment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SecuritiesCashTransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceBeforeTransaction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfterTransaction;

    @Enumerated(EnumType.STRING)
    private BankCode counterpartyBankCode;

    private String counterpartyAccountNumber;

    private String counterpartyName;

    private String description;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public static SecuritiesCashTransaction create(Investment investment,
                                                   Transfer transfer,
                                                   SecuritiesCashTransactionType type,
                                                   BigDecimal amount,
                                                   BigDecimal balanceBeforeTransaction,
                                                   BigDecimal balanceAfterTransaction,
                                                   BankCode counterpartyBankCode,
                                                   String counterpartyAccountNumber,
                                                   String counterpartyName,
                                                   String description) {
        SecuritiesCashTransaction transaction = new SecuritiesCashTransaction();
        transaction.investment = investment;
        transaction.transfer = transfer;
        transaction.type = type;
        transaction.amount = amount;
        transaction.balanceBeforeTransaction = balanceBeforeTransaction;
        transaction.balanceAfterTransaction = balanceAfterTransaction;
        transaction.counterpartyBankCode = counterpartyBankCode;
        transaction.counterpartyAccountNumber = counterpartyAccountNumber;
        transaction.counterpartyName = counterpartyName;
        transaction.description = description;
        return transaction;
    }
}
