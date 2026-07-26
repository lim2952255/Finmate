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
import com.finmate.repository.investment.InvestmentRepository;
import com.finmate.repository.investment.cash.exchange.InvestmentCurrencyExchangeTransactionRepository;
import com.finmate.service.market.MarketRealtimeQuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 단위 테스트
class InvestmentCurrencyExchangeServiceTest {

    // 실제로 사용하지는 않지만, 연관관계 주입이 필요한 객체들을 Mock을 통해 가짜 객체로 생성한다.
    private final InvestmentRepository investmentRepository = mock(InvestmentRepository.class);
    private final InvestmentCashBalanceRepository cashBalanceRepository = mock(InvestmentCashBalanceRepository.class);
    private final InvestmentCurrencyExchangeTransactionRepository transactionRepository =
            mock(InvestmentCurrencyExchangeTransactionRepository.class);
    private final MarketRealtimeQuoteService quoteService = mock(MarketRealtimeQuoteService.class);

    // InvestmentCurrencyExchangeService 객체 생성 및 연관관계 직접 주입
    private final InvestmentCurrencyExchangeService service = new InvestmentCurrencyExchangeService(
            investmentRepository, cashBalanceRepository, transactionRepository, quoteService);

    private User owner;
    private Investment investment;
    private InvestmentCashBalance krwBalance;
    private InvestmentCashBalance usdBalance;

    // 매 테스트가 실행되기 전에 사용지, 증권계좌, KrwBalance, UsdBalance등을 초기화하고, 각 Mock객체의 특정 메서드들의 기능을 추가한다.
    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(41L);
        investment = Investment.create(owner, "900000-11-000001", SecuritiesCompanyCode.KIWOOM);
        setInvestmentId(investment, 73L);
        krwBalance = cashBalance(CurrencyCode.KRW);
        usdBalance = cashBalance(CurrencyCode.USD);

        // Mock 객체들에 특정 기능들을 추가
        when(investmentRepository.findByIdForUpdate(73L)).thenReturn(Optional.of(investment));
        when(cashBalanceRepository.findByInvestmentIdAndCurrencyCodeForUpdate(73L, CurrencyCode.KRW))
                .thenReturn(Optional.of(krwBalance));
        when(cashBalanceRepository.findByInvestmentIdAndCurrencyCodeForUpdate(73L, CurrencyCode.USD))
                .thenReturn(Optional.of(usdBalance));
        // innocation은 Mockito가 가짜 메서드 호출 정보를 담아서 넘겨주는 객체이다.
        // 이 코드에서는 transactionRepository.save(어떤 객체)가 호출되면 save()에 전달된 첫 번째 인자를 그대로 반환하라는 코드이다.
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("FX-001/003: 최신 환율의 KRW→USD 결과를 달러 최소 단위로 내림한다")
    void fx001_usesLatestQuoteAndKrwToUsdRoundsDownToUsdMinorUnit() {
        krwBalance.deposit(new BigDecimal("1500")); // krwBalance에 1500원 입금
        quote(new BigDecimal("1333.3333333333")); // 환율 설정

        // KRW -> USD 환전 수행 (1500원)
        InvestmentCurrencyExchangeTransaction transaction = service.exchangeCurrency(
                owner.getId(), request(CurrencyCode.KRW, CurrencyCode.USD, "1500"));

        assertThat(transaction.getExchangeRate()).isEqualByComparingTo("1333.3333333333");
        assertThat(transaction.getFromAmount()).isEqualByComparingTo("1500");
        assertThat(transaction.getToAmount()).isEqualByComparingTo("1.12");
        // 환전 결과를 달러 최소단위 (Scale: 2)로 맞춰서 내림한다.
        assertThat(krwBalance.getAvailableBalance()).isEqualByComparingTo("0");
        assertThat(usdBalance.getAvailableBalance()).isEqualByComparingTo("1.12");
    }

    @Test
    @DisplayName("FX-003: USD→KRW 환전은 환율을 곱한 뒤 원 단위로 내림한다")
    void fx003_usdToKrwMultipliesAndRoundsDownToWholeWon() {
        usdBalance.deposit(new BigDecimal("1.23")); // USDBalance에 1.23 USD를 입금한다.
        quote(new BigDecimal("1333.3333333333"));

        // USD -> KRW 환전 수행 (1.23 USD)
        InvestmentCurrencyExchangeTransaction transaction = service.exchangeCurrency(
                owner.getId(), request(CurrencyCode.USD, CurrencyCode.KRW, "1.23"));
        // 1.23 * 1333.333333... 수행후 원화 단위로 내림한다 (Scale: 0)
        assertThat(transaction.getToAmount()).isEqualByComparingTo("1639");
        assertThat(usdBalance.getAvailableBalance()).isEqualByComparingTo("0");
        assertThat(krwBalance.getAvailableBalance()).isEqualByComparingTo("1639");
    }

    // 환전은 항상 Investment -> KRW -> USD 순서로 lock을 획득하여 데드락을 방지한다.
    @Test
    @DisplayName("FX-002: 역방향 환전도 Investment→KRW→USD 순서로 잠근다")
    void fx002_locksInvestmentThenKrwThenUsdEvenForReverseExchange() {
        usdBalance.deposit(new BigDecimal("1.00"));
        quote(new BigDecimal("1500"));

        // USD -> KRW로 환전을 수행할때에도 항상 lock은 KRW -> USD 순서로 획득해야 한다.
        service.exchangeCurrency(owner.getId(), request(CurrencyCode.USD, CurrencyCode.KRW, "1.00"));

        // InOrder는 Mockito에게 다음 두 Mock 객체의 메서드 호출순서를 추적하라고 지시하는 것이다.
        InOrder order = inOrder(investmentRepository, cashBalanceRepository);
        // 따라서 역방향 환전의 경우에도 항상 Investment -> KRW -> USD 순서로 lock을 획득하는지를 검증한다.
        order.verify(investmentRepository).findByIdForUpdate(73L);
        order.verify(cashBalanceRepository)
                .findByInvestmentIdAndCurrencyCodeForUpdate(73L, CurrencyCode.KRW);
        order.verify(cashBalanceRepository)
                .findByInvestmentIdAndCurrencyCodeForUpdate(73L, CurrencyCode.USD);
    }

