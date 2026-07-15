package com.finmate.service.stock.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.domain.stock.dto.realtime.RealtimeSubscriptionState;
import com.finmate.domain.stock.dto.realtime.StockRealtimeClientMessage;
import com.finmate.domain.stock.dto.realtime.StockRealtimeOrderbookClientMessage;
import com.finmate.domain.stock.dto.realtime.StockRealtimeOrderbookClientMessage.OrderbookLevel;
import com.finmate.infra.kis.stock.realtime.KisRealtimeApi;
import com.finmate.infra.kis.stock.realtime.KisRealtimePayload;
import com.finmate.infra.kis.stock.realtime.KisRealtimePayloadReceivedEvent;
import com.finmate.infra.kis.stock.realtime.KisRealtimeStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.finmate.infra.kis.parser.KisValueParser.parseNullableBigDecimal;
import static com.finmate.infra.kis.parser.KisValueParser.parseNullableBigDecimalOrNull;
import static com.finmate.infra.kis.parser.KisValueParser.parseNullableLong;

// 스프링서버의 WebSocket과 클라이언트의 연결을 관리하고, KIS 실시간 시세 이벤트가 들어오면, 해당 종목을 구독중인 클라이언트에게 JSON 메세지로 전송하는 중간 관리자
@Slf4j
@Service
@RequiredArgsConstructor
public class StockRealtimeClientSessionService {
    // 스프링 서버에서 클라이언트에게 보내는 메세지 타입
    private static final String STOCK_TRADE_MESSAGE_TYPE = "STOCK_TRADE"; // 실시간 시세 데이터
    private static final String STOCK_ORDERBOOK_MESSAGE_TYPE = "STOCK_ORDERBOOK"; // 실시간 호가 데이터
    private static final String SUBSCRIBED_MESSAGE_TYPE = "SUBSCRIBED"; // 구독 성공 정보
    private static final String UNSUBSCRIBED_MESSAGE_TYPE = "UNSUBSCRIBED"; // 구독 취소 정보
    private static final String ERROR_MESSAGE_TYPE = "ERROR"; // 오류 메세지

    private final StockRealtimeSubscriptionManager subscriptionManager; // 실제 KIS 실시간 구독(종목 구독)을 관리하는 매니저
    private final KisRealtimeStore realtimeStore; // KIS 서버로부터 전달받은 실시간 시세 데이터 저장소
    private final ObjectMapper objectMapper; // 자바 객체를 Json 문자열로 변환하는데 사용되는 객체

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>(); // 현재 서버 WebSocket으로 연결되어 있는 클라이언트들 저장
    private final Map<String, Set<ClientSubscription>> subscriptionsBySession = new ConcurrentHashMap<>(); // 특정 세션이 구독중인 종목목록을 저장하는 Map
    private final Map<String, Set<String>> sessionIdsByRealtimeKey = new ConcurrentHashMap<>(); // 종목 기준으로 해당 종목을 구독중인 세션 목록을 저장하는 Map

