package com.finmate.service.realtime;

import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

// 브라우저 WebSocket 세션과 해당 세션이 구독 중인 실시간 key를 함께 관리하는 공통 Registry
// T는 서비스별 구독 단위다. 예: 주식 실시간 구독은 ClientSubscription, 시장지표 실시간 구독은 MarketIndicatorSymbol
// 서비스마다 독립된 Registry 인스턴스를 갖기 위해 Spring Bean이 아니라 일반 클래스로 둔다.
public class RealtimeClientSessionRegistry<T> {
    // 현재 연결되어 있고 아직 close되지 않은 WebSocketSession을 sessionId 기준으로 저장한다.
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 세션별 구독 목록을 저장한다. 한 세션이 여러 종목/지표를 구독할 수 있다.
    private final ConcurrentHashMap<String, Set<T>> subscriptionsBySession = new ConcurrentHashMap<>();

    // KIS realtime key별로 어떤 브라우저 세션들이 해당 데이터를 기다리는지 역방향 인덱스를 저장한다.
    private final ConcurrentHashMap<String, Set<String>> sessionIdsByRealtimeKey = new ConcurrentHashMap<>();

    // WebSocket 연결 직후 세션을 등록하고, 이후 구독 목록을 넣을 빈 Set을 준비한다.
    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
        subscriptionsBySession.computeIfAbsent(session.getId(), ignored -> ConcurrentHashMap.newKeySet());
    }

    // WebSocket 연결 종료 시 세션과 세션의 모든 구독 정보를 제거한다.
    // 제거된 구독 목록은 호출자가 외부 구독 카운터를 감소시키는 데 사용한다.
    public Set<T> unregister(WebSocketSession session, Function<T, String> realtimeKeyResolver) {
        sessions.remove(session.getId());
        Set<T> subscriptions = subscriptionsBySession.remove(session.getId());
        if (subscriptions == null || subscriptions.isEmpty()) {
            return Set.of();
        }

        subscriptions.forEach(subscription ->
                removeSessionFromRealtimeKey(session.getId(), realtimeKeyResolver.apply(subscription)));
        return Set.copyOf(subscriptions);
    }

    // 세션의 구독 Set을 반환한다. 아직 없으면 새로 만든다.
    public Set<T> subscriptions(WebSocketSession session) {
        return subscriptionsBySession.computeIfAbsent(session.getId(), ignored -> ConcurrentHashMap.newKeySet());
    }

    // sessionId 기준으로 현재 구독 목록을 조회한다. 없으면 빈 Set을 반환한다.
    public Set<T> subscriptions(String sessionId) {
        Set<T> subscriptions = subscriptionsBySession.get(sessionId);
        return subscriptions == null ? Set.of() : subscriptions;
    }

    // 세션이 특정 조건의 구독을 이미 갖고 있는지 확인한다. 중복 구독 방지에 사용한다.
    public boolean contains(WebSocketSession session, Predicate<T> predicate) {
        return subscriptions(session.getId()).stream().anyMatch(predicate);
    }

    // 세션의 구독 목록에 구독 단위를 추가하고, realtime key -> sessionId 역방향 인덱스도 함께 갱신한다.
    public void addSubscription(WebSocketSession session, T subscription, String realtimeKey) {
        subscriptions(session).add(subscription);
        sessionIdsByRealtimeKey
                .computeIfAbsent(realtimeKey, ignored -> ConcurrentHashMap.newKeySet())
                .add(session.getId());
    }

    // 단일 구독을 제거한다.
    public void removeSubscription(WebSocketSession session, T subscription, String realtimeKey) {
        removeSubscriptions(session, Set.of(subscription), ignored -> realtimeKey);
    }

    // 여러 구독을 제거한다.
    // 같은 세션이 동일 realtime key를 다른 목적으로도 구독 중이면 역방향 인덱스는 유지한다.
    public void removeSubscriptions(WebSocketSession session,
                                    Collection<T> subscriptions,
                                    Function<T, String> realtimeKeyResolver) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            return;
        }

        Set<T> currentSubscriptions = subscriptionsBySession.get(session.getId());
        if (currentSubscriptions != null) {
            currentSubscriptions.removeAll(subscriptions);
        }

        subscriptions.forEach(subscription -> {
            String realtimeKey = realtimeKeyResolver.apply(subscription);
            if (hasSubscriptionForRealtimeKey(currentSubscriptions, realtimeKeyResolver, realtimeKey)) {
                return;
            }

            removeSessionFromRealtimeKey(session.getId(), realtimeKey);
        });
    }

    // KIS payload가 들어왔을 때 해당 realtime key를 구독 중인 sessionId 목록을 조회한다.
    public Set<String> sessionIds(String realtimeKey) {
        Set<String> sessionIds = sessionIdsByRealtimeKey.get(realtimeKey);
        return sessionIds == null ? Set.of() : Set.copyOf(sessionIds);
    }

    // sessionId로 현재 열려있는 WebSocketSession을 조회한다.
    public Optional<WebSocketSession> session(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            return Optional.empty();
        }

        return Optional.of(session);
    }

    // 세션의 구독 목록 중 조건에 맞는 구독 하나를 찾는다.
    public Optional<T> findSubscription(String sessionId, Predicate<T> predicate) {
        return subscriptions(sessionId).stream()
                .filter(predicate)
                .findFirst();
    }

    // 제거 대상 구독을 뺀 뒤에도 같은 realtime key를 쓰는 구독이 남아있는지 확인한다.
    private boolean hasSubscriptionForRealtimeKey(Set<T> subscriptions,
                                                  Function<T, String> realtimeKeyResolver,
                                                  String realtimeKey) {
        return subscriptions != null
                && subscriptions.stream()
                .anyMatch(subscription -> realtimeKey.equals(realtimeKeyResolver.apply(subscription)));
    }

    // realtime key의 구독 세션 목록에서 특정 세션을 제거하고, 비어 있으면 key 자체도 제거한다.
    private void removeSessionFromRealtimeKey(String sessionId, String realtimeKey) {
        Set<String> sessionIds = sessionIdsByRealtimeKey.get(realtimeKey);
        if (sessionIds == null) {
            return;
        }

        sessionIds.remove(sessionId);
        if (sessionIds.isEmpty()) {
            sessionIdsByRealtimeKey.remove(realtimeKey);
        }
    }
}
