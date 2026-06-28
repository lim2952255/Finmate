package com.finmate.domain.transfer;

import com.finmate.domain.account.Account;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "daily_transfer_usage",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_transfer_usage_account_date",
                        columnNames = {"account_id", "usage_date"}
                )
        }
)
@Entity
public class DailyTransferUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal usedAmount = BigDecimal.ZERO;

    public static DailyTransferUsage create(Account account, LocalDate usageDate) {
        DailyTransferUsage usage = new DailyTransferUsage();
        usage.account = account;
        usage.usageDate = usageDate;
        usage.usedAmount = BigDecimal.ZERO;
        return usage;
    }

    // 일일 이체한도 검사 + 오늘 사용금액 증가
    public void use(BigDecimal amount, BigDecimal dailyTransferLimit) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("이체 금액은 0보다 커야 합니다.");
        }

        BigDecimal nextUsedAmount = this.usedAmount.add(amount);
        if (nextUsedAmount.compareTo(dailyTransferLimit) > 0) {
            throw new RuntimeException("일일 이체한도를 초과했습니다.");
        }

        this.usedAmount = nextUsedAmount;
    }
}
