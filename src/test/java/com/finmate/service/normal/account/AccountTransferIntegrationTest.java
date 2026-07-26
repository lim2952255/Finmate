package com.finmate.service.normal.account;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.BankCode;
import com.finmate.domain.normal.account.dto.TransferRequest;
import com.finmate.domain.normal.transfer.DailyTransferUsage;
import com.finmate.domain.user.User;
import com.finmate.repository.normal.account.AccountRepository;
import com.finmate.repository.normal.account.transaction.AccountTransactionRepository;
import com.finmate.repository.normal.transfer.DailyTransferUsageRepository;
import com.finmate.repository.normal.transfer.TransferRepository;
import com.finmate.support.FinancialIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

// 이때 FinancialIntegrationTestSupport를 상속받기 떄문에, 스프링 컨텍스트를 로드하고, 테스트용 DB에 연결해서 테스트용 사용자와 계좌를 등록한다.
class AccountTransferIntegrationTest extends FinancialIntegrationTestSupport {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    // 스프링 컨테이너에서 실제 빈 객체를 주입받지만, 특정 기능을 수정해서 사용하기 위해 MockitoSpyBean을 사용한다
    @MockitoSpyBean
    private AccountTransactionRepository accountTransactionRepository;

    @Autowired
    private DailyTransferUsageRepository dailyTransferUsageRepository;

