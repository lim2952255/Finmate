package com.finmate.global.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmate.service.market.MarketRealtimeClientSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;


// 실제 웹소켓 연결을 담당하는 핸들러 (스프링 웹서버 <-> 클라이언트)
// TextWebSocketHandler는 Spring에서 제공하는 WebSocket 핸들이며, 텍스트메세지 기반 WebSocket을 처리한다.
// 클라이언트가 JSON 문자열과 같은 텍스트 메세지를 보내면, 해당 클래스의 handleTextMessage가 호출된다.
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketRealtimeWebSocketHandler extends TextWebSocketHandler {
    // Market 주가지수를 구독하거나 구독해제하는 Indicator
    private static final String SUBSCRIBE_MARKET_INDICATOR = "SUBSCRIBE_MARKET_INDICATOR";
    private static final String UNSUBSCRIBE_MARKET_INDICATOR = "UNSUBSCRIBE_MARKET_INDICATOR";

    private final ObjectMapper objectMapper; // Json 문자열 <-> 자바 객체 매핑
    private final MarketRealtimeClientSessionService clientSessionService; // 실제 애플리케이션 웹소켓에 클라이언트 세션을 등록 및 관리하는 서비스

    // 클라이언트와 애플리케이션간의 웹소켓 연결이 성립된 경우 호출된다. 이때 현재 클라이언트 세션을 등록한다.
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        clientSessionService.register(session);
    }

    // 클라이언트가 애플리케이션으로 JSON 문자열을 보내면 호출된다.
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 클라이언트가 보낸 Json 문자열을 MarketRealtimeRequest 객체로 매핑한다.
        MarketRealtimeRequest request = objectMapper.readValue(message.getPayload(), MarketRealtimeRequest.class);
        // 사용자가 보낸 요청이 구독요청일 경우, 사용자가 요청을 보낸 종목을 구독한다.
        if (SUBSCRIBE_MARKET_INDICATOR.equals(request.type())) {
            clientSessionService.subscribeIndicator(session, request.indicator());
            return;
        }
        // 사용자가 보낸 요청이 구독 해제일 경우, 사용자가 요청을 보낸 종목을 구독 해제한다.
        if (UNSUBSCRIBE_MARKET_INDICATOR.equals(request.type())) {
            clientSessionService.unsubscribeIndicator(session, request.indicator());
            return;
        }

        log.debug("Unknown market realtime websocket message. sessionId={}, payload={}",
                session.getId(),
                message.getPayload());
    }

    // 웹소켓 송수긴간에 오류가 발생한경우, 연결을 해제하고, 클라이언트 세션을 닫는다.
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("Market realtime websocket transport error. sessionId={}", session.getId(), exception);
        clientSessionService.unregister(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }
    // 연결이 종료될경우, ClientSessionService에서 클라이언트 세션을 등록해제한다.
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clientSessionService.unregister(session);
    }

    // 사용자가 보내는 메세지 형태 (구독과 구독취소를 담당)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MarketRealtimeRequest(
            String type, // 구독 or 구독해제
            String indicator // KOSPI or KOSDAQ
    ) {
    }
}
