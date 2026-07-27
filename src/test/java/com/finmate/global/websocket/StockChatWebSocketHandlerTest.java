package com.finmate.global.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.domain.user.User;
import com.finmate.domain.user.dto.SessionUser;
import com.finmate.global.constant.Const;
import com.finmate.service.stock.chat.StockChatClientSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockChatWebSocketHandlerTest {
    // StockChatClientSessionService는 실제로 기능이 필요하지는 않지만, Handler에 연관관계를 주입하기 위해서 Mock 가짜 객체를 생성한다.
    private final StockChatClientSessionService clientSessionService =
            mock(StockChatClientSessionService.class);
    private final StockChatWebSocketHandler handler =
            new StockChatWebSocketHandler(new ObjectMapper(), clientSessionService);

    @Test
    @DisplayName("HTTP 세션 로그인 정보가 없는 WebSocket 연결은 거부한다")
    void rejectsUnauthenticatedConnection() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        // 세션에서 로그인정보를 조회하면 null이 리턴되도록 설정
        when(session.getAttributes()).thenReturn(Map.of());

        // handler에서 afterConnectionEstablished를 호출하면 내부적으로 세션에서 로그인정보를 조회한다.
        handler.afterConnectionEstablished(session);

        // 세션에서 로그인정보를 조회할때, 로그인정보가 없기 때문에 POLICY_VIOLATION을 기반으로 세션이 닫혀야 한다.
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("로그인 사용자는 채팅 WebSocket 세션으로 등록한다")
    void registersAuthenticatedConnection() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        SessionUser sessionUser = sessionUser(); // 테스트용 세션 유저를 생성한다.
        // 세션에서 로그인사용자를 조회할 수 있도록 설정한다.
        when(session.getAttributes()).thenReturn(Map.of(Const.LOGIN_USER, sessionUser));

        handler.afterConnectionEstablished(session);

        // 이제는 handler.afterConnectionEstablished를 호출해서 로그인 사용자 정보를 조회할 수 있기 때문에 세션이 등록되어야 한다.
        verify(clientSessionService).register(session, sessionUser);
    }

    @Test
    @DisplayName("클라이언트가 보낸 사용자 정보가 아니라 서버 세션과 종목 정보로 메시지를 전송한다")
    void delegatesSendMessage() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);

        // handler가 SEND_MESSAGE를 받았을때 대상 세션에게 메세지를 전송하는지를 검증한다.
        handler.handleTextMessage(session, new TextMessage("""
                {
                  "type": "SEND_MESSAGE",
                  "stockId": 10,
                  "content": "안녕하세요",
                  "userId": 999
                }
                """));

        verify(clientSessionService).sendMessage(session, 10L, null, "안녕하세요");
    }

    @Test
    @DisplayName("답글 대상 메시지 ID를 채팅 서비스에 전달한다")
    void delegatesReplyMessage() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);

        // handler가 답글을 작성한 경우, 대상 세션에게 해당 메세지를 전송하는지를 검증한다.
        handler.handleTextMessage(session, new TextMessage("""
                {
                  "type": "SEND_MESSAGE",
                  "stockId": 10,
                  "parentMessageId": 7,
                  "content": "답글입니다"
                }
                """));

        verify(clientSessionService).sendMessage(session, 10L, 7L, "답글입니다");
    }

    @Test
    @DisplayName("메시지 수정 요청을 채팅 서비스에 전달한다")
    void delegatesEditMessage() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);

        // handler가 EDIT_MESSAGE를 받으면 대상 세션에게 메세지 수정 정보를 전달하는지를 검증한다.
        handler.handleTextMessage(session, new TextMessage("""
                {
                  "type": "EDIT_MESSAGE",
                  "stockId": 10,
                  "messageId": 8,
                  "content": "수정한 내용"
                }
                """));

        verify(clientSessionService).editMessage(session, 10L, 8L, "수정한 내용");
    }

    @Test
    @DisplayName("메시지 삭제 요청을 채팅 서비스에 전달한다")
    void delegatesDeleteMessage() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);

        // handler가 DELETE_MESSAGE를 받으면 대상 세션에게 메세지 삭제 정보를 전달하는지를 검증한다.
        handler.handleTextMessage(session, new TextMessage("""
                {
                  "type": "DELETE_MESSAGE",
                  "stockId": 10,
                  "messageId": 8
                }
                """));

        verify(clientSessionService).deleteMessage(session, 10L, 8L);
    }

    // 테스트용 세션 User를 생성한다.
    private SessionUser sessionUser() {
        User user = new User();
        user.setId(1L);
        user.setUserId("testuser0001");
        user.setUsername("사용자");
        return new SessionUser(user);
    }
}
