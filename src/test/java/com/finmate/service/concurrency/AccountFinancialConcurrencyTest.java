package com.finmate.service.concurrency;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.InvestmentCashBalance;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.investment.dto.cash.InvestmentDepositRequest;
import com.finmate.domain.investment.dto.cash.InvestmentWithdrawalRequest;
import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.BankCode;
import com.finmate.domain.normal.account.dto.TransferRequest;
import com.finmate.domain.normal.transfer.DailyTransferUsage;
import com.finmate.domain.user.User;
import com.finmate.repository.investment.InvestmentCashBalanceRepository;
import com.finmate.repository.investment.cash.transaction.SecuritiesCashTransactionRepository;
import com.finmate.repository.normal.account.AccountRepository;
import com.finmate.repository.normal.account.transaction.AccountTransactionRepository;
import com.finmate.repository.normal.transfer.DailyTransferUsageRepository;
import com.finmate.repository.normal.transfer.TransferRepository;
import com.finmate.service.investment.InvestmentService;
import com.finmate.service.normal.account.AccountService;
import com.finmate.support.FinancialIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

// 일반계좌 <-> 증권계좌간의 예수금 입출금은 본인 명의의 계좌들로만 가능해야 하는데 해당 업무규칙을 검증하는 테스트가 부족함

// 계좌이체시에 계좌번호에 오름차순으로 lock을 걸어 데드락을 방지하는지를 테스트한다.
// 이때 FinancialIntegrationTestSupport를 상속받기 떄문에, 스프링 컨텍스트를 로드하고, 테스트용 DB에 연결해서 테스트용 사용자와 계좌를 등록한다.
class AccountFinancialConcurrencyTest extends FinancialIntegrationTestSupport {

    private static final long FUTURE_TIMEOUT_SECONDS = 10L;

    @Autowired
    private AccountService accountService;

    @Autowired
    private InvestmentService investmentService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InvestmentCashBalanceRepository cashBalanceRepository;

    @Autowired
    private DailyTransferUsageRepository dailyTransferUsageRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private AccountTransactionRepository accountTransactionRepository;

    @Autowired
    private SecuritiesCashTransactionRepository securitiesCashTransactionRepository;

    // ExecutorService는 두개의 작업을 서로다른 스레드에서 동시에 실행하기 위해서 사용된다. (여러스레드를 동시에 실행하 동시성 문제가 발생하지 않는지를 테스트한다)
    // ExecutionService는 스레드를 직접 생성하고 관리하는 대신, 여러 작업 스레드 풀에 제출해서 실행하도록 도와주는 자바의 스레드 관리자이다.

    private ExecutorService executor;