    // 클라이언트 WebSocket 연결되었을 때, 세션을 세션보관서에 저장하는 메서드
    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
        // subscriptionsBySession에 새로 들어온 세션을 등록
        subscriptionsBySession.computeIfAbsent(session.getId(), ignored -> ConcurrentHashMap.newKeySet());
    }

    // 클라이언트가 특정 목적의 종목 구독을 요청하는 메서드
    public void subscribeStock(WebSocketSession session, Long stockId, StockRealtimeSubscriptionPurpose purpose) {
        if (stockId == null) {
            // 클라이언트(세션)에게 에러메세지 전송
            sendError(session, "stockId is required.");
            return;
        }
        // 구독 목적 정규화
        StockRealtimeSubscriptionPurpose normalizedPurpose = normalizePurpose(purpose);
        // 현재 세션이 구독중인 목록을 받는다.
        Set<ClientSubscription> sessionSubscriptions =
                subscriptionsBySession.computeIfAbsent(session.getId(), ignored -> ConcurrentHashMap.newKeySet());
        // 현재 세션이 이미 해당 종목을 구독중이고, 현재 구독하고자 하는 목적으로 구독중이였다면 별다른 작업없이 바로 return
        if (sessionSubscriptions.stream()
                .anyMatch(subscription -> subscription.stockId().equals(stockId)
                        && subscription.purpose() == normalizedPurpose)) {
            return;
        }
        // 현재 세션이 아직 해당 종목을 특정 목적으로 구독중인 상태가 아니였다면 구독 수행
        try {
            // subscriptionManager를 통해서 종목을 구독상태로 만든다. 이때 구독목적도 함께 전달한다.(이미 실제 Kis 서버에서는 다른사용자에 의해 이미 종목이 구독상태였을 수 있다. 이 경우 count만 증가)
            List<RealtimeSubscriptionState> states = subscriptionManager.subscribeStock(stockId, normalizedPurpose);
            // client가 구독한 종목에 대한 정보를 담은 record 생성
            List<ClientSubscription> subscriptions = states.stream()
                    .map(state -> new ClientSubscription(stockId, state.api(), state.trKey(), normalizedPurpose))
                    .toList();
            subscriptions.forEach(subscription -> {
                // 현재 세션이 구독중인 구독목록에 새로운 구독정보를 추가
                sessionSubscriptions.add(subscription);
                // 해당 종목을 구독중인 세션 목록에 현재 세션을 등록
                sessionIdsByRealtimeKey
                        .computeIfAbsent(subscription.realtimeKey(), ignored -> ConcurrentHashMap.newKeySet())
                        .add(session.getId());
                // 세션에게 구독 성공 메세지를 전송
                sendStatus(session, SUBSCRIBED_MESSAGE_TYPE, subscription);
                // realtimeStore에서 실시간 종목 데이터를 꺼내서 session에 전달한다.
                realtimeStore.get(subscription.api(), subscription.trKey())
                        .ifPresent(payload -> sendPayload(session, subscription, payload));
            });
        } catch (RuntimeException e) {
            log.warn("실시간 종목 구독에 실패했습니다. sessionId={}, stockId={}", session.getId(), stockId, e);
            sendError(session, "실시간 시세 구독에 실패했습니다.");
        }
    }

    // 클라이언트가 특정 목적의 종목 구독을 해제하는 메서드
    public void unsubscribeStock(WebSocketSession session, Long stockId, StockRealtimeSubscriptionPurpose purpose) {
        if (stockId == null) {
            sendError(session, "stockId is required.");
            return;
        }
        StockRealtimeSubscriptionPurpose normalizedPurpose = normalizePurpose(purpose);
        // 현재 세션이 구독중인 종목 목록을 받는다.
        Set<ClientSubscription> sessionSubscriptions = subscriptionsBySession.get(session.getId());
        if (sessionSubscriptions == null) {
            // 만약 구독중인 종목이 없다면 바로 리턴
            return;
        }
        // 현재 세션이 해당 종목을 구독중인 상태라면, unsubscribe를 호출해서 구독을 취소한다. 이때 동일 종목에 대해 여러 목적으로 구독이 되어있는 경우, 특정 목적의 구독만 해제한다.
        List<ClientSubscription> targetSubscriptions = sessionSubscriptions.stream()
                .filter(subscription -> subscription.stockId().equals(stockId))
                .filter(subscription -> subscription.purpose() == normalizedPurpose)
                .toList();
        if (!targetSubscriptions.isEmpty()) {
            unsubscribe(session, stockId, normalizedPurpose, targetSubscriptions);
        }
    }

    // 현재 세션에 대한 연결을 종료하는 메서드
    public void unregister(WebSocketSession session) {
        // 세션 저장소에서 현재 세션을 제거
        sessions.remove(session.getId());
        // subscriptionsBySession에서 현재 세션을 제거하면서, 현재 세션이 구독중이던 종목 목록을 받는다.
        Set<ClientSubscription> subscriptions = subscriptionsBySession.remove(session.getId());
        if (subscriptions == null || subscriptions.isEmpty()) {
            return;
        }
        // 현재 세션이 구독중이던 종목에 대해서 subscriptionManager를 호출해서 구독을 해제한다.(counter를 감소시킨다)
        subscriptions.forEach(subscription ->
            // sessionIdsByRealtimeKey 특정 종목을 구독하고 있던 session목록에서 현재 세션을 제거한다.
            removeSessionFromRealtimeKey(session.getId(), subscription));
        subscriptions.stream()
                .map(subscription -> new ClientSubscriptionKey(subscription.stockId(), subscription.purpose()))
                .distinct()
                .forEach(key -> subscriptionManager.unsubscribeStock(key.stockId(), key.purpose()));
    }

    // KIS WebSocket에 실시간 시세 데이터가 들어와 Spring 이벤트가 발생했을 때 실행되는 메서드
    // KIS WebSocket으로부터 실시간 데이터를 받으면 KisRealtimeWebSocketClient에서는 이를 RealtimeStore에 저장하고, KisRealtimePayloadReceivedEvent를 발생시킨다.
    // 이후 해당 EventListener가 호출되면서 해당 종목을 구독중인 클라이언트들에게 종목의 실시간 데이터를 전달한다.
    @EventListener
    public void handleKisRealtimePayload(KisRealtimePayloadReceivedEvent event) {
        // 이벤트에서 KIS 실시 payload를 꺼낸다
        KisRealtimePayload payload = event.payload();
        if (payload == null) {
            return;
        }
        // KIS로부터 받은 종목을 구독하고 있는 세션목록을 조회한다.
        Set<String> sessionIds = sessionIdsByRealtimeKey.get(realtimeKey(payload.api(), payload.trKey()));
        if (sessionIds == null || sessionIds.isEmpty()) {
            // 해당 종목을 구독중인 세션이 하나도 없으면 바로 return
            return;
        }
        // 세션 목록을 하나씩 조회하면서,각 세션에 종목 정보(payload)를 전송한다.
        sessionIds.forEach(sessionId -> {
            WebSocketSession session = sessions.get(sessionId);
            if (session == null || !session.isOpen()) {
                return;
            }
            // 해당 세션이 구독하고 있는 종목 목록들을 받는다.
            Set<ClientSubscription> subscriptions = subscriptionsBySession.get(sessionId);
            if (subscriptions == null) {
                return;
            }
            // 해당 세션이 구독하고 있는 종목들중, 현재 이벤트를 통해 전달받은 종목에 해당하는 종목이 실제로 존재하는지를 확인후 payload를 전송한다.
            subscriptions.stream()
                    .filter(subscription -> subscription.api() == payload.api())
                    .filter(subscription -> subscription.trKey().equals(payload.trKey()))
                    .findFirst()
                    .ifPresent(subscription -> sendPayload(session, subscription, payload));
        });
    }

    // 현재 세션에서 특정 종목에 대한 구독을 취소하는 메서드
    private void unsubscribe(WebSocketSession session,
                             Long stockId,
                             StockRealtimeSubscriptionPurpose purpose,
                             List<ClientSubscription> subscriptions) {
        // 현재 세션이 구독하고 있던 종목 목록을 조회
        Set<ClientSubscription> sessionSubscriptions = subscriptionsBySession.get(session.getId());
        if (sessionSubscriptions != null) {
            // 현재 세션이 구독하고 있던 종목 목록에서 종목을 제거
            sessionSubscriptions.removeAll(subscriptions);
        }
        // 제거하고자 하는 종목을 구독하고 있는 세션 목록에서 현재 세션 제거
        subscriptions.forEach(subscription -> removeSessionFromRealtimeKey(session.getId(), subscription));
        // subscriptionManager의 unsubscribeStock을 호출해서 해당 종목에 대한 subscribe Counter를 감소시킨다.
        subscriptionManager.unsubscribeStock(stockId, purpose);
        // 구독 취소가 성공했다는 메세지를 세션에 전송한다.
        subscriptions.forEach(subscription -> sendStatus(session, UNSUBSCRIBED_MESSAGE_TYPE, subscription));
    }

    // 특정 종목을 구독하고 있는 세션 목록을 조회하고, 해당 목록에서 현재 세션을 제거하는 메서드
    private void removeSessionFromRealtimeKey(String sessionId, ClientSubscription subscription) {
        Set<ClientSubscription> currentSubscriptions = subscriptionsBySession.get(sessionId);
        if (currentSubscriptions != null
                && currentSubscriptions.stream().anyMatch(current -> current.realtimeKey().equals(subscription.realtimeKey()))) {
            return;
        }

        Set<String> sessionIds = sessionIdsByRealtimeKey.get(subscription.realtimeKey());
        if (sessionIds == null) {
            return;
        }

        sessionIds.remove(sessionId);
        if (sessionIds.isEmpty()) {
            sessionIdsByRealtimeKey.remove(subscription.realtimeKey());
        }
    }

    // 현재 세션에게 실시간 데이터(payload 데이터)를 Json형태로 전달하는 메서드
    private void sendPayload(WebSocketSession session, ClientSubscription subscription, KisRealtimePayload payload) {
        sendJson(session, toClientMessage(subscription, payload));
    }

    // 세션에게 구독 성공 / 구독 취소와 같은 상태 메세지를 전달하는 메서드
    private void sendStatus(WebSocketSession session, String type, ClientSubscription subscription) {
        sendJson(session, Map.of(
                "type", type,
                "stockId", subscription.stockId(),
                "api", subscription.api(),
                "trKey", subscription.trKey(),
                "purpose", subscription.purpose()));
    }

    // 세션에게 오류를 전달하는 메서드
    private void sendError(WebSocketSession session, String message) {
        sendJson(session, Map.of(
                "type", ERROR_MESSAGE_TYPE,
                "message", message));
    }

    // 세션을 통해 데이터를 전송할때에는 Json문자열 형식으로 전송해야 하기 때문에, 자바 객체를 Json 문자열로 변환하는 작업을 수행해야 한다.
    private void sendJson(WebSocketSession session, Object message) {
        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            // session에 메세지를 전송하기 위해 자바 객체를 Json 문자열로 변환한다.
            String payload = objectMapper.writeValueAsString(message);
            synchronized (session) {
                if (session.isOpen()) {
                    // 세션에 변환된 Json 문자열을 전송한다.
                    session.sendMessage(new TextMessage(payload));
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("실시간 시세 메시지 직렬화에 실패했습니다.", e);
        } catch (IOException e) {
            log.warn("실시간 시세 메시지 전송에 실패했습니다. sessionId={}", session.getId(), e);
        }
    }

    // KIS payload를 클라이언트 DTO로 변환하여 리턴한다.
    private Object toClientMessage(ClientSubscription subscription, KisRealtimePayload payload) {
        if (payload.api().isOrderbook()) {
            return toOrderbookClientMessage(subscription, payload);
        }

        return toTradeClientMessage(subscription, payload);
    }

    // KIS 체결가 payload를 클라이언트 DTO로 변환하여 리턴한다.
    private StockRealtimeClientMessage toTradeClientMessage(ClientSubscription subscription, KisRealtimePayload payload) {
        Map<String, String> values = payload.values();
        BigDecimal currentPrice = parseNullableBigDecimal(payload.price());
        BigDecimal openPrice = parseNullableBigDecimal(value(values, "STCK_OPRC", "OPEN"));
        BigDecimal highPrice = parseNullableBigDecimal(value(values, "STCK_HGPR", "HIGH"));
        BigDecimal lowPrice = parseNullableBigDecimal(value(values, "STCK_LWPR", "LOW"));

        return new StockRealtimeClientMessage(
                STOCK_TRADE_MESSAGE_TYPE,
                subscription.stockId(),
                payload.api(),
                payload.trId(),
                payload.trKey(),
                currentPrice,
                openPrice,
                highPrice,
                lowPrice,
                parseNullableBigDecimal(payload.change()),
                parseNullableBigDecimal(payload.changeRate()),
                value(values, "PRDY_VRSS_SIGN", "SIGN"),
                parseNullableLong(value(values, "ACML_VOL", "TVOL")),
                parseNullableBigDecimal(value(values, "ACML_TR_PBMN", "TAMT")),
                value(values, "BSOP_DATE", "XYMD"),
                payload.tradeTime(),
                candleType(openPrice, currentPrice),
                payload.receivedAt());
    }

    // KIS 호가 payload를 클라이언트 DTO로 변환하여 리턴한다.
    private StockRealtimeOrderbookClientMessage toOrderbookClientMessage(ClientSubscription subscription,
                                                                         KisRealtimePayload payload) {
        Map<String, String> values = payload.values();

        return new StockRealtimeOrderbookClientMessage(
                STOCK_ORDERBOOK_MESSAGE_TYPE,
                subscription.stockId(),
                payload.api(),
                payload.trId(),
                payload.trKey(),
                askLevels(payload.api(), values),
                bidLevels(payload.api(), values),
                totalAskQuantity(payload.api(), values),
                totalBidQuantity(payload.api(), values),
                payload.tradeTime(),
                payload.receivedAt());
    }

    private List<OrderbookLevel> askLevels(KisRealtimeApi api, Map<String, String> values) {
        if (api == KisRealtimeApi.OVERSEAS_STOCK_ORDERBOOK) {
            return orderbookLevels(values, "pask", "vask", 1);
        }

        return orderbookLevels(values, "ASKP", "ASKP_RSQN", 10);
    }

    private List<OrderbookLevel> bidLevels(KisRealtimeApi api, Map<String, String> values) {
        if (api == KisRealtimeApi.OVERSEAS_STOCK_ORDERBOOK) {
            return orderbookLevels(values, "pbid", "vbid", 1);
        }

        return orderbookLevels(values, "BIDP", "BIDP_RSQN", 10);
    }

    private List<OrderbookLevel> orderbookLevels(Map<String, String> values,
                                                 String pricePrefix,
                                                 String quantityPrefix,
                                                 int maxLevel) {
        List<OrderbookLevel> levels = new ArrayList<>();
        for (int level = 1; level <= maxLevel; level++) {
            BigDecimal price = parseNullableBigDecimalOrNull(values.get(pricePrefix + level));
            BigDecimal quantity = parseNullableBigDecimalOrNull(values.get(quantityPrefix + level));
            if (price == null && quantity == null) {
                continue;
            }

            levels.add(new OrderbookLevel(level, price, quantity));
        }

        return levels;
    }

    private BigDecimal totalAskQuantity(KisRealtimeApi api, Map<String, String> values) {
        if (api == KisRealtimeApi.OVERSEAS_STOCK_ORDERBOOK) {
            return parseNullableBigDecimalOrNull(values.get("avol"));
        }

        return parseNullableBigDecimalOrNull(values.get("TOTAL_ASKP_RSQN"));
    }

    private BigDecimal totalBidQuantity(KisRealtimeApi api, Map<String, String> values) {
        if (api == KisRealtimeApi.OVERSEAS_STOCK_ORDERBOOK) {
            return parseNullableBigDecimalOrNull(values.get("bvol"));
        }

        return parseNullableBigDecimalOrNull(values.get("TOTAL_BIDP_RSQN"));
    }

    private String value(Map<String, String> values, String firstKey, String secondKey) {
        String firstValue = values.get(firstKey);
        if (firstValue != null && !firstValue.isBlank()) {
            return firstValue;
        }

        return values.get(secondKey);
    }

    // 시가와 현재가를 비교해 양봉 / 음봉 / 보합을 판단한다.
    private String candleType(BigDecimal openPrice, BigDecimal currentPrice) {
        if (openPrice == null || currentPrice == null) {
            return "flat"; // 보합
        }

        int compare = currentPrice.compareTo(openPrice);
        if (compare > 0) {
            return "bullish"; // 양봉
        }

        if (compare < 0) {
            return "bearish"; // 음봉
        }

        return "flat";
    }
    // RealtimeStore에서 실시간 데이터를 꺼내기 위한 키 설정
    private String realtimeKey(KisRealtimeApi api, String trKey) {
        return api.getTrId() + ":" + trKey;
    }

    // purpose가 없으면 기본값으로 상세페이지 구독으로 설정
    private StockRealtimeSubscriptionPurpose normalizePurpose(StockRealtimeSubscriptionPurpose purpose) {
        return purpose == null ? StockRealtimeSubscriptionPurpose.DETAIL_PAGE : purpose;
    }

    // client가 구독한 종목 정보
    private record ClientSubscription(
            Long stockId,
            KisRealtimeApi api,
            String trKey,
            StockRealtimeSubscriptionPurpose purpose // 클라이언트가 어떤 종목을 어떤 목적으로 구독하였는지
    ) {
        private String realtimeKey() {
            return api.getTrId() + ":" + trKey;
        }
    }

    private record ClientSubscriptionKey(
            Long stockId,
            StockRealtimeSubscriptionPurpose purpose // 구독 목적
    ) {
    }
}
