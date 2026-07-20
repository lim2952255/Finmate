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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

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

    // 각 증권 계좌별로 통화 종류에 맞는 금액을 얻기 위한 연관관계 설정
    @OneToMany(mappedBy = "investmentAccount", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("currencyCode ASC")
    private List<InvestmentCashBalance> cashBalances = new ArrayList<>();

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Investment create(User user,
                                    String accountNumber,
                                    SecuritiesCompanyCode securitiesCompanyCode) {
        validateRequired(user, "사용자는 필수입니다.");
        validateRequired(accountNumber, "증권 계좌번호는 필수입니다.");
        validateRequired(securitiesCompanyCode, "증권사는 필수입니다.");

        Investment investment = new Investment();
        investment.user = user;
        investment.accountNumber = accountNumber;
        investment.securitiesCompanyCode = securitiesCompanyCode;
        investment.initializeCashBalances(); // 모든 통화의 예수금 잔고를 등록
        investment.primary = false;
        return investment;
    }

    public void markAsPrimary() {
        this.primary = true;
    }

    public void unmarkPrimary() {
        this.primary = false;
    }

    // 증권 계좌에 특정 통화의 예수금 잔고를 하나 추가하는 메서드
    public InvestmentCashBalance addCashBalance(CurrencyCode currencyCode) {
        validateRequired(currencyCode, "통화는 필수입니다.");
        // 이미 해당 증권 계좌에 해당 예수금 통화가 등록되어 있으면 오류 출력
        boolean exists = this.cashBalances.stream()
                .anyMatch(cashBalance -> cashBalance.getCurrencyCode() == currencyCode);
        if (exists) {
            throw new RuntimeException("이미 등록된 예수금 통화입니다.");
        }

        // 예수금 통화 생성 후 연관관계 설정
        InvestmentCashBalance cashBalance = InvestmentCashBalance.create(this, currencyCode);
        this.cashBalances.add(cashBalance);
        return cashBalance;
    }

    // 현재 증권 계좌의 KRW 통화의 총 금액을 출력
    public BigDecimal getDepositBalance() {
        return getAvailableCashBalance(CurrencyCode.KRW);
    }


    public BigDecimal getAvailableCashBalance(CurrencyCode currencyCode) {
        return this.cashBalances.stream()
                .filter(cashBalance -> cashBalance.getCurrencyCode() == currencyCode)
                .findFirst()
                .map(InvestmentCashBalance::getAvailableBalance)
                .orElse(BigDecimal.ZERO);
    }

    // 모든 통화 종류에 따라 통화 예수금 잔고를 추가하는 메서드
    private void initializeCashBalances() {
        Arrays.stream(CurrencyCode.values())
                .forEach(this::addCashBalance);
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
