package com.finmate.service.investment;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.InvestmentCashBalance;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.investment.cash.exchange.InvestmentCurrencyExchangeTransaction;
import com.finmate.domain.investment.dto.exchange.InvestmentCurrencyExchangeRequest;
import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.domain.market.dto.MarketRealtimeMessage;
import com.finmate.domain.user.User;
import com.finmate.repository.investment.InvestmentCashBalanceRepository;
import com.finmate.repository.investment.cash.exchange.InvestmentCurrencyExchangeTransactionRepository;
import com.finmate.service.market.MarketRealtimeQuoteService;
import com.finmate.support.FinancialIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

// 이때 FinancialIntegrationTestSupport를 상속받기 떄문에, 스프링 컨텍스트를 로드하고, 테스트용 DB에 연결해서 테스트용 사용자와 계좌를 등록한다.
class CurrencyExchangeIntegrationTest extends FinancialIntegrationTestSupport {

    @Autowired
    private InvestmentCurrencyExchangeService exchangeService;

    @Autowired
    private InvestmentCashBalanceRepository cashBalanceRepository;

    //MockitoSpyBean은 실제 스프링 빈을 의존관계 주입을 받지만, 이를 MockitoSpyBean으로 감싼다음에, 일부 기능만 수정해서 사용하기 위해서 사용한다.
    @MockitoSpyBean
    private InvestmentCurrencyExchangeTransactionRepository transactionRepository;

    // 환율서비스는 실제 KIS API와의 연동이 필요하기 때문에, 실제 MarketRealtimeQuoteService를 호출하면 서비스가 불안정해진다.
    // 따라서 테스트 안정화를 위해 실제 MarketRealtimeQuoteService 객체 대신 MockitoBean을 통해 가짜 객체를 만들어서 주입한다.
    @MockitoBean
    private MarketRealtimeQuoteService quoteService;

    // 매 테스트가 끝날때마다 MockitoSpyBean에 수정한 기능들을 다시 원상태로 되돌린다.
    @AfterEach
    void resetTransactionRepositorySpy() {
        reset(transactionRepository);
    }

    @Test
    @DisplayName("FX-003/004: 환전은 통화 단위로 절삭한 잔액 스냅샷을 기록하고 잠금 예수금을 보존한다")
    void fx003_fx004_exchangePersistsRoundedSnapshotsAndLeavesLockedBalancesUntouched() {
        User user = persistUser("fx-owner");
        Investment investment = persistInvestment(
                user, "910000-11-000001", SecuritiesCompanyCode.KIWOOM);
        // 증권계좌에 2000원만큼 입금
        depositCash(investment, CurrencyCode.KRW, "2000");
        // 증권계좌의 원화 잔고중 500원 만큼에 lock을 건다
        lockCash(investment, CurrencyCode.KRW, "500");
        // 증권계좌에 2 USD를 입금
        depositCash(investment, CurrencyCode.USD, "2.00");
        // 증권계좌의 USD 잔고중 0.50 USD만큼에 lock을 건다
        lockCash(investment, CurrencyCode.USD, "0.50");
        // 환율 설정 -> quoteService의 LatestRate가 1333.33333...이 된다.
        quote("1333.3333333333");

        // 환율 기반으로 환전을 수행한다.
        // 이때 ExchangeService의 exchangeCurrency()메서는 내부에서 QuoteService에서 LatestRate를 꺼내서 환전을 수행한다.
        InvestmentCurrencyExchangeTransaction transaction = exchangeService.exchangeCurrency(
                // 원화를 달러로 1500원어치 만큼을 환전
                user.getId(), request(investment, CurrencyCode.KRW, CurrencyCode.USD, "1500"));

        // assertCash는 availableBalance와 lockedBalance를 검증한다.
        assertCash(investment, CurrencyCode.KRW, "0", "500");
        assertCash(investment, CurrencyCode.USD, "2.62", "0.50");
        // 환전 내역 Repository에 환전 내역 1건이 저장되어야 한다.
        assertThat(transactionRepository.count()).isEqualTo(1);
        // 환율 정보
        assertThat(transaction.getExchangeRate()).isEqualByComparingTo("1333.3333333333");
        // 환전 금액 검사
        assertThat(transaction.getFromAmount()).isEqualByComparingTo("1500");
        assertThat(transaction.getToAmount()).isEqualByComparingTo("1.12");
        // 원화 환전 전 금액과 한전 후 금액 검증
        assertThat(transaction.getFromBalanceBeforeExchange()).isEqualByComparingTo("1500");
        assertThat(transaction.getFromBalanceAfterExchange()).isEqualByComparingTo("0");
        // 달러 환전 전 금액과 한전 후 금액 검증
        assertThat(transaction.getToBalanceBeforeExchange()).isEqualByComparingTo("1.50");
        assertThat(transaction.getToBalanceAfterExchange()).isEqualByComparingTo("2.62");
    }

