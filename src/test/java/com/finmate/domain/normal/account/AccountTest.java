package com.finmate.domain.normal.account;

import com.finmate.domain.investment.CurrencyCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 일반계좌 입출금 테스트

// 일일 이체한도와 일회 이체한도가 같다는 테스트는 굳이 필요 없을듯?
class AccountTest {

    @Test
    @DisplayName("ACC-003: 잔액과 정확히 같은 금액을 출금하면 잔액이 0이 된다")
    void withdrawsEntireBalance() {
        // 처음 계좌 개설시에 KRW 계좌의 경우 기본적으로 3000000원을 제공한다..
        Account account = Account.create("100-001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);

        account.withdraw(new BigDecimal("3000000"));

        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ACC-003: 잔액을 초과한 출금은 잔액을 변경하지 않는다")
    void rejectsWithdrawalBeyondBalanceWithoutChangingBalance() {
        Account account = Account.create("100-001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);

        // 잔액을 초과한 출금의 경우 예외가 발생해야 하며, 잔액이 변경되면 안된다.
        assertThatThrownBy(() -> account.withdraw(new BigDecimal("3000001")))
                .hasMessage("잔액이 부족합니다.");
        assertThat(account.getBalance()).isEqualByComparingTo("3000000");
    }

    @Test
    @DisplayName("ACC-003: 0원 입금은 잔액을 변경하지 않고 거부한다")
    void rejectsZeroDepositWithoutChangingBalance() {
        Account account = Account.create("100-001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);

        // 입금금액의 최소단위 검증
        assertThatThrownBy(() -> account.deposit(BigDecimal.ZERO))
                .hasMessage("입금 금액은 0보다 커야 합니다.");
        assertThat(account.getBalance()).isEqualByComparingTo("3000000");
    }

    @Test
    @DisplayName("ACC-003: 계좌 통화의 입력 scale을 초과한 입금을 거부한다")
    void rejectsDepositBeyondCurrencyScale() {
        Account account = Account.create("100-001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);

        // 계좌 통화(CurrencyCode)의 통화규칙을 어긴 금액에 대해서는 예외가 발생해야 하며, 잔액이 변경되면 안된다.
        assertThatThrownBy(() -> account.deposit(new BigDecimal("1.01")))
                .isInstanceOf(RuntimeException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("3000000");
    }

    @Test
    @DisplayName("ACC-004: 1회 이체한도는 일일 이체한도와 같을 수 있다")
    void allowsSingleTransferLimitEqualToDailyLimit() {
        Account account = Account.create("100-001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);

        account.updateTransferLimit(new BigDecimal("2000000"), new BigDecimal("2000000"));

        // 이체한도 검증
        assertThat(account.getDailyTransferLimit()).isEqualByComparingTo("2000000");
        assertThat(account.getSingleTransferLimit()).isEqualByComparingTo("2000000");
    }

    @Test
    @DisplayName("ACC-004: 1회 이체한도가 일일 이체한도보다 크면 기존 한도를 보존한다")
    void rejectsSingleTransferLimitBeyondDailyLimitWithoutChangingLimits() {
        Account account = Account.create("100-001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);

        // 1회 이체한도가 일일 이체한도보다 커지면 예외가 발생해야 한다.
        assertThatThrownBy(() -> account.updateTransferLimit(
                new BigDecimal("2000000"), new BigDecimal("2000001")))
                .hasMessage("1회 이체한도는 일일 이체한도보다 클 수 없습니다.");
        assertThat(account.getDailyTransferLimit()).isEqualByComparingTo("5000000"); // 일일 이체한도 기본값
        assertThat(account.getSingleTransferLimit()).isEqualByComparingTo("1000000"); // 일회 이체한도 기본값
    }
}
