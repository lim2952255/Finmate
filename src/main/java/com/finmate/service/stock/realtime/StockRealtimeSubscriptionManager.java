package com.finmate.service.stock.realtime;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.dto.realtime.RealtimeSubscriptionState;
import com.finmate.infra.kis.stock.realtime.KisRealtimeApi;
import com.finmate.infra.kis.stock.realtime.KisRealtimeSubscription;
import com.finmate.infra.kis.stock.realtime.KisRealtimeWebSocketClient;
import com.finmate.repository.stock.StockRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// 사용자들이 어떤 종목을 구독하고 있는지를 파악하여, 종목을 구독하고 구독해제를 관리하는 매니저
@Service
@RequiredArgsConstructor
public class StockRealtimeSubscriptionManager {
    private static final String KOSPI_INDEX_CODE = "0001";
    private static final String KOSDAQ_INDEX_CODE = "1001";
    private static final String NASDAQ_REALTIME_PREFIX = "DNAS";

    private final StockRepository stockRepository; // 종목을 저장하는 repository
    private final KisRealtimeWebSocketClient realtimeWebSocketClient; // 실제로 WebSocket연결을 관리하고, 데이터를 저장하는 client
    private final Map<KisRealtimeSubscription, AtomicInteger> subscriberCounts = new ConcurrentHashMap<>(); // 종목별로 구독하고 있는 사용자수를 관리(동시성문제가 발생할 수 있으므로 atomic하게 관리)
    private final Map<KisRealtimeSubscription, ScheduledFuture<?>> pendingUnsubscribes = new ConcurrentHashMap<>(); // 예약된 구독 해제 작업을 저장하는 Map
    private final ScheduledExecutorService unsubscribeExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> { // 구독 해제를 나중에 수행하기 위한 스케줄러
        Thread thread = new Thread(runnable, "kis-realtime-unsubscribe");
        thread.setDaemon(true);
        return thread;
    });

    // 구독 해제를 얼마나 늦출지를 설정하는 값(기본 값:60초) -> 사용자가 잠깐 구독을 취소하더라도, 다시 구독할 확률이 높기때문에 구독을 바로 취소하지 않는다.
    @Value("${finmate.kis.realtime-unsubscribe-grace-millis:60000}")
    private long unsubscribeGraceMillis;

    // stockId를 입력받고, 이에 해당하는 종목을 찾아서 구독하는 메서드
    @Transactional(readOnly = true)
    public RealtimeSubscriptionState subscribeStock(Long stockId) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new RuntimeException("Stock was not found."));

        return subscribe(resolveStockSubscription(stock));
    }

    // stockId를 입력받고, 이에 해당하는 종목의 구독을 해제하는 메서드
    @Transactional(readOnly = true)
    public RealtimeSubscriptionState unsubscribeStock(Long stockId) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new RuntimeException("Stock was not found."));

        return unsubscribe(resolveStockSubscription(stock));
    }

    // 코스피 / 코스닥 지수를 한번에 구독하는 메서드
    public List<RealtimeSubscriptionState> subscribeDomesticIndexes() {
        return List.of(
                subscribe(new KisRealtimeSubscription(KisRealtimeApi.DOMESTIC_INDEX_TRADE, KOSPI_INDEX_CODE)),
                subscribe(new KisRealtimeSubscription(KisRealtimeApi.DOMESTIC_INDEX_TRADE, KOSDAQ_INDEX_CODE)));
    }

    // 코스피 / 코스닥 지수를 한번에 구독해제하는 메서드
    public List<RealtimeSubscriptionState> unsubscribeDomesticIndexes() {
        return List.of(
                unsubscribe(new KisRealtimeSubscription(KisRealtimeApi.DOMESTIC_INDEX_TRADE, KOSPI_INDEX_CODE)),
                unsubscribe(new KisRealtimeSubscription(KisRealtimeApi.DOMESTIC_INDEX_TRADE, KOSDAQ_INDEX_CODE)));
    }

    // RealtimeWebSocketClient가 구독하고 있는 종목목록을 받아와서 상태 dto로 변환하는 메서드
    public List<RealtimeSubscriptionState> getSubscriptionStates() {
        return realtimeWebSocketClient.getActiveSubscriptions().stream()
                .map(this::toState)
                .toList();
    }

    // 실제로 구독을 처리하는 메서드
    private RealtimeSubscriptionState subscribe(KisRealtimeSubscription subscription) {
        // 구독을 수행하는 메서드이기 때문에, 해당 종목에 대한 구독 취소가 예약되어 있다면, 이를 취소한다.
        cancelPendingUnsubscribe(subscription);

        // 이미 해당 종목의 counter가 있으면 해당 값을 가져오고, 없으면 종목을 등록하고 0을 받는다.
        AtomicInteger counter = subscriberCounts.computeIfAbsent(subscription,ignored -> new AtomicInteger(0));
        // counter의 값을 1만큼 늘린다음, 값을 반환한다.(atomic)
        int subscriberCount = counter.incrementAndGet();

        try {
            // 종목 구독을 수행한다.
            realtimeWebSocketClient.subscribe(subscription);
        } catch (RuntimeException e) {
            // 종목을 구독하는데 실패했다면 다시 counter값을 되돌린다.
            int rolledBackCount = counter.updateAndGet(value -> Math.max(0, value - 1));
            if (rolledBackCount == 0) {
                // 만약 counter값이 0이 된다면 구독목록에서 종목을 제거한다.
                subscriberCounts.remove(subscription);
            }
            throw e;
        }
        // 종목의 구독정보를 dto로 만들어서 리턴한다.
        return toState(subscription, subscriberCount, true);
    }

    // 종목에 대한 구독을 취소하는 메서드
    private RealtimeSubscriptionState unsubscribe(KisRealtimeSubscription subscription) {
        AtomicInteger counter = subscriberCounts.get(subscription);
        if (counter == null) {
            // 만약 종목 구독목록에 해당 종목이 없다면 바로 리턴
            return toState(subscription, 0, false);
        }
        // 구독 counter를 1만큼 감소
        int subscriberCount = counter.updateAndGet(value -> Math.max(0, value - 1));
        if (subscriberCount == 0) {
            // 구독 counter가 0이 되면 구독취소 작업을 예약한다.
            scheduleUnsubscribe(subscription);
        }

        return toState(subscription, subscriberCount, subscriberCount > 0);
    }

    // 종목에 대한 구독취소작업을 예약하는 메서드
    private void scheduleUnsubscribe(KisRealtimeSubscription subscription) {
        // 같은 종목에 대한 구독 취소가 이미 예약되어 있으면 해당 구독취소 예약은 제거한다 (중복 예약 방지)
        cancelPendingUnsubscribe(subscription);

        // 일정 시간뒤에 실행할 작업을 등록한다.
        ScheduledFuture<?> future = unsubscribeExecutor.schedule(() -> {
            // 수행할 작압 지정
            // 이때 바로 구독을 취소하는 것이 아니라, count를 다시한번 체크해서 그 사이에 구독중인 사용자가 생겼는지를 확인한다.
            AtomicInteger counter = subscriberCounts.get(subscription);
            if (counter != null && counter.get() <= 0) {
                subscriberCounts.remove(subscription);
                realtimeWebSocketClient.unsubscribe(subscription);
            }
            pendingUnsubscribes.remove(subscription);
        }, Math.max(0, unsubscribeGraceMillis), TimeUnit.MILLISECONDS); // 60초뒤에 실행할 작업을 등록

        pendingUnsubscribes.put(subscription, future);
    }

    // 특정 종목에 설정되어 있는 구독 취소 예약이 존재한다면, 이를 해제한다.
    private void cancelPendingUnsubscribe(KisRealtimeSubscription subscription) {
        ScheduledFuture<?> pending = pendingUnsubscribes.remove(subscription);
        if (pending != null) {
            pending.cancel(false);
        }
    }

    // 구독중인 종목에 대한 정보를 담고 있는 dto 생성
    private RealtimeSubscriptionState toState(KisRealtimeSubscription subscription) {
        int subscriberCount = subscriberCounts.getOrDefault(subscription, new AtomicInteger(0)).get();
        return toState(subscription, subscriberCount, true);
    }

    // 구독중인 종목에 대한 정보를 담고 있는 dto 생성
    private RealtimeSubscriptionState toState(KisRealtimeSubscription subscription, int subscriberCount, boolean active) {
        return new RealtimeSubscriptionState(
                subscription.api(),
                subscription.api().getTrId(),
                subscription.trKey(),
                subscriberCount,
                active);
    }

    // Stock 엔티티를 보고, 어떤 KIS 실시간 API와 어떤 trKey를 사용할지를 결정하는 메서드
    private KisRealtimeSubscription resolveStockSubscription(Stock stock) {
        if (stock.getMarketType() == StockMarketType.NASDAQ) {
            return new KisRealtimeSubscription(
                    // api와 RealtimeSymbol을 저장
                    KisRealtimeApi.OVERSEAS_STOCK_TRADE,
                    resolveNasdaqRealtimeSymbol(stock));
        }

        if (stock.getMarketType() == StockMarketType.KOSPI || stock.getMarketType() == StockMarketType.KOSDAQ) {
            return new KisRealtimeSubscription(
                    // api와 RealtimeSymbol을 저장
                    KisRealtimeApi.DOMESTIC_STOCK_TRADE,
                    resolveDomesticRealtimeSymbol(stock));
        }

        throw new RuntimeException("Unsupported realtime stock market type. marketType=" + stock.getMarketType());
    }

    private String resolveDomesticRealtimeSymbol(Stock stock) {
        if (stock.getRealtimeSymbol() != null && !stock.getRealtimeSymbol().isBlank()) {
            return stock.getRealtimeSymbol().trim();
        }

        return stock.getSymbol().trim();
    }

    private String resolveNasdaqRealtimeSymbol(Stock stock) {
        if (stock.getRealtimeSymbol() != null && !stock.getRealtimeSymbol().isBlank()) {
            return stock.getRealtimeSymbol().trim();
        }

        return NASDAQ_REALTIME_PREFIX + stock.getSymbol().trim();
    }

    // Spring 애플리케이션이 종료될 때, 예약된 구독 해제 작업을 모두 취소하고, unsubscribeExecutor를 종료한다.
    @PreDestroy
    public void shutdown() {
        pendingUnsubscribes.values().forEach(future -> future.cancel(false));
        unsubscribeExecutor.shutdownNow();
    }
}
