package com.finmate.service.stock.trading;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.InvestmentCashBalance;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.StockSecurityType;
import com.finmate.domain.stock.dto.trading.StockOrderRequest;
import com.finmate.domain.stock.dto.trading.StockOrderReservationRequest;
import com.finmate.domain.stock.trading.StockHolding;
import com.finmate.domain.stock.trading.StockOrderSide;
import com.finmate.domain.stock.trading.StockOrderTriggerCondition;
import com.finmate.domain.stock.trading.StockOrderType;
import com.finmate.domain.user.User;
import com.finmate.repository.investment.InvestmentCashBalanceRepository;
import com.finmate.repository.stock.StockRepository;
import com.finmate.repository.stock.trading.StockHoldingRepository;
import com.finmate.service.stock.realtime.StockRealtimeSubscriptionManager;
import com.finmate.support.MySqlIntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

// 스프링 컨텍스트 로딩
@SpringBootTest(properties = {
        "finmate.stock-ranking.initial-delay-millis=86400000",
        "finmate.market-realtime.initial-delay-millis=86400000"
})
abstract class StockSettlementIntegrationTestSupport extends MySqlIntegrationTestSupport {

    // 테스트 사용자를 만들 때, 이메일과 아이디가 중복되지 않도 번호를 증가시키는 용도
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    // JPA 엔티티를 직접 저장하거나 조회하기 위한 객체
    @Autowired
    protected EntityManager entityManager;

    // 명시적으로 트랜잭션을 실행하기 위한 객체
    @Autowired
    protected TransactionTemplate transactionTemplate;

    // JPA가 아니라 SQL을 직접 실행하기 위한 Spring 객체
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected InvestmentCashBalanceRepository cashBalanceRepository;

    @Autowired
    protected StockHoldingRepository holdingRepository;

    @Autowired
    protected StockRepository stockRepository;

    // 실제로 사용되지는 않지만, 의존관계 주입을 위해서 Mock 가짜 객체를 생성한다.
    @MockitoBean
    protected StockTradingRealtimePriceService realtimePriceService;

    @MockitoBean
    protected StockRealtimeSubscriptionManager subscriptionManager;

    protected Fixture persistFixture(CurrencyCode currencyCode, BigDecimal availableCash) {
        // 테스트용 사용자와 증권계좌, 테스트용 종목, 증권계좌에 예수금 입금 후 Fixture 레코드에 (사용자, 증권계좌, 종목, 통화코드)를 담아서 리턴한다.
        return transactionTemplate.execute(status -> {
            int sequence = SEQUENCE.incrementAndGet();
            User user = new User();
            user.setUsername("stock-settlement-" + sequence);
            user.setTelephone("010-0000-0000");
            user.setEmail("stock-settlement-" + sequence + "@finmate.test");
            user.setUserId(String.format("settle%08d", sequence));
            user.setPassword("password1!");
            entityManager.persist(user);

            Investment investment = Investment.create(user, "INV-" + sequence,
                    SecuritiesCompanyCode.KOREA_INVESTMENT);
            entityManager.persist(investment);

            StockMarketType marketType = currencyCode == CurrencyCode.KRW
                    ? StockMarketType.KOSPI
                    : StockMarketType.NASDAQ;
            // 테스트용 종목 생성
            Stock stock = Stock.create(
                    currencyCode == CurrencyCode.KRW ? "K" + sequence : "N" + sequence,
                    currencyCode == CurrencyCode.KRW ? "K" + sequence : "DNASN" + sequence,
                    currencyCode == CurrencyCode.KRW ? "KR" + sequence : null,
                    "정산테스트" + sequence,
                    "Settlement Test " + sequence,
                    marketType,
                    currencyCode == CurrencyCode.KRW ? "KR" : "US",
                    currencyCode == CurrencyCode.KRW ? "KRX" : "NAS",
                    currencyCode.name(),
                    StockSecurityType.COMMON_STOCK,
                    false,
                    null,
                    LocalDateTime.of(2026, 7, 23, 0, 0));
            entityManager.persist(stock);

            InvestmentCashBalance cashBalance = investment.getCashBalances().stream()
                    .filter(balance -> balance.getCurrencyCode() == currencyCode)
                    .findFirst()
                    .orElseThrow();
            // 증권계좌의 통화에 맞는 cashBalance에 예수금 입금
            if (availableCash.signum() > 0) {
                cashBalance.deposit(availableCash);
            }
            entityManager.flush();
            return new Fixture(user.getId(), investment.getId(), stock.getId(), currencyCode);
        });
    }


