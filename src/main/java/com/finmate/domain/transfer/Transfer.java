package com.finmate.domain.transfer;

import com.finmate.domain.account.Account;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "account_transfer")
@Entity
public class Transfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 거래내역 id를 unique하게 관리한다.
    @Column(nullable = false, unique = true, length = 36, updatable = false)
    private String transferGroupId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_account_id", nullable = false)
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_account_id", nullable = false)
    private Account toAccount;

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
}
