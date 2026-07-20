package com.finmate.service.market;

import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.infra.kis.stock.realtime.KisRealtimeApi;
import com.finmate.infra.kis.stock.realtime.KisRealtimeSubscription;
import com.finmate.infra.kis.stock.realtime.KisRealtimeWebSocketClient;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// 국내 주가지수를 구독중인 클라이언트 수를 Counter로 관리하고, 이에 따라 KisWebSocket에 구독 및 구독해제를 관리하는 메니저
@Service
@RequiredArgsConstructor
public class MarketRealtimeSubscriptionManager {
    private final KisRealtimeWebSocketClient realtimeWebSocketClient; // KisWebSocket과 스프링 애플리케이션간의 웹소켓 연결을 관리하는 클라이언트(실제 KIS WebSocket에 구독 및 구독 해제를 관리하는 클라이언트)
    private final Map<KisRealtimeSubscription, AtomicInteger> subscriberCounts = new ConcurrentHashMap<>(); // 국내 주가지수를 구독중인 클라이언트 수를 관리하는 카운터
    private final Map<KisRealtimeSubscription, ScheduledFuture<?>> pendingUnsubscribes = new ConcurrentHashMap<>(); // 지연중인 구독 해제 작업 목록
    // 구독해제 전용 스레드, 이 스레드는 데몬으로 실행된다.
    private final ScheduledExecutorService unsubscribeExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "kis-market-realtime-unsubscribe");
        thread.setDaemon(true);
        return thread;
    });
    // 구독해제 유예시간. 현재는 counter가 0이여도 다른 클라이언트가 다시 구독할 수 있기 때문에 바로 구독을 해제하는 것이 아니라, 구독해제 유예시간을 설정한다.
    @Value("${finmate.kis.realtime-unsubscribe-grace-millis:60000}")
    private long unsubscribeGraceMillis;

    // 클라이언트 하나가 국내 주가지수를 구독할때 호출되는 메서드
    public KisRealtimeSubscription subscribeIndicator(MarketIndicatorSymbol indicator) {
        KisRealtimeSubscription subscription = domesticIndexSubscription(indicator); // 구독할 종목을 결정한다.
        cancelPendingUnsubscribe(subscription); // 예약되어있던 구독 해제 작업을 취소한다.

        // 국내 주가지수를 구독중인 구독자수를 받아서 1만큼 증가시킨다.
        AtomicInteger counter = subscriberCounts.computeIfAbsent(subscription, ignored -> new AtomicInteger(0));
        counter.incrementAndGet();

        try {
            // RealtimeWebSocketClient를 통해서 국내 주가지수를 구독한다.(이미 Counter가 1이상이라 구독이 되어있는 상태라면 별다른 작업 x)
            realtimeWebSocketClient.subscribe(subscription);
            return subscription;
        } catch (RuntimeException e) {
            // 만약 주가지수를 구독하다가 오류가 발생한 경우, counter값을 감소시키면서 counter가 0이되면 subscriberCounts에서 해당 구독을 제거한다.
            int rolledBackCount = counter.updateAndGet(value -> Math.max(0, value - 1));
            if (rolledBackCount == 0) {
                subscriberCounts.remove(subscription);
            }
            throw e;
        }
    }

    // 클라이언트 하나가 국내 주가지수 구독을 해제할때 호출하는 메서드
    public KisRealtimeSubscription unsubscribeIndicator(MarketIndicatorSymbol indicator) {
        KisRealtimeSubscription subscription = domesticIndexSubscription(indicator);
        // 주가지수를 구독중인 클라이언트 수를 받아서 1만큼 감소시킨다.
        AtomicInteger counter = subscriberCounts.get(subscription);
        if (counter == null) {
            return subscription;
        }

        int subscriberCount = counter.updateAndGet(value -> Math.max(0, value - 1));
        if (subscriberCount == 0) {
            // 만약 counter값이 0이되면 구독 해제 작업을 예약한다.
            scheduleUnsubscribe(subscription);
        }

        return subscription;
    }

    // 구독종목을 KisRealtimeApi에서 꺼내서 KisRealtimeSubscription을 리턴한다.
    private KisRealtimeSubscription domesticIndexSubscription(MarketIndicatorSymbol indicator) {
        if (indicator == null || !indicator.isDomesticRealtimeIndex()) {
            throw new RuntimeException("국내 지수 실시간 구독 대상이 아닙니다. indicator=" + indicator);
        }

        return new KisRealtimeSubscription(KisRealtimeApi.DOMESTIC_INDEX_TRADE, indicator.getKisSymbol());
    }

    // 구독 종목해제를 예약하는 메서드
    private void scheduleUnsubscribe(KisRealtimeSubscription subscription) {
        cancelPendingUnsubscribe(subscription); // 기존 구독해제 예약을 취소한다

        // 구독 종목해제를 담당하는 스레드를 예약
        ScheduledFuture<?> future = unsubscribeExecutor.schedule(() -> {
            AtomicInteger counter = subscriberCounts.get(subscription);
            //스레드가 실행되어도, counter값이 0인지를 다시한번 검증한다.
            if (counter != null && counter.get() <= 0) {
                subscriberCounts.remove(subscription);
                realtimeWebSocketClient.unsubscribe(subscription);
            }
            // 구독해제를 완료하고 나면 예약정보를 제거한다.
            pendingUnsubscribes.remove(subscription);
        }, Math.max(0, unsubscribeGraceMillis), TimeUnit.MILLISECONDS);

        // 예약작업을 pendingUnsubscribes에 저장한다.
        pendingUnsubscribes.put(subscription, future);
    }

    // 구독 종목해제를 취소하는 메서드
    private void cancelPendingUnsubscribe(KisRealtimeSubscription subscription) {
        ScheduledFuture<?> pending = pendingUnsubscribes.remove(subscription);
        if (pending != null) {
            pending.cancel(false);
        }
    }

    // 애플리케이션 종료전에 호출된다.
    @PreDestroy
    public void shutdown() {
        pendingUnsubscribes.values().forEach(future -> future.cancel(false));
        unsubscribeExecutor.shutdownNow();
    }
}
