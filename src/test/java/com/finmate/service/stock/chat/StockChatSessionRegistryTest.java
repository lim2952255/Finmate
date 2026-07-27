package com.finmate.service.stock.chat;

import com.finmate.domain.user.User;
import com.finmate.domain.user.dto.SessionUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 채팅방 사용자 수, 연결등록, 연결해제 등을 검증한다.
class StockChatSessionRegistryTest {
    private final StockChatSessionRegistry registry = new StockChatSessionRegistry();

    @Test
    @DisplayName("같은 사용자가 여러 탭으로 접속해도 접속자 수는 한 명이다")
    void countsDistinctUsers() {
        SessionUser user = sessionUser(1L, "사용자");
        // 두 소켓을 성한다.
        WebSocketSession first = session("session-1");
        WebSocketSession second = session("session-2");

        // 두 소켓을 생성하고, 두 소켓 모두 동일한 테스트용 사용자에 연결한 다음, 동일 종목 채팅방에 join시킨다.
        registry.register(first, user);
        registry.register(second, user);
        registry.join(first, 10L);
        registry.join(second, 10L);

        // 채팅방 사용자 수는 User_id 기반으로 검사하기 때문에, 동일 사용자가 여러 세션을 통해 중복으로 종목 채팅페이지에 접속하더라도 사용자수는 한명으로 집계한다.
        assertThat(registry.participantCount(10L)).isEqualTo(1);
        assertThat(registry.sessionIds(10L)).containsExactlyInAnyOrder("session-1", "session-2");
    }

    @Test
    @DisplayName("세션이 다른 종목 채팅방으로 이동하면 기존 방에서 제거한다")
    void movesSessionBetweenRooms() {

        WebSocketSession session = session("session-1");
        registry.register(session, sessionUser(1L, "사용자"));

        // 사용자가 10L 채팅방에 있다가 20L 채팅방으로 이동 -> 10L 채팅방은 비어있어야 한다.
        registry.join(session, 10L);
        registry.join(session, 20L);

        assertThat(registry.sessionIds(10L)).isEmpty();
        assertThat(registry.sessionIds(20L)).containsExactly("session-1");
        assertThat(registry.isJoined(session, 20L)).isTrue();
    }

    @Test
    @DisplayName("연결 종료 시 세션과 채팅방 참가 정보를 함께 제거한다")
    void unregistersSession() {
        WebSocketSession session = session("session-1");
        registry.register(session, sessionUser(1L, "사용자"));
        registry.join(session, 10L);

        // 세션 연결 종료
        Long stockId = registry.unregister(session);
        // 세션 연결 종료시에 세션이 속해있던 채팅방에도 연결이 해제되고 세션 정보도 제거된다.
        assertThat(stockId).isEqualTo(10L);
        assertThat(registry.sessionIds(10L)).isEmpty();
        assertThat(registry.user(session)).isEmpty();
    }

    // 웹소켓을 생성한다.
    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of());
        return session;
    }
    // 테스트용 사용자를 생성한다.
    private SessionUser sessionUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUserId("testuser" + id);
        user.setUsername(username);
        return new SessionUser(user);
    }
}
