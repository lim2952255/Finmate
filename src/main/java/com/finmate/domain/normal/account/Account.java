package com.finmate.domain.normal.account;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.normal.transfer.TransferLimitPolicy;
import com.finmate.domain.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

import static com.finmate.global.validation.NumericValidator.validatePositive;
import static com.finmate.global.validation.RequiredValidator.validateRequired;

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

    // 계좌 통화
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "varchar(3) default 'KRW'")
    private CurrencyCode currencyCode;

    // 가상계좌 개설시 계좌 머니 (해당 계좌의 통화 기준)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = true, name="is_primary")
    private boolean primary;

    @NotNull(message = "은행은 필수입니다.")
    @Enumerated(EnumType.STRING) // DB에 enum 값들을 String으로 저장
    @Column(name = "bank_code", nullable = false)
    private BankCode bankCode;

    // 계좌별로 일회 이체한도와 일일 이체한도를 저장하고 관리한다.
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyTransferLimit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal singleTransferLimit;

    // Account 엔티티는 외부에서 함부로 수정하면 안되기 때문에 setter를 제거하고 별도의 메서드를 추가
    public static Account create(String accountNumber, BankCode bankCode, CurrencyCode currencyCode) {
        validateRequired(accountNumber, "계좌번호는 필수입니다.");
        validateRequired(bankCode, "은행은 필수입니다.");

        CurrencyCode accountCurrencyCode = currencyCode == null ? CurrencyCode.DEFAULT : currencyCode;

        Account account = new Account();
        account.accountNumber = accountNumber;
        account.bankCode = bankCode;
        account.currencyCode = accountCurrencyCode;
        account.balance = AccountBalancePolicy.initialBalanceOf(accountCurrencyCode);
        TransferLimitPolicy policy = TransferLimitPolicy.from(accountCurrencyCode); // 통화 종류에 따른 이체한도 설정
        account.dailyTransferLimit = policy.getDailyTransferLimit();
        account.singleTransferLimit = policy.getSingleTransferLimit();
        return account;
    }

    // 연관관계를 설정하는 용도
    public void assignUser(User user) {
        validateRequired(user, "사용자는 필수입니다.");

        this.user = user;
    }

    public void markAsPrimary() {
        this.primary = true;
    }

    public void unmarkPrimary() {
        this.primary = false;
    }

    // 계좌 출금 메서드
    public void withdraw(BigDecimal amount) {
        validateCurrencyAmountScale(amount);
        validatePositive(amount, "출금 금액은 0보다 커야 합니다.");

        if (this.balance.compareTo(amount) < 0) {
            throw new RuntimeException("잔액이 부족합니다.");
        }

        this.balance = this.balance.subtract(amount);
    }

    // 계좌 입금 메서드
    public void deposit(BigDecimal amount) {
        validateCurrencyAmountScale(amount);
        validatePositive(amount, "입금 금액은 0보다 커야 합니다.");

        this.balance = this.balance.add(amount);
    }

    // 이체한도를 update하는 메서드
    public void updateTransferLimit(BigDecimal dailyTransferLimit, BigDecimal singleTransferLimit) {
        validateRequired(dailyTransferLimit, "이체한도를 입력해주세요.");
        validateRequired(singleTransferLimit, "이체한도를 입력해주세요.");
        validateCurrencyAmountScale(dailyTransferLimit);
        validateCurrencyAmountScale(singleTransferLimit);
        validatePositive(dailyTransferLimit, "이체한도는 0보다 커야 합니다.");
        validatePositive(singleTransferLimit, "이체한도는 0보다 커야 합니다.");

        if (singleTransferLimit.compareTo(dailyTransferLimit) > 0) {
            throw new RuntimeException("1회 이체한도는 일일 이체한도보다 클 수 없습니다.");
        }

        this.dailyTransferLimit = dailyTransferLimit;
        this.singleTransferLimit = singleTransferLimit;
    }

    private void validateCurrencyAmountScale(BigDecimal amount) {
        this.currencyCode.validateAmountScale(amount);
    }
}