    @Test
    @DisplayName("FX-001: 환율 조회 실패 시 양쪽 예수금과 환전 원장을 변경하지 않는다")
    void fx001_quoteLookupFailureLeavesBothBalancesAndLedgerUnchanged() {
        User user = persistUser("fx-owner");
        Investment investment = persistInvestment(
                user, "920000-11-000001", SecuritiesCompanyCode.KIWOOM);
        // 증권계좌의 원화에 1500원을 입근한다.
        depositCash(investment, CurrencyCode.KRW, "1500");
        // quoteService(Mockito)의 getLatest메서드를 호출 시 Optional.empty()를 리턴하도록 설정한다.
        when(quoteService.getLatest(MarketIndicatorSymbol.USD_KRW)).thenReturn(Optional.empty());

        // quoeService.getLatest를 통해 환율을 조회할 수 없기 때문에 환전 시도시 예외가 발생해야 한다.
        assertThatThrownBy(() -> exchangeService.exchangeCurrency(
                user.getId(), request(investment, CurrencyCode.KRW, CurrencyCode.USD, "1500")))
                .hasMessage("USD/KRW 환율을 조회할 수 없습니다.");

        // 환전에 실패했기 때문에 증권계좌의 통화별 잔고가 그대로여야 하며, TransactionRepository에도 환전내역이 저장되어서는 안된다.
        assertCash(investment, CurrencyCode.KRW, "1500", "0");
        assertCash(investment, CurrencyCode.USD, "0", "0");
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("FX-004: 환전 원장 저장 실패 시 잠금 스냅샷을 포함한 양쪽 예수금을 롤백한다")
    void fx004_ledgerSaveFailureRollsBackBothBalancesIncludingLockedSnapshots() {
        User user = persistUser("fx-owner");
        Investment investment = persistInvestment(
                user, "930000-11-000001", SecuritiesCompanyCode.KIWOOM);
        // 증권계좌의 원화 통화에 2000원 입금
        depositCash(investment, CurrencyCode.KRW, "2000");
        // 증권계좌의 원화 통화에 500원에 lock을 건다
        lockCash(investment, CurrencyCode.KRW, "500");
        // 증권계좌의 달러 통화에 2.00 USD 입금
        depositCash(investment, CurrencyCode.USD, "2.00");
        // 증권계좌의 달러 통화에 0.50 USD에 lock을 건다
        lockCash(investment, CurrencyCode.USD, "0.50");
        // 환율을 1500으로 설정
        quote("1500");

        // MockitoSpyBean을 통해 TransactionRepository 객체의 save 메서드 호출 시 예외가 발생하도록 기능을 수정한다.
        doThrow(new RuntimeException("injected exchange ledger failure"))
                .when(transactionRepository).save(any());

        // TransactionRepository에 환전 내역을 저장하는데 실패하기 때문에 환전이 성공적으로 수행되면 안되며, 예외가 발생해야 한다.
        assertThatThrownBy(() -> exchangeService.exchangeCurrency(
                user.getId(), request(investment, CurrencyCode.KRW, CurrencyCode.USD, "1500")))
                .hasMessage("injected exchange ledger failure");
        // MockitoSpyBean에 추가한 기능을 초기화한다.
        reset(transactionRepository);
        // TransactionRepository에 환전 내역을 저장하는데 실패하기 때문에 통화별 금액에 변화가 없어야 하며, 환전 내역이 0건이어야 한다.
        assertCash(investment, CurrencyCode.KRW, "1500", "500");
        assertCash(investment, CurrencyCode.USD, "1.50", "0.50");
        assertThat(transactionRepository.count()).isZero();
    }
    // KRW -> USD / USD -> KRW 양방향 동시 환전시에도 항상 원화먼저 lock을 얻기 때문에 데드락이 발생하지 않는다.
    @Test
    @DisplayName("FX-002: 반대 방향 동시 환전은 데드락과 잔액 유실 없이 완료된다")
    @Timeout(value = 15, unit = TimeUnit.SECONDS) // 테스트의 Timeout을 15초로 설정
    void fx002_oppositeDirectionExchangesFinishWithoutDeadlockOrLostBalance() throws Exception {
        User user = persistUser("fx-owner");
        Investment investment = persistInvestment(
                user, "940000-11-000001", SecuritiesCompanyCode.KIWOOM);
        depositCash(investment, CurrencyCode.KRW, "3000");
        depositCash(investment, CurrencyCode.USD, "2.00");
        quote("1500");

        // CountDownLatch는 여러 스레드의 실행시점을 맞추기 위한 동기화도구이다. (CircuitBarrier와 유사)
        // CountDownLatch에서는 count값이 0이 될때까지 모든 스레드가 대기한다. 메인스레드가 count값을 감소시켜 0이 되는 시점에 모든 스레드가 동시에 시작된다.
        CountDownLatch start = new CountDownLatch(1);
        // ExecutorService ( 스레드 풀)에 두개의 스레드 생성
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // KRW -> USD로 환전하는 작업과 USD -> KRW로 환전하는 두가지 작업을 등록하고 CountDownLatch 앞에서 기다리게 한다.
            Future<?> krwToUsd = executor.submit(() -> exchangeAfterStart(
                    start, user, investment, CurrencyCode.KRW, CurrencyCode.USD, "1500"));
            Future<?> usdToKrw = executor.submit(() -> exchangeAfterStart(
                    start, user, investment, CurrencyCode.USD, CurrencyCode.KRW, "1.00"));

            // 메인스레드에서 CountDownLatch의 값을 1만큼 감소시킨다 -> 이 경우 Count값이 0이되면서 CountDownLatch 앞에서 대기중이던 스레드들이 작업을 시작한다.
            start.countDown();
            // 두 Future에서 스레드작업이 끝나 결과를 받을때까지 최대 10초동안 대기한다.
            krwToUsd.get(10, TimeUnit.SECONDS);
            usdToKrw.get(10, TimeUnit.SECONDS);
        } finally {
            // ExecutorService를 종료한다.(스레드 풀 정리)
            executor.shutdownNow();
        }
        // KRW -> USD 와 USD -> KRW를 환율 기준으로 동일한 금액만큼 환전했기 때문에 최종적인 통화별 금액의 결과는 처음과 같아야 한다.
        assertCash(investment, CurrencyCode.KRW, "3000", "0");
        assertCash(investment, CurrencyCode.USD, "2.00", "0");
        // TransactionRepository에 저장된 모든 거래내역을 확인한다.(총 2건)
        List<InvestmentCurrencyExchangeTransaction> transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(2);
        assertThat(transactions)
                // KRW -> USD와 USD -> KRW 환전 내역이 기록되어있는지를 확인한다.
                .extracting(InvestmentCurrencyExchangeTransaction::getFromCurrencyCode)
                .containsExactlyInAnyOrder(CurrencyCode.KRW, CurrencyCode.USD);
        // 환전 후 각 통화의 금액이 음수가 아닌지를 확인한다.
        assertThat(transactions).allSatisfy(transaction -> {
            assertThat(transaction.getFromBalanceAfterExchange()).isNotNegative();
            assertThat(transaction.getToBalanceAfterExchange()).isNotNegative();
        });
    }

