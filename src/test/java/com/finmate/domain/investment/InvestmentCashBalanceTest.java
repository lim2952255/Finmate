package com.finmate.domain.investment;

import com.finmate.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// InvestmentCashBalance 객체의 예수금 잠금을 테스트한다.
// 예수금 잠금과 잠금해제가 적절히 동작하는지를 검증한다.

// 부족한 테스트: 실제 정산액이 locked와 available의 합계를 초과하면 정산을 거부하고 기존 예수금 상태를 유지해야 한다.
class InvestmentCashBalanceTest {

    @Test
    @DisplayName("INV-006: 예수금 잠금은 available과 locked 사이에서 total을 보존한다")
    void lockingPreservesTotalBalance() {
        InvestmentCashBalance balance = usdBalanceWith("100.00"); // 계좌 개설 및 InvestmentCachBalance에 100.00를 입금한다.

        balance.lock(new BigDecimal("40.25")); // 40.25만큼을 lock을 건다.

        // InvestmentCashBalance의 AvailableBalance와 LockedBalance, TotalBalance가 제대로 계산되고 제대로 잠금이 되고 있는지를 검증한다.
        assertThat(balance.getAvailableBalance()).isEqualByComparingTo("59.75");
        assertThat(balance.getLockedBalance()).isEqualByComparingTo("40.25");
        assertThat(balance.getTotalBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("INV-006: 잠금 해제는 locked와 available 사이에서 total을 보존한다")
    void releasingLockPreservesTotalBalance() {
        InvestmentCashBalance balance = usdBalanceWith("100.00"); // 계좌 개설 및 InvestmentCachBalance에 100.00를 입금한다.

        balance.lock(new BigDecimal("40.25")); // Amount에 lock을 건다.

        balance.releaseLocked(new BigDecimal("10.25")); // LockedBalance에 일부를 lock을 해제한다.

        // 예수금 잠금 / 잠금해제시에 예수금 잠금과 이용가능한 예수금이 적절하 계산되는지를 검증한다.
        assertThat(balance.getAvailableBalance()).isEqualByComparingTo("70.00");
        assertThat(balance.getLockedBalance()).isEqualByComparingTo("30.00");
        assertThat(balance.getTotalBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("INV-006: available과 정확히 같은 금액을 잠글 수 있다")
    void locksEntireAvailableBalance() {
        InvestmentCashBalance balance = usdBalanceWith("100.00");

        balance.lock(new BigDecimal("100.00"));

        assertThat(balance.getAvailableBalance()).isEqualByComparingTo("0");
        assertThat(balance.getLockedBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("INV-006: available을 초과한 잠금은 잔액을 변경하지 않는다")
    void rejectsLockBeyondAvailableWithoutChangingBalance() {
        InvestmentCashBalance balance = usdBalanceWith("100.00");

        // available을 초과한 금액을 잠금 시도 시 예외가 발생해야 한다.
        assertThatThrownBy(() -> balance.lock(new BigDecimal("100.01")))
                .hasMessage("주문 가능 예수금이 부족합니다.");
        // 예외 발생 이후에는 실제 금액변동이 없어야한다.
        assertThat(balance.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(balance.getLockedBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ORD-007: 실제 정산액이 예약액보다 작으면 차액을 available로 돌려준다")
    void refundsDifferenceWhenSettlementIsLessThanLockedAmount() {
        InvestmentCashBalance balance = usdBalanceWith("100.00");
        balance.lock(new BigDecimal("60.00"));

        // 전체 예약금액중, 실제 정산액이 더 적은경우, 남은 차액을 available로 돌려줘야 한다.
        balance.settleBuyFromLocked(new BigDecimal("60.00"), new BigDecimal("55.25"));

        assertThat(balance.getAvailableBalance()).isEqualByComparingTo("44.75");
        assertThat(balance.getLockedBalance()).isEqualByComparingTo("0"); // 주문이 체결되고 나면 더이상 예약이 잡혀있지 않기 때문에 lockedBalance가 0이어야 한다.
        assertThat(balance.getTotalBalance()).isEqualByComparingTo("44.75");
    }

    @Test
    @DisplayName("ORD-007: 실제 정산액이 예약액과 같으면 locked만 소비한다")
    void consumesLockedAmountWhenSettlementEqualsReservation() {
        InvestmentCashBalance balance = usdBalanceWith("100.00");
        balance.lock(new BigDecimal("60.00"));

        // 전체 예약금액과 실제 정산액이 같은 경우, locked만 0으로 update한다.
        balance.settleBuyFromLocked(new BigDecimal("60.00"), new BigDecimal("60.00"));

        assertThat(balance.getAvailableBalance()).isEqualByComparingTo("40.00");
        assertThat(balance.getLockedBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ORD-007: 실제 정산액이 예약액보다 크면 추가 금액을 available에서 소비한다")
    void consumesAdditionalAvailableWhenSettlementExceedsReservation() {
        InvestmentCashBalance balance = usdBalanceWith("100.00");
        balance.lock(new BigDecimal("60.00"));

        // 실제 정산액이 예약액보다 크면 추가 금액을 available에서 차감해야 한다. (-5.50)
        balance.settleBuyFromLocked(new BigDecimal("60.00"), new BigDecimal("65.50"));

        assertThat(balance.getAvailableBalance()).isEqualByComparingTo("34.50");
        assertThat(balance.getLockedBalance()).isEqualByComparingTo("0");
        assertThat(balance.getTotalBalance()).isEqualByComparingTo("34.50");
    }

    @Test
    @DisplayName("INV-005: 한 통화 예수금 변경은 다른 통화 예수금을 변경하지 않는다")
    void changingOneCurrencyDoesNotChangeAnotherCurrency() {
        Investment investment = newInvestment();
        InvestmentCashBalance krw = investment.getCashBalances().stream()
                .filter(balance -> balance.getCurrencyCode() == CurrencyCode.KRW)
                .findFirst().orElseThrow();
        InvestmentCashBalance usd = investment.getCashBalances().stream()
                .filter(balance -> balance.getCurrencyCode() == CurrencyCode.USD)
                .findFirst().orElseThrow();

        krw.deposit(new BigDecimal("1000")); // 원화 통화에 1000원 입금

        // 원화는 원화통화끼리만 계산되며, 달러는 달러통화끼리만 계산되어야 한다.
        assertThat(krw.getAvailableBalance()).isEqualByComparingTo("1000");
        assertThat(usd.getAvailableBalance()).isEqualByComparingTo("0");
    }

    private static InvestmentCashBalance usdBalanceWith(String amount) {
        Investment investment = newInvestment(); // 새로운 증권계좌 개설
        InvestmentCashBalance balance = investment.getCashBalances().stream() // investmnet의 USD 통화를 리턴한다.
                .filter(candidate -> candidate.getCurrencyCode() == CurrencyCode.USD)
                .findFirst().orElseThrow();
        balance.deposit(new BigDecimal(amount)); // InvestmentCashBalance에 amount를 입금한다.
        return balance;
    }

    // 새로운 증권 계좌를 개설한다.
    private static Investment newInvestment() {
        return Investment.create(new User(), "200-001",
                SecuritiesCompanyCode.KOREA_INVESTMENT);
    }
}