    protected void persistHolding(Fixture fixture, BigDecimal quantity, BigDecimal averagePrice) {
        transactionTemplate.executeWithoutResult(status -> {
            // 증권계좌와 종목을 프록시 객체로 조회한다.
            Investment investment = entityManager.getReference(Investment.class, fixture.investmentId());
            Stock stock = entityManager.getReference(Stock.class, fixture.stockId());
            // 해당 증권계좌가 해당 종목에 대한 보유 수량을 나타내는 StockHolding을 생성한다.
            StockHolding holding = StockHolding.create(investment, stock, fixture.currencyCode());
            // 종목 구매를 통해 StockHolding을 업데이트한 후 DB에 저장한다
            holding.applyBuyExecution(quantity, averagePrice);
            entityManager.persist(holding);
            entityManager.flush();
        });
    }

    // 증권계좌의 CashBalance를 찾아서 리턴한다.
    protected InvestmentCashBalance cashBalance(Fixture fixture) {
        return cashBalanceRepository.findByInvestmentAccount_Id(fixture.investmentId()).stream()
                .filter(balance -> balance.getCurrencyCode() == fixture.currencyCode())
                .findFirst()
                .orElseThrow();
    }

    // StockHolding을 DB에서 조회한다.
    protected StockHolding holding(Fixture fixture) {
        return holdingRepository.findByInvestment_IdAndStock_Id(fixture.investmentId(), fixture.stockId())
                .orElseThrow();
    }

    // 일반주문 요청(StockOrderRequest)를 생성한다.
    protected StockOrderRequest orderRequest(Fixture fixture,
                                             StockOrderSide side,
                                             BigDecimal quantity,
                                             BigDecimal orderPrice) {
        StockOrderRequest request = new StockOrderRequest();
        request.setInvestmentId(fixture.investmentId());
        request.setStockId(fixture.stockId());
        request.setSide(side); // 매수 or 매도
        request.setOrderType(StockOrderType.LIMIT); // 지정가 (예약 주문)
        request.setQuantity(quantity);
        request.setOrderPrice(orderPrice); // 지정가 설정
        request.setExpiresAt(LocalDateTime.now().plusDays(1));
        return request;
    }
    // 예약주문 요청(StockOrderReservationRequest)를 생성한다.
    protected StockOrderReservationRequest reservationRequest(Fixture fixture,
                                                               StockOrderSide side,
                                                               BigDecimal quantity,
                                                               BigDecimal triggerPrice,
                                                               BigDecimal orderPrice) {
        StockOrderReservationRequest request = new StockOrderReservationRequest();
        request.setInvestmentId(fixture.investmentId());
        request.setStockId(fixture.stockId());
        request.setSide(side); // 매수 or 매도
        request.setOrderType(StockOrderType.LIMIT); // 지정가주문
        request.setTriggerCondition(side == StockOrderSide.BUY // 예약충족 조건
                ? StockOrderTriggerCondition.PRICE_AT_OR_BELOW
                : StockOrderTriggerCondition.PRICE_AT_OR_ABOVE);
        request.setQuantity(quantity);
        request.setTriggerPrice(triggerPrice); // 예약충족 가격
        request.setOrderPrice(orderPrice);
        request.setExpiresAt(LocalDateTime.now().plusDays(1));
        return request;
    }

    // Fixture는 사용자, 증권계좌, 종목, 통화를 저장하는 레코드
    protected record Fixture(Long userId, Long investmentId, Long stockId, CurrencyCode currencyCode) {
    }
}
