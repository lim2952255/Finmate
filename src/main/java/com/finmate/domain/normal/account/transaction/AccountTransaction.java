package com.finmate.domain.normal.account.transaction;

import com.finmate.domain.investment.SecuritiesCompanyCode;
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
// 특정 계좌 입장에서의 거래내역 (거래 추적 / 오류 검증을 용이하게 하기 위함)
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

    // 어떤 거래내역과 매핑되는지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private Transfer transfer;

    // 거래 타입 설정
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountTransactionType type;

    // 거래 금액
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    /*
    * 1. 거래 당시 잔액을 정확히 보여주기 위해
    * 2. 계좌 현재 잔액이 바뀌어도 과거 내역이 흔들리지 않게 하기 위해
    * 3. 나중에 오류/분쟁/디버깅이 생겼을 때 검증하기 위해
    * 4. 거래내역 화면에서 매번 과거 잔액을 재계산하지 않기 위해
    * 5. 감사 로그처럼 “그때 실제로 어떻게 바뀌었는지” 남기기 위해
    *
    * 거래전 잔액과 거래 후 잔액을 모두 저장한다.
    * */
    // 거래 전 잔액
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceBeforeTransaction;

    // 거래 후 잔액
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfterTransaction;

    @Enumerated(EnumType.STRING)
    private BankCode counterpartyBankCode;

    @Enumerated(EnumType.STRING)
    private SecuritiesCompanyCode counterpartySecuritiesCompanyCode;

    private String counterpartyAccountNumber;

    private String counterpartyName;

    // 거래 메세지 설명용
    private String description;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    //@PrePersist를 설정하 JPA 엔티티가 DB에 처음 저장되기 직전에 자동으로 호출되는 메서드를 설정할 수 있다.
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // 일반 계좌와의 거래시
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

    // 증권 계좌와의 거래 시
    public static AccountTransaction createWithSecuritiesCounterparty(Account account,
                                                                      Transfer transfer,
                                                                      AccountTransactionType type,
                                                                      BigDecimal amount,
                                                                      BigDecimal balanceBeforeTransaction,
                                                                      BigDecimal balanceAfterTransaction,
                                                                      SecuritiesCompanyCode counterpartySecuritiesCompanyCode,
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
        transaction.counterpartySecuritiesCompanyCode = counterpartySecuritiesCompanyCode;
        transaction.counterpartyAccountNumber = counterpartyAccountNumber;
        transaction.counterpartyName = counterpartyName;
        transaction.description = description;
        return transaction;
    }
}
