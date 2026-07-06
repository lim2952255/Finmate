package com.finmate.domain.normal.transfer;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.normal.account.Account;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 계좌이체 내역을 저장하고 관리하기 위한 엔티티
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "account_transfer")
@Entity
public class Transfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 거래내역 id를 unique하게 관리한다.
    // 거래내역 아이디는 UUID를 활용해서 생성한다.
    @Column(nullable = false, unique = true, length = 36, updatable = false)
    private String transferGroupId;

    // 일반 계좌간의 거래내역 뿐만 아니라, 증권 계좌로부터의 투자금 입출금 내역도 함께 저장 및 관리한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_investment_id")
    private Investment fromInvestment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_investment_id")
    private Investment toInvestment;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // 일반계좌간의 입출금 내역 저장
    public static Transfer createCompleted(String transferGroupId,
                                           Account fromAccount,
                                           Account toAccount,
                                           BigDecimal amount) {
        Transfer transfer = new Transfer();
        transfer.transferGroupId = transferGroupId;
        transfer.fromAccount = fromAccount;
        transfer.toAccount = toAccount;
        transfer.amount = amount;
        transfer.status = TransferStatus.COMPLETED;
        return transfer;
    }

    // 일반 계좌 -> 증권 계좌로 투자금 입금내역 저장
    public static Transfer createInvestmentDeposit(String transferGroupId,
                                                   Account fromAccount,
                                                   Investment toInvestment,
                                                   BigDecimal amount) {
        Transfer transfer = new Transfer();
        transfer.transferGroupId = transferGroupId;
        transfer.fromAccount = fromAccount;
        transfer.toInvestment = toInvestment;
        transfer.amount = amount;
        transfer.status = TransferStatus.COMPLETED;
        return transfer;
    }

    // 증권 계좌 -> 일반 계좌로 투자금 출금내역 저장
    public static Transfer createInvestmentWithdrawal(String transferGroupId,
                                                      Investment fromInvestment,
                                                      Account toAccount,
                                                      BigDecimal amount) {
        Transfer transfer = new Transfer();
        transfer.transferGroupId = transferGroupId;
        transfer.fromInvestment = fromInvestment;
        transfer.toAccount = toAccount;
        transfer.amount = amount;
        transfer.status = TransferStatus.COMPLETED;
        return transfer;
    }
}