    @Test
    @DisplayName("ACC-002: 타인 명의 계좌를 출금 계좌로 사용한 이체는 상태 변경 없이 거부한다")
    void acc002_nonOwnerCannotUseAnotherUsersAccountAsWithdrawalSource() {
        User owner = persistUser("owner");
        User attacker = persistUser("attacker");

        Account from = persistAccount(owner, "100000-00-000001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        Account to = persistAccount(attacker, "100000-00-000002", BankCode.SHINHAN, CurrencyCode.KRW);

        // 출금계좌가 타인명의로 되어있는 경우에는 예외가 발생해야 한다. (attacker가 owner 계좌를 출금계좌로 설정함)
        assertThatThrownBy(() -> accountService.transfer(transfer(from, to, "10000"), attacker))
                .hasMessage("출금 계좌가 현재 사용자의 계좌가 아닙니다.");

        // 두 계좌의 금액에 변동이 없는지 확인한다(실제로 계좌이체가 실패했는지)
        assertBalances(from, "3000000", to, "3000000");
        // 계좌이체에 실패하였기 때문에 transferRepository와 accountTransactionRepository에도 원장이 기록되지 않는다.
        assertThat(transferRepository.count()).isZero();
        assertThat(accountTransactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("ACC-003: 일반계좌 이체는 합계 잔액을 보존하고 양쪽 원장을 기록한다")
    void acc003_transferPreservesCombinedBalanceAndWritesBothLedgers() {
        User user = persistUser("owner");
        Account from = persistAccount(user, "200000-00-000001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        Account to = persistAccount(user, "200000-00-000002", BankCode.SHINHAN, CurrencyCode.KRW);

        // from 계좌에서 to 계좌로 250000원을 이체한다.
        accountService.transfer(transfer(from, to, "250000"), user);

        Account reloadedFrom = accountRepository.findById(from.getId()).orElseThrow();
        Account reloadedTo = accountRepository.findById(to.getId()).orElseThrow();
        // from 계좌의 잔액: 3000000 - 250000 , to 계좌의 잔액: 3000000 + 250000
        assertThat(reloadedFrom.getBalance()).isEqualByComparingTo("2750000");
        assertThat(reloadedTo.getBalance()).isEqualByComparingTo("3250000");
        // 두 계좌의 잔액의 총합이 계좌이체 이전과 동일해야 한다.
        assertThat(reloadedFrom.getBalance().add(reloadedTo.getBalance())).isEqualByComparingTo("6000000");
        // TransferRepository에 저장된 Transfer의 이체 금액이 250000으로 기록되어 있는지를 검증한다.
        assertThat(transferRepository.findAll()).singleElement()
                .satisfies(transfer -> assertThat(transfer.getAmount()).isEqualByComparingTo("250000"));
        // AccountTransactionRepository에 from 계좌기준과 to 계좌 기준으로 2개의 원장이 기록되었는지를 검사한다. 그리고 두 원장 모두 이체 금액이 250000으로 기록되어 있는지를 검사한다.
        assertThat(accountTransactionRepository.findAll()).hasSize(2)
                .allSatisfy(transaction -> assertThat(transaction.getAmount()).isEqualByComparingTo("250000"));
    }

    @Test
    @DisplayName("ACC-003: 서로 다른 통화 계좌 간 이체는 상태 변경 없이 거부한다")
    void acc003_differentCurrencyTransferIsRejectedWithoutStateChange() {
        User user = persistUser("owner");
        // KRW 통화 계좌와 USD 통화 계좌 개설
        Account krw = persistAccount(user, "210000-00-000001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        Account usd = persistAccount(user, "210000-00-000002", BankCode.SHINHAN, CurrencyCode.USD);

        // 서로 다른 통화 계좌간의 이체는 예외가 발생한다.
        assertThatThrownBy(() -> accountService.transfer(transfer(krw, usd, "1"), user))
                .hasMessage("서로 다른 통화 계좌 간 이체는 환전 기능이 필요합니다.");

        // KRW 통화계좌와 USD 통화계좌의 잔액이 이체시도 전과 동일한지 검사한다.
        assertBalances(krw, "3000000", usd, "0");
        assertThat(transferRepository.count()).isZero();
    }

    @Test
    @DisplayName("ACC-004: 1회 한도와 같은 이체는 허용하고 최소 단위 초과는 거부한다")
    void acc004_exactSingleLimitIsAllowedAndMinimumUnitOverIsRejected() {
        User user = persistUser("owner");
        Account from = persistAccount(user, "300000-00-000001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        Account to = persistAccount(user, "300000-00-000002", BankCode.SHINHAN, CurrencyCode.KRW);
        // 일일 이체한도는 2000000으로, 일회 이체한도는 1000000으로 설정한다.
        accountService.updateTransferLimit(user.getId(), from.getAccountNumber(), from.getBankCode(),
                new BigDecimal("2000000"), new BigDecimal("1000000"));

        // 백만원까지는 한번에 이체가 가능하다.
        accountService.transfer(transfer(from, to, "1000000"), user);
        // 101만원은 일회 이체한도를 초과하기 때문에 예외가 발생한다.
        assertThatThrownBy(() -> accountService.transfer(transfer(from, to, "1000001"), user))
                .hasMessage("일회 이체한도를 초과했습니다.");

        // 첫번째 이체만 성공했는지 검사한다.
        assertBalances(from, "2000000", to, "4000000");
        assertThat(transferRepository.count()).isEqualTo(1);
        assertThat(accountTransactionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("ACC-005: 일일 누적 한도와 같은 이체는 허용하고 초과분은 롤백한다")
    void acc005_dailyLimitAllowsExactCumulativeAmountAndRollsBackExcess() {
        User user = persistUser("owner");
        Account from = persistAccount(user, "400000-00-000001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        Account to = persistAccount(user, "400000-00-000002", BankCode.SHINHAN, CurrencyCode.KRW);
        // 일일 이체한도를 200, 일회 이체한도를 150으로 설정한다.
        accountService.updateTransferLimit(user.getId(), from.getAccountNumber(), from.getBankCode(),
                new BigDecimal("200"), new BigDecimal("150"));

        accountService.transfer(transfer(from, to, "100"), user);
        accountService.transfer(transfer(from, to, "100"), user);

        // 누적 이체량이 200인데, 1만큼을 추가로 이체를 시도하면 일일 이체한도를 초과하기 때문에 예외가 발생한다.
        assertThatThrownBy(() -> accountService.transfer(transfer(from, to, "1"), user))
                .hasMessage("일일 이체한도를 초과했습니다.");

        DailyTransferUsage usage = dailyTransferUsageRepository
                .findByAccount_IdAndUsageDate(from.getId(), LocalDate.now(ZoneId.of("Asia/Seoul")))
                .orElseThrow();
        // DailyTransferUsage에 해당 계좌의 금일 누적 거래량이 200인지 확인한다.
        assertThat(usage.getUsedAmount()).isEqualByComparingTo("200");
        // 100원의 계좌이체 2건이 성공적으로 수행되었는지를 검사한다.
        assertBalances(from, "2999800", to, "3000200");
        assertThat(transferRepository.count()).isEqualTo(2);
        assertThat(accountTransactionRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("ACC-007: 원장 저장 실패 시 잔액·이체·원장·일일 사용량을 모두 롤백한다")
    void acc007_ledgerFailureRollsBackBalancesTransferLedgersAndDailyUsage() {
        User user = persistUser("owner");
        Account from = persistAccount(user, "500000-00-000001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        Account to = persistAccount(user, "500000-00-000002", BankCode.SHINHAN, CurrencyCode.KRW);
        // 이체 원장을 저장하려고 할때, 예외를 발생시킨다.
        doThrow(new RuntimeException("injected ledger failure"))
                .when(accountTransactionRepository).save(any());

        // 계좌이체를 시도하게 되면 원장을 저장하는데 실패하기 때문에 예외가 발생한다.
        assertThatThrownBy(() -> accountService.transfer(transfer(from, to, "100000"), user))
                .hasMessage("injected ledger failure");

        reset(accountTransactionRepository);
        // 원장을 저장하는데 실패했기 때문에 계좌이체 시도도 실패되었는지를 검사한다.
        assertBalances(from, "3000000", to, "3000000");
        assertThat(transferRepository.count()).isZero();
        assertThat(accountTransactionRepository.count()).isZero();
        assertThat(dailyTransferUsageRepository.count()).isZero();
    }

    // from 계좌에서 to 계좌로 계좌이체를 수행하는 TransferRequest 객체를 생성한다.
    private TransferRequest transfer(Account from, Account to, String amount) {
        TransferRequest request = new TransferRequest();
        request.setFromAccountNumber(from.getAccountNumber());
        request.setFromBankCode(from.getBankCode());
        request.setToAccountNumber(to.getAccountNumber());
        request.setToBankCode(to.getBankCode());
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private void assertBalances(Account first, String firstExpected, Account second, String secondExpected) {
        // 첫번째 계좌의 balance와 두번째 계좌의 balance를 검증한다.
        assertThat(accountRepository.findById(first.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(firstExpected);
        assertThat(accountRepository.findById(second.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(secondExpected);
    }
}
