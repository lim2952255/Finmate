package com.finmate.global.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.global.security.FinMateAuthenticatedPrincipal;
import com.finmate.service.stock.chat.StockChatClientSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// /ws/chat으로 들어오는 웹소켓 연결을 처리하고, 사용자 메세지를 받아서 StockChatClientSessionService를 통해 메세지를 저장 및 전송한다.
// TextWebSocketHandler를 상속받기 때문에, 문자열 형태의 웹소켓 메세지를 처리할 수 있다.
@Slf4j
@Component
@RequiredArgsConstructor
public class StockChatWebSocketHandler extends TextWebSocketHandler {
    // 클라이언트가 요청하는 메세지 타입
    private static final String JOIN_ROOM = "JOIN_ROOM"; // 종목 채팅방 입장
    private static final String LEAVE_ROOM = "LEAVE_ROOM"; // 종목 채팅방 퇴장
    private static final String SEND_MESSAGE = "SEND_MESSAGE"; // 메세지 작성
    private static final String EDIT_MESSAGE = "EDIT_MESSAGE"; // 메세지 수정
    private static final String DELETE_MESSAGE = "DELETE_MESSAGE"; //메세지 삭제

    private final ObjectMapper objectMapper; // 자바 객체 -> Json 문자열로 변환
    private final StockChatClientSessionService clientSessionService; // 실제 세션 등록, 채팅방 입/퇴장, 메세지 작성,수정,삭제, 웹소켓 연결종료를 처리한다.

    // 웹소켓 연결이 성공하면 자동으로 호출된다.
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // HTTP session에서 WebSocket handshake로 복사된 Spring Security 인증정보를 받는다.
        Object contextValue = session.getAttributes().get(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
        );
        if (!(contextValue instanceof SecurityContext securityContext)
                || securityContext.getAuthentication() == null
                || !(securityContext.getAuthentication().getPrincipal()
                instanceof FinMateAuthenticatedPrincipal principal)) {
            // 만약 로그인 사용자정보가 맞지않으면 연결을 종료시킨다.(규칙 위반)
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        // 세션과 사용자정보를 서버 메모리에 저장하고, 클라이언트에게 웹소켓 세션에 연결되었다는 메세지를 전송한다.
        clientSessionService.register(session, principal);
    }

    // 웹소켓 연결 후, 클라이언트가 문자열 메세지를 보낼때마다 호출된다.
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            // 클라이언트가 보낸 메세지를 읽고, StockChatRequest DTO로 변환한다.
            StockChatRequest request = objectMapper.readValue(
                    message.getPayload(),
                    StockChatRequest.class);
            // 메세지 타입에 따른 처리
            // 채팅방 입장 처리
            if (JOIN_ROOM.equals(request.type())) {
                clientSessionService.joinRoom(session, request.stockId());
                return;
            }
            // 채팅방 퇴장 처리
            if (LEAVE_ROOM.equals(request.type())) {
                clientSessionService.leaveRoom(session, request.stockId());
                return;
            }
            // 메세지 작성 처리
            if (SEND_MESSAGE.equals(request.type())) {
                clientSessionService.sendMessage(
                        session,
                        request.stockId(),
                        request.parentMessageId(),
                        request.content());
                return;
            }
            // 메세지 수정 처리
            if (EDIT_MESSAGE.equals(request.type())) {
                clientSessionService.editMessage(
                        session,
                        request.stockId(),
                        request.messageId(),
                        request.content());
                return;
            }
            // 메세지 삭제 처리
            if (DELETE_MESSAGE.equals(request.type())) {
                clientSessionService.deleteMessage(
                        session,
                        request.stockId(),
                        request.messageId());
                return;
            }
            log.debug("Unknown stock chat websocket message. sessionId={}, payload={}",
                    session.getId(), message.getPayload());
        } catch (Exception e) {
            log.debug("Invalid stock chat websocket message. sessionId={}, payload={}",
                    session.getId(), message.getPayload(), e);
        }
    }

    // WebSocket 통신 오류발생시 자동으로 호출된다.
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Stock chat websocket transport error. sessionId={}", session.getId(), exception);
        // 웹소켓 연결이 되어있었다면, 해당 세션정보를 제거하고 세션을 닫는다.
        clientSessionService.unregister(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    // 웹소켓 연결이 종료되면 해당 세션 정보를 제거한다.
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clientSessionService.unregister(session);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StockChatRequest(
            String type,
            Long stockId,
            Long messageId,
            Long parentMessageId,
            String content
    ) {
    }
}
