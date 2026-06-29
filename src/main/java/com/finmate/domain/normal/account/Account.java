package com.finmate.domain.normal.account;

import com.finmate.domain.user.User;
import com.finmate.global.Const;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_account_account_number",
                        columnNames = "account_number"
                )
        }
)
@Entity
public class Account {
    // PK는 비즈니스 로직상 의미가 없는 대리키를 사용해야 한다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 성능 + N+1 문제 방지를 위해 연관관계는 항상 지연로딩으로 설정하고, 꼭 필요한 경우에만 fetch join을 통해 즉시 로딩해야 한다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) // 연관관계의 주인 설정
    private User user;

    @NotBlank(message = "계좌번호는 필수입니다.")
    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    // 가상계좌 개설시 계좌 머니
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = Const.INITIAL_BALANCE;

    @Column(nullable = true, name="is_primary")
    private boolean primary;

    @NotNull(message = "은행은 필수입니다.")
    @Enumerated(EnumType.STRING) // DB에 enum 값들을 String으로 저장
    @Column(name = "bank_code", nullable = false)
    private BankCode bankCode;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyTransferLimit = Const.DAILY_TRANSFER_LIMIT;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal singleTransferLimit = Const.SINGLE_TRANSFER_LIMIT;

    // Account 엔티티는 외부에서 함부로 수정하면 안되기 때문에 setter를 제거하고 별도의 메서드를 추가
    public static Account create(String accountNumber, BankCode bankCode) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new RuntimeException("계좌번호는 필수입니다.");
        }

        if (bankCode == null) {
            throw new RuntimeException("은행은 필수입니다.");
        }

        Account account = new Account();
        account.accountNumber = accountNumber;
        account.bankCode = bankCode;
        account.balance = Const.INITIAL_BALANCE;
        return account;
    }

    public void assignUser(User user) {
        if (user == null) {
            throw new RuntimeException("사용자는 필수입니다.");
        }

        this.user = user;
    }

    public void markAsPrimary() {
        this.primary = true;
    }

    public void unmarkPrimary() {
        this.primary = false;
    }

    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("출금 금액은 0보다 커야 합니다.");
        }

        if (this.balance.compareTo(amount) < 0) {
            throw new RuntimeException("잔액이 부족합니다.");
        }

        this.balance = this.balance.subtract(amount);
    }

    public void deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("입금 금액은 0보다 커야 합니다.");
        }

        this.balance = this.balance.add(amount);
    }

    public void updateTransferLimit(BigDecimal dailyTransferLimit, BigDecimal singleTransferLimit) {
        if (dailyTransferLimit == null || singleTransferLimit == null) {
            throw new RuntimeException("이체한도를 입력해주세요.");
        }

        if (dailyTransferLimit.compareTo(BigDecimal.ZERO) <= 0 || singleTransferLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("이체한도는 1원 이상이어야 합니다.");
        }

        if (singleTransferLimit.compareTo(dailyTransferLimit) > 0) {
            throw new RuntimeException("1회 이체한도는 일일 이체한도보다 클 수 없습니다.");
        }

        this.dailyTransferLimit = dailyTransferLimit;
        this.singleTransferLimit = singleTransferLimit;
    }
}