    // 각 테스트가 끝날때마다 executor를 종료해서 자원을 정리한다.
    @AfterEach
    void stopExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    // A->B 계좌이체와 B->A 계좌이체를 동시에 실행할때 데드락이 발생하지 않는지를 테스트한다.
    @Test
    @DisplayName("ACC-006: 반대 방향 동시 이체는 ID 오름차순 락으로 deadlock 없이 보존식을 만족한다")
    void acc006_opposingTransfersCompleteWithoutDeadlockAndPreserveTotal() throws Exception {
        User user = persistUser("opposing-transfer-user"); // User 생성 후 DB에 저장
        // 첫번째 계좌와 두번째 계좌 생성
        Account first = persistAccount(user, "810000-00-000001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        Account second = persistAccount(user, "810000-00-000002", BankCode.SHINHAN, CurrencyCode.KRW);
        BigDecimal totalBefore = balance(first).add(balance(second)); // 총금액 계산 (300만원 + 300만원)

        // CycleBarrier는 두 스레드가 동시에 작업을 수행하도록 대기시키는 역할을한다.
        // 즉 스레드 A가 준비되었는데 스레드 B가 아직 준비가 안되었다면, 대기하다가 스레드 B까지 도착해서 두개의 스레드가 모두 준비되면 그때 작업을 동시에 시작한다.
        CyclicBarrier start = new CyclicBarrier(2);

        // 양방향 계좌이체를 수행할때에도 lock을 계좌 id의 오름차순 순서대로 잡기 때문에, 데드락이 발생하지 않는다.
        List<Future<WorkerResult>> futures = submitTogether(
                start, // cycleBarrier 전달
                // A 계좌 -> B 계좌로 100000원을 계좌이체하는 스레드작업 (ThreadRunnable)
                () -> accountService.transfer(transfer(first, second, "100000"), user),
                // B 계좌 -> A 계좌로 70000원을 계좌이체하는 스레드작업 (ThreadRunnable)
                () -> accountService.transfer(transfer(second, first, "70000"), user));

        // 두 작업이 모두 성공했는지를 검사
        assertThat(await(futures)).allMatch(WorkerResult::succeeded);
        // 첫번째 계좌의 잔고 = 3000000 - 100000 + 70000
        BigDecimal firstAfter = balance(first);
        // 두번째 계좌의 잔고 = 3000000 + 100000 - 70000
        BigDecimal secondAfter = balance(second);
        assertThat(firstAfter).isEqualByComparingTo("2970000");
        assertThat(secondAfter).isEqualByComparingTo("3030000");
        // 총 잔고가 계좌이체 전과 동일한지 검사한다.
        assertThat(firstAfter.add(secondAfter)).isEqualByComparingTo(totalBefore);
        // 잔고는 음수가 나올 수 없기 때문에 음수인지를 검사한다.
        assertThat(firstAfter).isNotNegative();
        assertThat(secondAfter).isNotNegative();
        // transferRepository에는 1. A->B 계좌이체, 2. B->A 계좌이체 두건이 저장된다.
        assertThat(transferRepository.count()).isEqualTo(2);
        // accountTransactionRepository에는 1. A -> B 계좌이체에 대해서 A 계좌 기준, B계좌 기준이 저장되며, 2. B -> A 계좌이체에 대해서도 두 기준으로 저장되어 총 4건이 저장된다.
        assertThat(accountTransactionRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("ACC-005: 일일 한도 경계의 동시 출금은 정확히 하나만 commit한다")
    void acc005_simultaneousDailyLimitUsageAllowsOnlyOneTransfer() throws Exception {
        User user = persistUser("daily-limit-user");
        Account from = persistAccount(user, "820000-00-000001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        Account firstTarget = persistAccount(user, "820000-00-000002", BankCode.SHINHAN, CurrencyCode.KRW);
        Account secondTarget = persistAccount(user, "820000-00-000003", BankCode.HANA, CurrencyCode.KRW);

        // 일일 이체한도를 150, 일회 이체한도를 100으로 변경한다.
        accountService.updateTransferLimit(
                user.getId(), from.getAccountNumber(), from.getBankCode(),
                new BigDecimal("150"), new BigDecimal("100"));
        // 두 스레드 작업을 동시에 실행하기 위한 CycleBarrier를 설정
        CyclicBarrier start = new CyclicBarrier(2);

        // 계좌이체가 한 계좌에서 동시에 발생할때 동시성문제로 인해 일일 이체한도를 초과할 수 있는지를 검사한다
        // 이때 await와 submitTogether를 통해 두 작업이 동시에 실행해서 끝날때까지 기다리고, 결과를 WorkerResult로 반환한다.
        List<WorkerResult> results = await(submitTogether(
                start,
                // 첫번째 작업은 기준 계좌 -> A계좌로 100 이체
                () -> accountService.transfer(transfer(from, firstTarget, "100"), user),
                // 두번째 작업은 기준 계좌 -> B계좌로 100 이체
                () -> accountService.transfer(transfer(from, secondTarget, "100"), user)));

        assertThat(results).filteredOn(WorkerResult::succeeded).hasSize(1); // 성공한 작업이 하나인지를 검사한다.
        // 실행에 실패한 작업이 한가지이고, 오류메세지가 "일일 이체한도를 초과했습니다"인지를 검사한다.
        assertThat(results).filteredOn(result -> !result.succeeded()).singleElement()
                .extracting(result -> result.failure().getMessage())
                .asString()
                .contains("일일 이체한도를 초과했습니다");
        // 금일 기준계좌의 누적 이체량을 조회한다.
        DailyTransferUsage usage = dailyTransferUsageRepository
                .findByAccount_IdAndUsageDate(from.getId(), LocalDate.now(ZoneId.of("Asia/Seoul")))
                .orElseThrow();
        // 누적 이체량이 100인지를 검사한다.
        assertThat(usage.getUsedAmount()).isEqualByComparingTo("100");
        // 이체금액만큼 금액이 차감되었는지를 검사한다.
        assertThat(balance(from)).isEqualByComparingTo("2999900");
        // A계좌와 B계좌중 하나는 100을 입금받았기 때문에 총 금액은 6000100이 된다. (이때 두 스레드가 동시에 실행되었기 때문에, 어떤 계좌가 100원을 이체받았는지는 알 수 없다.
        assertThat(balance(firstTarget).add(balance(secondTarget))).isEqualByComparingTo("6000100");
        assertThat(transferRepository.count()).isEqualTo(1); // 계좌이체가 한번만 발생했기 때문에 transferRepository의 데이터는 1건
        assertThat(accountTransactionRepository.count()).isEqualTo(2); // 계좌이체가 한번만 발생했기 때문에 accountTranssactionRepository의 데이터는 2건
    }

    // 일반 계좌 -> 증권계좌 예수금 입금과 증권 계좌 -> 일반 계좌의 예수금 출금은 항상 일반계좌를 먼저 lock을 걸어 동시성이 발생하지 않도록 한다.
    @Test
    @DisplayName("INV-002/003/004: 일반계좌와 투자계좌의 양방향 동시 이동은 deadlock 없이 원자적이다")
    void accountInvestmentOpposingMovementsPreserveCashAndLedgers() throws Exception {
        User user = persistUser("account-investment-user");
        // 동일 사용자 명의로 일반 계좌와 증권계좌를 개설한다.
        Account account = persistAccount(user, "830000-00-000001", BankCode.KB_KOOKMIN, CurrencyCode.KRW);
        Investment investment = persistInvestment(
                user, "830000-11-000001", SecuritiesCompanyCode.KOREA_INVESTMENT);
        // 증권계좌에 출금가능한 금액 1000원을 미리 입금한다.
        depositCash(investment, "1000");
        // 일반계좌 300만원 + 증권계좌 1000원 = 3001000
        BigDecimal totalBefore = balance(account).add(availableCash(investment));
        // 두 작업을 동시에 실행하기 위한 CycleBarrier 객체 생성
        CyclicBarrier start = new CyclicBarrier(2);

        List<Future<WorkerResult>> futures = submitTogether(
                start,
                // deposit과 withdrawal을 통해 각각 예수금 입출금 정보를 저장하고 있는 InvestmentDepositRequest와 InvestmentWithdrawRequest객체를 생성하고, 예수금 입출금 작업을 수행한다.
                // 작업 1: 일반계좌 -> 증권계좌로 예수금 400원 입금
                () -> investmentService.depositToInvestment(deposit(account, investment, "400"), user.getId()),
                // 작업 2: 증권계좌 -> 일반계좌로 예수금 300원 출금
                () -> investmentService.withdrawFromInvestment(withdrawal(investment, account, "300"), user.getId()));

        assertThat(await(futures)).allMatch(WorkerResult::succeeded); // 두 작업이 모두 성공하는지
        BigDecimal accountAfter = balance(account);
        BigDecimal investmentAfter = availableCash(investment);
        assertThat(accountAfter).isEqualByComparingTo("2999900"); // 3000000 - 400 + 300
        assertThat(investmentAfter).isEqualByComparingTo("1100"); // 1000 + 400 - 300
        assertThat(accountAfter.add(investmentAfter)).isEqualByComparingTo(totalBefore); // 총금액은 일치해야 한다.
        assertThat(transferRepository.count()).isEqualTo(2); // transferRepository에는 총 2건의 이체내역이 저장된다.
        assertThat(accountTransactionRepository.count()).isEqualTo(2); // accountTransactionRepository에는 일반계좌 관점에서의 2건의 이체내역이 저장된다.
        assertThat(securitiesCashTransactionRepository.count()).isEqualTo(2); // SecuritiesCashTransactionRepository에는 증권계좌 관점에서의 2건의 이체내역이 저장된다.
        assertThat(todayUsage(account)).isNull(); // 일반계좌의 금일 누적 거래량은 null이어야 한다 (일반 계좌 -> 증권 계좌로의 이체는 본인 명의의 계좌로만 이체되기 때문에 안전하므로 이체한도를 걸지않는다.)
    }

    // WorkerResult는 작업이 성공했는지, 실패했다면 어떤 예외가 발생했는지를 담는 결과 객체이다.
    // Future<WorkerResult>는 작업이 끝날때까지 기다리는 것이 아니라, 다음 코드를 실행하다가, 나중에 WorkerResult를 필요할때 꺼내서 확인할 수 있는 객체이다.
    private List<Future<WorkerResult>> submitTogether(CyclicBarrier barrier,
                                                      ThrowingRunnable first,
                                                      ThrowingRunnable second) {
        // 스레드 풀에 스레드를 2개를 생성한다.
        executor = Executors.newFixedThreadPool(2);
        return List.of(
                // Executor( 스레드풀)에 Callable을  submit하면, 해당 스레드풀에서 해당 Callable 작업이 실행된다.
                executor.submit(worker(barrier, first)),
                executor.submit(worker(barrier, second)));
    }

    // 해당 메서드에서는 barrier를 통해서 두 스레드가 모두 준비가 될때까지 기다리고, 두 스레드 작업을 동시에 실행시킨다.
    // 이후 작업을 실행하고, 작업 성공시 success를, 작업 실패시 예외를 리턴한다.
    // Callable은 스레드가 실행할 수 있는 작업객체이다.
    private Callable<WorkerResult> worker(CyclicBarrier barrier, ThrowingRunnable action) {
        return () -> {
            barrier.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            try {
                action.run();
                return WorkerResult.success();
            } catch (RuntimeException failure) {
                return WorkerResult.failure(failure);
            }
        };
    }

    // await 메서드는 두 작업이 끝날때까지 기다리다가 작업 결과(WorkerResult)를 리턴한다.
    private List<WorkerResult> await(List<Future<WorkerResult>> futures)
            throws InterruptedException, ExecutionException, TimeoutException {
        return List.of(
                // futures에서 Future 객체를 꺼내고, 작업이 끝날때까지 최대 FUTURE_TIMEOUT_SECONDS만큼을 기다린다. (데드락에 의해 너무 오래기다리는 것을 방지한다)
                futures.get(0).get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                futures.get(1).get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private BigDecimal balance(Account account) {
        return accountRepository.findById(account.getId()).orElseThrow().getBalance();
    }

    private BigDecimal availableCash(Investment investment) {
        return cashBalance(investment).getAvailableBalance();
    }

    private InvestmentCashBalance cashBalance(Investment investment) {
        return cashBalanceRepository.findByInvestmentAccount_Id(investment.getId()).stream()
                .filter(balance -> balance.getCurrencyCode() == CurrencyCode.KRW)
                .findFirst()
                .orElseThrow();
    }


    private void depositCash(Investment investment, String amount) {
        // 증권계좌로 예수금을 입금하는 작업은 트랜잭션 내에서 실행되어야 하며, 이때문에 transactionTemplate을 사용한다.
        transactionTemplate.executeWithoutResult(status ->
                cashBalanceRepository.findByInvestmentIdAndCurrencyCodeForUpdate(
                                investment.getId(), CurrencyCode.KRW)
                        .orElseThrow()
                        .deposit(new BigDecimal(amount)));
    }

    private DailyTransferUsage todayUsage(Account account) {
        return dailyTransferUsageRepository.findByAccount_IdAndUsageDate(
                account.getId(), LocalDate.now(ZoneId.of("Asia/Seoul"))).orElse(null);
    }

    // from 계좌 -> to 계좌로 계좌이체를 하기 위한 TransferRequest 객체를 생성
    private TransferRequest transfer(Account from, Account to, String amount) {
        TransferRequest request = new TransferRequest();
        request.setFromAccountNumber(from.getAccountNumber());
        request.setFromBankCode(from.getBankCode());
        request.setToAccountNumber(to.getAccountNumber());
        request.setToBankCode(to.getBankCode());
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private InvestmentDepositRequest deposit(Account account, Investment investment, String amount) {
        InvestmentDepositRequest request = new InvestmentDepositRequest();
        //
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

    // 하나의 작업을 저장하고 있는 사용자 정의 함수형 인터페이스
    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    // WorkerResult에서는 성공여부와 실패시 예외를 담는 레코드이다.
    private record WorkerResult(boolean succeeded, RuntimeException failure) {
        private static WorkerResult success() {
            return new WorkerResult(true, null);
        }

        private static WorkerResult failure(RuntimeException failure) {
            return new WorkerResult(false, failure);
        }
    }
}