    @Test
    @DisplayName("FX-001: 환율이 없으면 금융 상태를 조회하거나 변경하지 않는다")
    void fx001_missingQuoteDoesNotLoadOrMutateFinancialState() {
        krwBalance.deposit(new BigDecimal("1500"));
        // quoteService의 getLatest 메서드 호출시 Optional.empty()가 반환되돌고 Mock에 기능을 추가한다. 이는 최신 환율 정보를 알 수 없다는 의미이다.
        when(quoteService.getLatest(MarketIndicatorSymbol.USD_KRW)).thenReturn(Optional.empty());

        // 최신 환율 정보를 조회할 수 없는 경우에는 예외가 발생한다.
        assertThatThrownBy(() -> service.exchangeCurrency(
                owner.getId(), request(CurrencyCode.KRW, CurrencyCode.USD, "1500")))
                .hasMessage("USD/KRW 환율을 조회할 수 없습니다.");

        // 환전에 실패하면 기존 금액을 유지해야 한다.
        assertThat(krwBalance.getAvailableBalance()).isEqualByComparingTo("1500");
        assertThat(usdBalance.getAvailableBalance()).isZero();

        // 해당 Mock 메서드가 한번호 호출되지 않았는지를 검사한다.
        verify(investmentRepository, never()).findByIdForUpdate(any());
        verify(transactionRepository, never()).save(any());
    }

    @ParameterizedTest
    @DisplayName("FX-001: 0 이하 환율은 금융 상태를 조회하거나 변경하지 않고 거부한다")
    @ValueSource(strings = {"0", "-1"})
    void fx001_nonPositiveQuoteDoesNotLoadOrMutateFinancialState(String rate) {
        krwBalance.deposit(new BigDecimal("1500"));
        // 환율이 0 이하인 경우, 문제가 발생한 상황이기 때문에 환전 시도시 예외가 발생해야 한다.
        quote(new BigDecimal(rate));

        assertThatThrownBy(() -> service.exchangeCurrency(
                owner.getId(), request(CurrencyCode.KRW, CurrencyCode.USD, "1500")))
                .hasMessage("USD/KRW 환율을 조회할 수 없습니다.");

        assertThat(krwBalance.getAvailableBalance()).isEqualByComparingTo("1500");
        verify(investmentRepository, never()).findByIdForUpdate(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("FX-003: 환전 결과가 대상 통화 최소 단위보다 작으면 락 획득 전에 거부한다")
    void fx003_rejectsResultBelowTargetCurrencyMinimumBeforeFinancialLocks() {
        krwBalance.deposit(BigDecimal.ONE);
        quote(new BigDecimal("1500"));

        // 환전 계산 결과가 USD 통화의 최소단위인 0.01보다 작은 경우에는 환전요청을 거부하고 예외를 발생시켜야 한다.
        assertThatThrownBy(() -> service.exchangeCurrency(
                owner.getId(), request(CurrencyCode.KRW, CurrencyCode.USD, "1")))
                .hasMessage("환전 후 금액이 최소 단위보다 작습니다.");

        assertThat(krwBalance.getAvailableBalance()).isEqualByComparingTo("1");
        verify(investmentRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("FX-003: 출발 통화의 허용 소수 자릿수를 초과한 금액은 환율 조회 전에 거부한다")
    void fx003_rejectsSourceAmountScaleBeyondCurrencyContract() {
        quote(new BigDecimal("1500"));

        // 통화 허용 소수 자릿수를 초과한 금액을 입력한 경우에는 예와가 발생해야 한다.
        assertThatThrownBy(() -> service.exchangeCurrency(
                owner.getId(), request(CurrencyCode.USD, CurrencyCode.KRW, "1.001")))
                .hasMessage("USD는 소수점 2자리까지 입력할 수 있습니다.");

        verify(quoteService, never()).getLatest(any());
        verify(investmentRepository, never()).findByIdForUpdate(any());
    }

    private void quote(BigDecimal rate) {
        when(quoteService.getLatest(MarketIndicatorSymbol.USD_KRW))
                .thenReturn(Optional.of(message(rate)));
    }

    private InvestmentCurrencyExchangeRequest request(CurrencyCode from, CurrencyCode to, String amount) {
        InvestmentCurrencyExchangeRequest request = new InvestmentCurrencyExchangeRequest();
        request.setInvestmentId(73L);
        request.setSecuritiesCompanyCode(SecuritiesCompanyCode.KIWOOM);
        request.setFromCurrencyCode(from);
        request.setToCurrencyCode(to);
        request.setFromAmount(new BigDecimal(amount));
        return request;
    }

    private InvestmentCashBalance cashBalance(CurrencyCode currencyCode) {
        return investment.getCashBalances().stream()
                .filter(balance -> balance.getCurrencyCode() == currencyCode)
                .findFirst()
                .orElseThrow();
    }

    private void setInvestmentId(Investment target, Long id) {
        try {
            var field = Investment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private MarketRealtimeMessage message(BigDecimal rate) {
        return new MarketRealtimeMessage(
                null, MarketIndicatorSymbol.USD_KRW, null, null, null, null, 2, null,
                rate, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
