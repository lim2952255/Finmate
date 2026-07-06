package com.finmate.global.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.service.stock.realtime.StockRealtimeClientSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// 실제 웹소켓 연결을 담당하는 핸들러
// TextWebSocketHandler Spring에서 제공하는 WebSocket 핸들이며, 텍스트메세지 기반 WebSocket을 처리한다.
// 클라이언트 JSON 문자열과 같은 텍스트 메세지를 보내면, 해당 클래스의 handleTextMessage가 호출된다.
@Slf4j
@Component
@RequiredArgsConstructor
public class StockRealtimeWebSocketHandler extends TextWebSocketHandler {
    // 클래스가 보낼 메세지 타입 상수로 정의
    private static final String SUBSCRIBE_STOCK = "SUBSCRIBE_STOCK"; // 종목의 실시간 시세를 구독하는 메세지
    private static final String UNSUBSCRIBE_STOCK = "UNSUBSCRIBE_STOCK"; // 종목의 실시간 시세 구독을 해제하는 메세지

    private final ObjectMapper objectMapper; // Json 문자열을 자바 객체로 변환하는 객체
    private final StockRealtimeClientSessionService clientSessionService; // WebSocket 세션과 구독상태를 관리

    // 웹소켓 처음 연결되었을때 실행되는 메서드
    // WebSocketSession은 클라이언트 한명의 연결 정보를 나타내는 객체
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        clientSessionService.register(session);
    }

    // 클라이언트가 웹소켓을 통해 텍스트메세지를 전송하면 실행되는 메서드
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 클라이언트가 전송한 Json 문자열을 StockRealtimeRequest 객체로 변환한다.
        StockRealtimeRequest request = objectMapper.readValue(message.getPayload(), StockRealtimeRequest.class);

        // 만약 클라이언트가 전송한 메세지가 종목 구독이였다면, 종목을 구독하는 로직 호출
        if (SUBSCRIBE_STOCK.equals(request.type())) {
            clientSessionService.subscribeStock(session, request.stockId());
            return;
        }

        // 만약 클라이언트가 전송한 메세지가 종목 구독해제였다면, 종목 구독을 해제하는 로직 호출
        if (UNSUBSCRIBE_STOCK.equals(request.type())) {
            clientSessionService.unsubscribeStock(session, request.stockId());
            return;
        }

        log.debug("Unknown stock realtime websocket message. sessionId={}, payload={}",
                session.getId(),
                message.getPayload());
    }

    // WebSocket 통신 중 오류가 발생했을때 실행되는 메서드
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Stock realtime websocket transport error. sessionId={}", session.getId(), exception);
        // 오류가 난 세션을 등록목록에서 제거하고, 세션을 종료한다.
        clientSessionService.unregister(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    // WebSocket연결이 정상적으로 종료된 경우에 실행되는 메서드
    // 이 경우에는 해당 세션을 제거한다.
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clientSessionService.unregister(session);
    }

    // 사용자가 보내는 메세지의 형식(1. 종목 구독/구독 취소 요청 2. 종목명)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StockRealtimeRequest(
            String type,
            Long stockId
    ) {
    }
}
