package com.finmate.domain.normal.accountTransaction;

import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.BankCode;
import com.finmate.domain.normal.transfer.Transfer;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// AccountTransaction은 변하면 안되는 일종의 로그이기 때문에 Setter를 지운다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_account_transaction_transfer_type",
                columnNames = {"transfer_id", "type"}
        )
})
@Entity
public class AccountTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 연관관계의 주인 설정
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private Transfer transfer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountTransactionType type;

    // 거래 금액
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // 거래 전 잔액
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceBeforeTransaction;

    // 거래 후 잔액
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfterTransaction;

    @Enumerated(EnumType.STRING)
    private BankCode counterpartyBankCode;

    private String counterpartyAccountNumber;

    private String counterpartyName;

    // 사용자 입력 메세지용
    private String description;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    //@PrePersist를 설정하 JPA 엔티티가 DB에 처음 저장되기 직전에 자동으로 호출되는 메서드를 설정할 수 있다.
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public static AccountTransaction create(Account account,
                                            Transfer transfer,
                                            AccountTransactionType type,
                                            BigDecimal amount,
                                            BigDecimal balanceBeforeTransaction,
                                            BigDecimal balanceAfterTransaction,
                                            BankCode counterpartyBankCode,
                                            String counterpartyAccountNumber,
                                            String counterpartyName,
                                            String description) {
        AccountTransaction transaction = new AccountTransaction();
        transaction.account = account;
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
