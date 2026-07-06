package com.finmate.domain.investment;

import com.finmate.domain.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 증권 계좌도 외부에서 함부로 수정하면 안되기 때문에 Setter를 설정하지 않는다.
/*
* 관심종목 = 사용자별
* 보유종목 = 증권계좌별
* 포트폴리오 = 기본적으로 증권계좌별, 화면에서는 사용자별 통합 조회 가능
* */
// 증권 계좌 엔티티
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "investment_account",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_investment_account_number",
                        columnNames = "account_number"
                )
        }
)
@Entity
public class Investment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank(message = "증권 계좌번호는 필수입니다.")
    @Column(name = "account_number", nullable = false, updatable = false)
    private String accountNumber;

    @NotNull(message = "증권사는 필수입니다.")
    @Enumerated(EnumType.STRING)
    @Column(name = "securities_company_code", nullable = false, updatable = false)
    private SecuritiesCompanyCode securitiesCompanyCode;

    // 주식 주문에 사용할 수 있는 현금성 잔고
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal depositBalance = BigDecimal.ZERO;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Investment create(User user,
                                    String accountNumber,
                                    SecuritiesCompanyCode securitiesCompanyCode) {
        if (user == null) {
            throw new RuntimeException("사용자는 필수입니다.");
        }

        if (accountNumber == null || accountNumber.isBlank()) {
            throw new RuntimeException("증권 계좌번호는 필수입니다.");
        }

        if (securitiesCompanyCode == null) {
            throw new RuntimeException("증권사는 필수입니다.");
        }

        Investment investment = new Investment();
        investment.user = user;
        investment.accountNumber = accountNumber;
        investment.securitiesCompanyCode = securitiesCompanyCode;
        investment.depositBalance = BigDecimal.ZERO;
        investment.primary = false;
        return investment;
    }

    public void markAsPrimary() {
        this.primary = true;
    }

    public void unmarkPrimary() {
        this.primary = false;
    }

    public void depositCash(BigDecimal amount) {
        if (amount == null) {
            throw new RuntimeException("입금 금액은 필수입니다.");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("입금 금액은 0보다 커야 합니다.");
        }

        this.depositBalance = this.depositBalance.add(amount);
    }

    public void withdrawCash(BigDecimal amount) {
        if (amount == null) {
            throw new RuntimeException("출금 금액은 필수입니다.");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("출금 금액은 0보다 커야 합니다.");
        }

        if (this.depositBalance.compareTo(amount) < 0) {
            throw new RuntimeException("예수금이 부족합니다.");
        }

        this.depositBalance = this.depositBalance.subtract(amount);
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