    // 환전 작업 스레드가 시작신호를 기다렸다, 신호가 들어오면 실제 환전 서비스를 호출한다.
    private void exchangeAfterStart(CountDownLatch start,
                                    User user,
                                    Investment investment,
                                    CurrencyCode from,
                                    CurrencyCode to,
                                    String amount) {
        try {
            // start.await()은 현재 스레드를 CountDownLatch 앞에서 기다리게 한다. 이때 만약 5초안에 시작 시그널이 오지않으면 false를 리턴한다.
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("concurrent exchange start timed out");
            }
            exchangeService.exchangeCurrency(user.getId(), request(investment, from, to, amount));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    // 환전정보를 담는다. from 통화 -> to 통화로 amount만큼 환전
    private InvestmentCurrencyExchangeRequest request(Investment investment,
                                                      CurrencyCode from,
                                                      CurrencyCode to,
                                                      String amount) {
        InvestmentCurrencyExchangeRequest request = new InvestmentCurrencyExchangeRequest();
        request.setInvestmentId(investment.getId());
        request.setSecuritiesCompanyCode(investment.getSecuritiesCompanyCode());
        request.setFromCurrencyCode(from);
        request.setToCurrencyCode(to);
        request.setFromAmount(new BigDecimal(amount));
        return request;
    }

    // quote 메서드는 quoteService(Mockito)의 getLatest 메서드의 기능을 임시로 구현한다.
    private void quote(String rate) {
        when(quoteService.getLatest(MarketIndicatorSymbol.USD_KRW))
                .thenReturn(Optional.of(message(new BigDecimal(rate))));
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

    // availableBalance와 lockedBalance를 검증한다.
    private void assertCash(Investment investment, CurrencyCode currencyCode, String available, String locked) {
        InvestmentCashBalance balance = cashBalance(investment, currencyCode);
        assertThat(balance.getAvailableBalance()).isEqualByComparingTo(available);
        assertThat(balance.getLockedBalance()).isEqualByComparingTo(locked);
    }

    private MarketRealtimeMessage message(BigDecimal rate) {
        return new MarketRealtimeMessage(
                null, MarketIndicatorSymbol.USD_KRW, null, null, null, null, 2, null,
                rate, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
