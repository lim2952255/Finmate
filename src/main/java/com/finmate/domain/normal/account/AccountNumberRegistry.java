package com.finmate.domain.normal.account;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 고유한 계좌번호 생성용 엔티티(테이블)
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "account_number_registry",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_account_number", columnNames = "account_number")
        }
)
@Getter
public class AccountNumberRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, updatable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, updatable = false)
    private AccountType accountType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AccountNumberRegistry create(String accountNumber, AccountType accountType) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new RuntimeException("계좌번호는 필수입니다.");
        }

        if (accountType == null) {
            throw new RuntimeException("계좌 타입은 필수입니다.");
        }

        AccountNumberRegistry registry = new AccountNumberRegistry();
        registry.accountNumber = accountNumber;
        registry.accountType = accountType;
        return registry;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
