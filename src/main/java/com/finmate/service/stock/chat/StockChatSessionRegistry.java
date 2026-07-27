package com.finmate.service.stock.chat;

import com.finmate.domain.user.dto.SessionUser;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// WebSocket session을 관리하고, 사용자수, 종목 채팅방, 접속 인원을 관리한다.
@Component
public class StockChatSessionRegistry {
    // 어떤 WebSocket Session들이 연결되었는지, 어떤 사용자가 어떤 세션을 사용하는지, 각 세션이 어떤 종목 채팅방에 들어가있는지, 각 종목 채팅방에 어떤 세션들이 있는지를 서버메모리에 기록한다.
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>(); // 세션 ID 관리
    private final ConcurrentHashMap<String, SessionUser> usersBySessionId = new ConcurrentHashMap<>(); //각 세션이 어떤 사용자와 연결되어있는지를 관리
    private final ConcurrentHashMap<String, Long> stockIdBySessionId = new ConcurrentHashMap<>(); // 각 세션이 어떤 종목 채팅방에 속해있는지를 관리
    private final ConcurrentHashMap<Long, Set<String>> sessionIdsByStockId = new ConcurrentHashMap<>(); // 각 종목별로 어떤 세션들이 속해있는지를 검사한다.

    // 세션 id와 세션을 연결하고, 세션과 사용자를 연결해서 저장한다.
    public void register(WebSocketSession session, SessionUser user) {
        sessions.put(session.getId(), session);
        usersBySessionId.put(session.getId(), user);
    }

    // 해당 세션을 종목 채팅방에 추가하는 메서드
    public Long join(WebSocketSession session, Long stockId) {
        // 세션이 기존에 참여하고 있었던 채팅방 정보를 조회한다. (어떤 종목의 채팅방에 있었는지)
        Long previousStockId = stockIdBySessionId.put(session.getId(), stockId);
        // 기존 채팅방과 새로운 채팅방이 다르다면, 기존 채팅방에서 해당 세션을 제거한다.
        if (previousStockId != null && !previousStockId.equals(stockId)) {
            removeFromRoom(session.getId(), previousStockId);
        }
        // 새로운 채팅방에 해당 세션을 추가한다.
        sessionIdsByStockId
                .computeIfAbsent(stockId, ignored -> ConcurrentHashMap.newKeySet())
                .add(session.getId());
        return previousStockId;
    }

    // 현재 참가중인 종목 채팅방에서 나가는 메서드이다.
    public Long leave(WebSocketSession session) {
        // 현재 사용자가 종목 채팅방에 나갔으므로 세션 - 종목 정보를 제거한다.
        Long stockId = stockIdBySessionId.remove(session.getId());
        if (stockId != null) {
            // 종목 - 세션 정보에서 현재 세션을 제거한다.
            removeFromRoom(session.getId(), stockId);
        }
        return stockId;
    }

    // 웹소켓 연결이 완전히 종료됐을때 호출하는 메서드이다.
    public Long unregister(WebSocketSession session) {
        // 현재 세션이 속해있는 채팅방에서 나간다.
        Long stockId = leave(session);
        // 세션목록에서 현재 세션을 제거한다.
        sessions.remove(session.getId());
        // 세션과 사용자 정보를 제거한다.
        usersBySessionId.remove(session.getId());
        return stockId;
    }

    // 해당 세션이 요청한 종목 채팅방에 실제로 참가했는지를 검사
    public boolean isJoined(WebSocketSession session, Long stockId) {
        return stockId != null && stockId.equals(stockIdBySessionId.get(session.getId()));
    }

    // 현재 웹소켓 세션의 로그인 사용자 정보를 리턴한다.
    public Optional<SessionUser> user(WebSocketSession session) {
        return Optional.ofNullable(usersBySessionId.get(session.getId()));
    }

    // 세션 ID를 기반으로 실제 웹소켓 연결을 받아온다.
    public Optional<WebSocketSession> session(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    // 특정 종목 채팅방에 속해있는 세션목록을 조회한다.
    public Set<String> sessionIds(Long stockId) {
        Set<String> sessionIds = sessionIdsByStockId.get(stockId);
        return sessionIds == null ? Set.of() : Set.copyOf(sessionIds);
    }

    // 해당 종목 채팅방에 참가한 실제 사용자수를 계산한다.
    public int participantCount(Long stockId) {
        return (int) sessionIds(stockId).stream()
                .map(usersBySessionId::get)
                .filter(user -> user != null)
                .map(SessionUser::getId)
                .distinct()
                .count();
    }

    // 특정 세션을 특정 종목 채팅방에서 제거한다.
    private void removeFromRoom(String sessionId, Long stockId) {
        // 종목재팅방에 속해있는 세션들을 조회한다.
        Set<String> sessionIds = sessionIdsByStockId.get(stockId);
        if (sessionIds == null) {
            return;
        }
        // 세션들에서 현재 세션을 제거한다.
        sessionIds.remove(sessionId);
        if (sessionIds.isEmpty()) {
            sessionIdsByStockId.remove(stockId, sessionIds);
        }
    }
}
