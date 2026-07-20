package com.finmate.service.market;

import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.domain.market.dto.MarketRealtimeErrorMessage;
import com.finmate.domain.market.dto.MarketRealtimeSubscriptionMessage;
import com.finmate.global.websocket.WebSocketJsonMessageSender;
import com.finmate.infra.kis.stock.realtime.KisRealtimeApi;
import com.finmate.infra.kis.stock.realtime.KisRealtimePayload;
import com.finmate.infra.kis.stock.realtime.KisRealtimePayloadReceivedEvent;
import com.finmate.infra.kis.stock.realtime.KisRealtimeSubscription;
import com.finmate.service.realtime.RealtimeClientSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;
import java.util.Set;

// 스프링 애플리케이션 <-> 클라이언 WebSocket 세션 연결을 관리하고, 각 세션 어떤 국내 주가지수를 예약하고 있는지 기록하여, KIS WebSocket에서 받은 실시간 주가지수 데이터 해당 클라이언트에게 전달하는 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketRealtimeClientSessionService {
    private static final String MESSAGE_NAME = "시장지표 실시간";
    private static final String SUBSCRIBED_MESSAGE_TYPE = "SUBSCRIBED"; // 구독 요청 타입
    private static final String UNSUBSCRIBED_MESSAGE_TYPE = "UNSUBSCRIBED"; // 구독 해제 타입

    private final MarketRealtimeSubscriptionManager subscriptionManager; // 국내 주가지수를 구독중인 클라이언트 수를 관리한다.
    private final MarketRealtimeQuoteService marketRealtimeQuoteService; // RealtimeStore 또는 Redis에서 최신 데이터를 꺼내서 반환하는 서비스
    private final WebSocketJsonMessageSender messageSender; // 자바 객체를 Json 문자열로 변환해 WebSocketSession으로 전달하는 객체
    private final RealtimeClientSessionRegistry<MarketIndicatorSymbol> sessionRegistry =
            new RealtimeClientSessionRegistry<>(); // 현재 연결된 클라이언트 세션 / 각 세션별 구독중인 종목 / 지수 등을 관리한다.

    // 클라이언트가 스프링 웹소켓에 연결할 경우, 클라이언트 세션을 sessions에 등록하고 관리한다.
    public void register(WebSocketSession session) {
        sessionRegistry.register(session);
    }

    // 클라이언트가 특정 국내 주가지수를 구독할때 호출된다.
    public void subscribeIndicator(WebSocketSession session, String indicatorName) {
        // 지수 이름을 파싱한다.
        Optional<MarketIndicatorSymbol> indicator = MarketIndicatorSymbol.parse(indicatorName);
        if (indicator.isEmpty()) {
            messageSender.send(session, MarketRealtimeErrorMessage.of("indicator is required."), MESSAGE_NAME);
            return;
        }

        // 파싱한 지수가 국내 주가지수가 아니라면 오류메세지를 전달한다.
        MarketIndicatorSymbol selectedIndicator = indicator.get();
        if (!selectedIndicator.isDomesticRealtimeIndex()) {
            messageSender.send(session, MarketRealtimeErrorMessage.of("국내 지수만 WebSocket 구독을 지원합니다."), MESSAGE_NAME);
            return;
        }

        // 이미 동일한 세션이 동일한 지수에 대해 구독을 수행중이였다면 바로 리턴한다.
        if (sessionRegistry.contains(session, subscription -> subscription == selectedIndicator)) {
            return;
        }

        try {
            // SubscriptionManager를 호출하여 국내 주가지수를 구독한다.
            KisRealtimeSubscription subscription = subscriptionManager.subscribeIndicator(selectedIndicator);
            // SessionRegistry에 세션과 지수에 대한 구독정보를 추가한다.
            sessionRegistry.addSubscription(session, selectedIndicator, subscription.id());
            // 세션에 구독 성공 메세지를 전송한다.
            messageSender.send(
                    session,
                    MarketRealtimeSubscriptionMessage.of(SUBSCRIBED_MESSAGE_TYPE, selectedIndicator, subscription),
                    MESSAGE_NAME);
            // RealtimeStore에 이미 최신값이 있다면 QuoteService를 통해 조회해 세션에 전달한다.
            sendLatestQuote(session, selectedIndicator);
        } catch (RuntimeException e) {
            log.warn("국내 지수 실시간 구독에 실패했습니다. sessionId={}, indicator={}",
                    session.getId(),
                    selectedIndicator,
                    e);
            messageSender.send(session, MarketRealtimeErrorMessage.of("국내 지수 실시간 구독에 실패했습니다."), MESSAGE_NAME);
        }
    }

    // 특정 세션이 지수 구독을 해제할 때 호출되는 메서드
    public void unsubscribeIndicator(WebSocketSession session, String indicatorName) {
        Optional<MarketIndicatorSymbol> indicator = MarketIndicatorSymbol.parse(indicatorName);
        if (indicator.isEmpty()) {
            return;
        }
        // 특정 세션의 특정 종목에 대한 구독을 해제한다.
        unsubscribeIndicator(session, indicator.get(), true);
    }

    // 특정 세션의 WebSocket 연결 자체가 종료된 경우에 호출되는 메서드
    public void unregister(WebSocketSession session) {
        // SessionRegistry에서 세션을 제거한다.
        Set<MarketIndicatorSymbol> subscriptions = sessionRegistry.unregister(session, this::realtimeKey);
        subscriptions.stream()
                // 해당 세션이 구독중이던 종목의 카운터수를 하나씩 감소시킨다.
                .filter(MarketIndicatorSymbol::isDomesticRealtimeIndex)
                .forEach(subscriptionManager::unsubscribeIndicator);
    }

    // Kis WebSocket Client가 Kis WebSocket으로부터 실시간 데이터를 받고 이벤트를 발생시키면 호출되는 이벤트핸들러
    @EventListener
    public void handleKisRealtimePayload(KisRealtimePayloadReceivedEvent event) {
        // Kis Websocket으로부터 실시간 데이터를 받고, 이를 KisRealtimePayload로 변환한다.
        KisRealtimePayload payload = event.payload();
        if (payload == null || payload.api() != KisRealtimeApi.DOMESTIC_INDEX_TRADE) {
            return;
        }

        // 해당 주가지수를 구독중인 세션 목록을 조회한다.
        Set<String> sessionIds = sessionRegistry.sessionIds(KisRealtimeSubscription.id(payload.api(), payload.trKey()));
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        // 해당 주가지수를 구독중인 세션들에게 새로 받은 데이터를 메세지로 전달한다.
        sessionIds.forEach(sessionId ->
            sessionRegistry.session(sessionId)
                    .ifPresent(session -> sessionRegistry.findSubscription(
                                    sessionId,
                                    indicator -> matchesPayload(indicator, payload))
                            // 이벤트로 들어온 payload를 그대로 메세지로 변환해서 전송한다.
                            .ifPresent(indicator -> sendPayload(session, indicator, payload))));
    }

    // 특정 세션의 주가지수에 대한 구독을 해제하는 메서드
    private void unsubscribeIndicator(WebSocketSession session,
                                      MarketIndicatorSymbol indicator,
                                      boolean sendStatus) {
        if (!indicator.isDomesticRealtimeIndex()) {
            return;
        }

        if (!sessionRegistry.contains(session, subscription -> subscription == indicator)) {
            return;
        }

        // 세션에 대한 구독 종목을 해제하고, SessionRegistry를 update한다
        KisRealtimeSubscription subscription = subscriptionManager.unsubscribeIndicator(indicator);
        sessionRegistry.removeSubscription(session, indicator, subscription.id());
        if (sendStatus) {
            messageSender.send(
                    session,
                    MarketRealtimeSubscriptionMessage.of(UNSUBSCRIBED_MESSAGE_TYPE, indicator, subscription),
                    MESSAGE_NAME);
        }
    }

    private boolean matchesPayload(MarketIndicatorSymbol indicator, KisRealtimePayload payload) {
        return indicator.isDomesticRealtimeIndex()
                && indicator.getKisSymbol().equals(payload.trKey());
    }

    private void sendPayload(WebSocketSession session,
                             MarketIndicatorSymbol indicator,
                             KisRealtimePayload payload) {
        messageSender.send(
                session,
                marketRealtimeQuoteService.toDomesticRealtimeMessage(indicator, payload),
                MESSAGE_NAME);
    }

    // RealtimeStore에서 최신 데이터를 꺼내서 데이터를 메세지에 담아서 전송한다.
    private void sendLatestQuote(WebSocketSession session, MarketIndicatorSymbol indicator) {
        marketRealtimeQuoteService.getLatest(indicator)
                .ifPresent(message -> messageSender.send(session, message, MESSAGE_NAME));
    }

    private String realtimeKey(MarketIndicatorSymbol indicator) {
        return KisRealtimeSubscription.id(KisRealtimeApi.DOMESTIC_INDEX_TRADE, indicator.getKisSymbol());
    }
}
