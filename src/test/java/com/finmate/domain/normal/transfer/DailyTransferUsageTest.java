package com.finmate.domain.normal.transfer;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.BankCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyTransferUsageTest {

    @Test
    @DisplayName("ACC-005: 누적 사용액은 일일 이체한도와 정확히 같을 수 있다")
    void allowsCumulativeUsageEqualToDailyLimit() {
        DailyTransferUsage usage = usageOn(LocalDate.of(2026, 7, 23));

        // 300만원 사용 (일일 이체한도: 500, 누적 사용액: 300)
        usage.use(new BigDecimal("3000000"), new BigDecimal("5000000"));
        // 200만원 사용 (일일 이체한도: 500, 누적 사용액: 500)
        usage.use(new BigDecimal("2000000"), new BigDecimal("5000000"));

        assertThat(usage.getUsedAmount()).isEqualByComparingTo("5000000");
    }

    @Test
    @DisplayName("ACC-005: 누적 사용액이 일일 한도를 초과하면 기존 사용액을 보존한다")
    void rejectsUsageBeyondDailyLimitWithoutChangingUsedAmount() {
        DailyTransferUsage usage = usageOn(LocalDate.of(2026, 7, 23));
        usage.use(new BigDecimal("4999999"), new BigDecimal("5000000"));

        // 누적사용액이 일일한도를 초과하면 예외가 발생해야 한다.
        assertThatThrownBy(() -> usage.use(new BigDecimal("2"), new BigDecimal("5000000")))
                .hasMessage("일일 이체한도를 초과했습니다.");
        assertThat(usage.getUsedAmount()).isEqualByComparingTo("4999999");
    }

    @Test
    @DisplayName("ACC-005: 0원 사용은 누적액을 변경하지 않고 거부한다")
    void rejectsZeroUsageWithoutChangingUsedAmount() {
        DailyTransferUsage usage = usageOn(LocalDate.of(2026, 7, 23));

        // 0원을 이체하려고 하는 경우에는 예외가 발생한다.
        assertThatThrownBy(() -> usage.use(BigDecimal.ZERO, new BigDecimal("5000000")))
                .hasMessage("이체 금액은 0보다 커야 합니다.");
        assertThat(usage.getUsedAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ACC-005: 사용량은 계좌와 업무 날짜를 보존한다")
    void preservesAccountAndUsageDate() {
        Account account = Account.create("100-001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        LocalDate usageDate = LocalDate.of(2026, 7, 23);

        DailyTransferUsage usage = DailyTransferUsage.create(account, usageDate);

        // DailyTransferUsage 엔티티에 연관관계 설정과 날짜가 제대로 등록되는지를 검사한다.
        assertThat(usage.getAccount()).isSameAs(account);
        assertThat(usage.getUsageDate()).isEqualTo(usageDate);
    }

    // 계좌를 개설하고, DailyTransferUsage를 생성후 연관관계를 설정한다.
    private static DailyTransferUsage usageOn(LocalDate date) {
        Account account = Account.create("100-001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        return DailyTransferUsage.create(account, date);
    }
}
