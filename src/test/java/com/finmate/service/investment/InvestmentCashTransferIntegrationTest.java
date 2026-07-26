package com.finmate.service.investment;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.InvestmentCashBalance;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.investment.dto.cash.InvestmentDepositRequest;
import com.finmate.domain.investment.dto.cash.InvestmentWithdrawalRequest;
import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.BankCode;
import com.finmate.domain.normal.transfer.DailyTransferUsage;
import com.finmate.domain.user.User;
import com.finmate.repository.investment.InvestmentCashBalanceRepository;
import com.finmate.repository.investment.cash.transaction.SecuritiesCashTransactionRepository;
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
class InvestmentCashTransferIntegrationTest extends FinancialIntegrationTestSupport {

    @Autowired
    private InvestmentService investmentService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InvestmentCashBalanceRepository cashBalanceRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private AccountTransactionRepository accountTransactionRepository;

    // SecuritiesCashTransactionRepository 스프링 빈을 실제로 주입받지만, 특정 기능들은 수정해서 사용하기 위해 MockitoSpyBean으로 등록한다.
    @MockitoSpyBean
    private SecuritiesCashTransactionRepository securitiesCashTransactionRepository;

    @Autowired
    private DailyTransferUsageRepository dailyTransferUsageRepository;

    @Test
    @DisplayName("INV-002: 일반계좌에서 투자계좌로 입금하면 잔액을 이동하고 양쪽 원장을 기록한다")
    void inv002_depositMovesCashAppliesLimitsAndWritesBothLedgers() {
        User user = persistUser("owner");
        Account account = persistAccount(user, "610000-00-000001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        Investment investment = persistInvestment(user, "610000-11-000001", SecuritiesCompanyCode.KIWOOM);

        // 일반 계좌 -> 증권 계좌로 250000원 이체
        investmentService.depositToInvestment(deposit(account, investment, "250000"), user.getId());
        // 일반 계좌 잔고: 3000000 - 250000
        assertThat(reloadAccount(account).getBalance()).isEqualByComparingTo("2750000");
        // 증권계좌 잔고가 정상저그올 업데이트되었는지를 확인한다.
        assertCash(investment, CurrencyCode.KRW, "250000", "0");
        assertCash(investment, CurrencyCode.USD, "0", "0");
        // 본인 명의 일반 계좌 -> 증권계좌로의 이체는 본인명의 계좌간의 이체이기 때문에 일일 누적이체량에 포함시키지않는다.
        assertThat(todayUsage(account)).isNull();
        // 일반 계좌 -> 증권 계좌 이체시 Transfer 1건, AccountTransaction 1건, SecuritiesCashTransaction 1건이 저장된다.
        assertThat(transferRepository.count()).isEqualTo(1);
        assertThat(accountTransactionRepository.count()).isEqualTo(1);
        assertThat(securitiesCashTransactionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("INV-002/007: 타인 명의 일반계좌나 투자계좌를 사용한 입금은 상태 변경 없이 거부한다")
    void inv002_depositRejectsForeignSourceAndDestinationOwnershipWithoutMutation() {
        // 일반계좌 명의와 증권계좌 명의가 서로 다른경우
        User owner = persistUser("owner");
        User other = persistUser("other");
        Account foreignAccount = persistAccount(other, "620000-00-000001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        Investment ownerInvestment = persistInvestment(owner, "620000-11-000001", SecuritiesCompanyCode.KIWOOM);

        // 일반계좌 명의와 증권계좌 명의가 서로 다른경우, 예외가 발생하며 예수금 이체를 막아야 한다.
        assertThatThrownBy(() -> investmentService.depositToInvestment(
                deposit(foreignAccount, ownerInvestment, "1"), owner.getId()))
                .hasMessage("출금 계좌가 현재 사용자의 계좌가 아닙니다.");

        // 일반계좌 명의와 증권계좌 명의가 서로 다른경우 예수금 이체를 막고, 기존 잔고를 유지해야 한다.
        assertThat(reloadAccount(foreignAccount).getBalance()).isEqualByComparingTo("3000000");
        assertCash(ownerInvestment, CurrencyCode.KRW, "0", "0");
        assertThat(transferRepository.count()).isZero();
    }

    @Test
    @DisplayName("INV-003: 투자계좌 출금은 available만 사용하고 일반계좌 이체한도를 소비하지 않는다")
    void inv003_withdrawUsesAvailableOnlyDoesNotConsumeGeneralTransferLimitAndWritesLedgers() {
        User user = persistUser("owner");
        Account account = persistAccount(user, "630000-00-000001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        Investment investment = persistInvestment(user, "630000-11-000001", SecuritiesCompanyCode.KIWOOM);
        // 증권계좌에 500원 입금 후 그중 200원에 lock을 건다.
        depositCash(investment, CurrencyCode.KRW, "500");
        lockCash(investment, CurrencyCode.KRW, "200");

        // 증권계좌 -> 일반계좌로 예수금 300원을 출금한다.
        investmentService.withdrawFromInvestment(withdrawal(investment, account, "300"), user.getId());

        assertThat(reloadAccount(account).getBalance()).isEqualByComparingTo("3000300");
        assertCash(investment, CurrencyCode.KRW, "0", "200");
        assertThat(dailyTransferUsageRepository.count()).isZero(); // 증권계좌 이체는 이체한도가 없으며, 따라서 DailyTransferUsage도 생성하지 않는다.
        assertThat(transferRepository.count()).isEqualTo(1);
        assertThat(accountTransactionRepository.count()).isEqualTo(1);
        assertThat(securitiesCashTransactionRepository.count()).isEqualTo(1);

        // 증권계좌에서 일반계좌로의 이체는 AvailableBalance로만 가능해야 하며, LockedBalance는 사용할 수 없다.
        assertThatThrownBy(() -> investmentService.withdrawFromInvestment(
                withdrawal(investment, account, "1"), user.getId()))
                .hasMessage("예수금이 부족합니다.");
        assertCash(investment, CurrencyCode.KRW, "0", "200");
    }

    @Test
    @DisplayName("INV-003/007: 타인 명의 일반계좌로의 투자예수금 출금은 상태 변경 없이 거부한다")
    void inv003_withdrawRejectsForeignDestinationAccount() {
        // 일반계좌 명의와 증권계좌 명의가 서로 다른경우
        User owner = persistUser("owner");
        User other = persistUser("other");
        Investment investment = persistInvestment(owner, "640000-11-000001", SecuritiesCompanyCode.KIWOOM);
        Account foreignAccount = persistAccount(other, "640000-00-000001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        // 증권계좌에 100원 입금
        depositCash(investment, CurrencyCode.KRW, "100");

        // 증권계좌 -> 일반계좌로의 이체시에도 본인명의의 계좌로만 이체가 가능해야 하며, 그렇지 않으면 예외가 발생해야 한다.
        assertThatThrownBy(() -> investmentService.withdrawFromInvestment(
                withdrawal(investment, foreignAccount, "100"), owner.getId()))
                .hasMessage("입금 계좌가 현재 사용자의 계좌가 아닙니다.");

        assertThat(reloadAccount(foreignAccount).getBalance()).isEqualByComparingTo("3000000");
        assertCash(investment, CurrencyCode.KRW, "100", "0");
    }

    @Test
    @DisplayName("INV-004: 증권 원장 저장 실패 시 일반계좌·예수금·모든 원장을 롤백한다")
    void inv004_securitiesLedgerFailureRollsBackAllDepositStateAndLedgers() {
        User user = persistUser("owner");
        Account account = persistAccount(user, "650000-00-000001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        Investment investment = persistInvestment(user, "650000-11-000001", SecuritiesCompanyCode.KIWOOM);

        // SecuritiesCashTransactionRepository의 save메서드를 호출하면 예외가 발생하도록 MockitoSpyBean 객체의 기능을 수정한다.
        doThrow(new RuntimeException("injected securities ledger failure"))
                .when(securitiesCashTransactionRepository).save(any());

        // 증권계좌 -> 일반계좌로 예수금 출금시에 증권 원장저장을 시도할때 예외가 발생해야 하며, 예수금 입출금이 수행되지 않아야 한다.
        assertThatThrownBy(() -> investmentService.depositToInvestment(
                deposit(account, investment, "100000"), user.getId()))
                .hasMessage("injected securities ledger failure");
        // MockitoSpyBean의 설정을 초기화한다.
        reset(securitiesCashTransactionRepository);
        // 증권 원장 저장에 실패하는 경우 예수금 입출금 처리가 수행되면 안된다.
        assertThat(reloadAccount(account).getBalance()).isEqualByComparingTo("3000000");
        assertCash(investment, CurrencyCode.KRW, "0", "0");
        assertThat(transferRepository.count()).isZero();
        assertThat(accountTransactionRepository.count()).isZero();
        assertThat(securitiesCashTransactionRepository.count()).isZero();
        assertThat(dailyTransferUsageRepository.count()).isZero();
    }

    @Test
    @DisplayName("INV-005: 일반계좌 입금은 동일 통화 예수금만 변경한다")
    void inv005_depositChangesOnlyMatchingCurrencyBalance() {
        User user = persistUser("owner");
        Account krwAccount = persistAccount(user, "660000-00-000001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        Investment investment = persistInvestment(user, "660000-11-000001", SecuritiesCompanyCode.KIWOOM);

        // 일반계좌 -> 증권계좌로 예수금 1000원 입금
        investmentService.depositToInvestment(deposit(krwAccount, investment, "1000"), user.getId());

        // 이때 증권계좌는 KRW 통화의 예수금만 1000원으로 증가하며, USD 통화 예수금은 변하면 안된다.
        assertCash(investment, CurrencyCode.KRW, "1000", "0");
        assertCash(investment, CurrencyCode.USD, "0", "0");
    }

    @Test
    @DisplayName("INV-007: 타인 투자계좌의 출금 준비와 대표계좌 설정을 거부한다")
    void inv007_nonOwnerCannotPrepareOrMarkForeignInvestmentPrimary() {
        User owner = persistUser("owner");
        User other = persistUser("other");
        Investment foreign = persistInvestment(other, "670000-11-000001", SecuritiesCompanyCode.KIWOOM);

        assertThatThrownBy(() -> investmentService.prepareInvestmentWithdrawal(
                owner.getId(), foreign.getAccountNumber(), foreign.getSecuritiesCompanyCode()))
                .hasMessage("현재 사용자의 증권 계좌가 아닙니다.");
        assertThatThrownBy(() -> investmentService.setPrimary(foreign.getId(), owner.getId()))
                .hasMessage("현재 사용자의 증권 계좌가 아닙니다.");
    }

    private InvestmentDepositRequest deposit(Account account, Investment investment, String amount) {
        InvestmentDepositRequest request = new InvestmentDepositRequest();
        request.setFromAccountId(account.getId());
        request.setFromBankCode(account.getBankCode());
        request.setToInvestmentId(investment.getId());
        request.setToSecuritiesCompanyCode(investment.getSecuritiesCompanyCode());
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private InvestmentWithdrawalRequest withdrawal(Investment investment, Account account, String amount) {
        InvestmentWithdrawalRequest request = new InvestmentWithdrawalRequest();
        request.setFromInvestmentId(investment.getId());
        request.setFromSecuritiesCompanyCode(investment.getSecuritiesCompanyCode());
        request.setToAccountId(account.getId());
        request.setToBankCode(account.getBankCode());
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private void depositCash(Investment investment, CurrencyCode currencyCode, String amount) {
        transactionTemplate.executeWithoutResult(status -> cashBalance(investment, currencyCode)
                .deposit(new BigDecimal(amount)));
    }

    private void lockCash(Investment investment, CurrencyCode currencyCode, String amount) {
        transactionTemplate.executeWithoutResult(status -> cashBalance(investment, currencyCode)
                .lock(new BigDecimal(amount)));
    }

    private InvestmentCashBalance cashBalance(Investment investment, CurrencyCode currencyCode) {
        return cashBalanceRepository.findByInvestmentAccount_Id(investment.getId()).stream()
                .filter(balance -> balance.getCurrencyCode() == currencyCode)
                .findFirst()
                .orElseThrow();
    }

    private Account reloadAccount(Account account) {
        return accountRepository.findById(account.getId()).orElseThrow();
    }

    private DailyTransferUsage todayUsage(Account account) {
        return dailyTransferUsageRepository.findByAccount_IdAndUsageDate(
                account.getId(), LocalDate.now(ZoneId.of("Asia/Seoul"))).orElse(null);
    }

    private void assertCash(Investment investment, CurrencyCode currencyCode, String available, String locked) {
        InvestmentCashBalance balance = cashBalance(investment, currencyCode);
        assertThat(balance.getAvailableBalance()).isEqualByComparingTo(available);
        assertThat(balance.getLockedBalance()).isEqualByComparingTo(locked);
    }
}
