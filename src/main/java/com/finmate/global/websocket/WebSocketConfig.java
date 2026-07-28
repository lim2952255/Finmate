package com.finmate.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// 웹소켓 관련 설정 추가
@Configuration
@EnableWebSocket // Spring에서 웹소켓 기능을 활성화하 에노테이션
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    // 종목의 실시간 시세 데이터를 받는 WebSocketHandler
    private final StockRealtimeWebSocketHandler stockRealtimeWebSocketHandler;
    // 국내 실시간 주가지수 데이터를 받는 WebSocketHandler
    private final MarketRealtimeWebSocketHandler marketRealtimeWebSocketHandler;
    // 종목 상세페이지에서 클라이언트들간의 실시간 채팅을 수행하는 WebSocketHandler
    private final StockChatWebSocketHandler stockChatWebSocketHandler;

    // /ws/stocks 로 들어오는 WebSocket 연결은 stockRealtimeWebSocketHandler가 처리하도록 핸들러 등록
    // 웹 브라우저에서 사용자들이 종목 상세페이지에 들어오면 js를 통해 /ws/stocks로 요청을 보내도록 설정되어 있어 stockRealtimeWebSocketHandler가 호출된다.
    // 각 핸들러들은 클라이언트와 스프링 애플리케이션간의 웹소켓 연결 및 메세지 송수신 처리를 담당한다.
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // /ws/stocks 로 요청이 들어오면 StockRealtimeWebSocketHandler가 실행된다.
        registry.addHandler(stockRealtimeWebSocketHandler, "/ws/stocks");
        // /ws/market-data 로 요청이 들어오면 MarketRealtimeWebSocketHandler가 실행된다.
        registry.addHandler(marketRealtimeWebSocketHandler, "/ws/market-data");
        // /ws/chat으로 요청이 들어오면 StockChatWebSocketHandler가 실행되며, 이때 WebSocket 연결을 위한 Handshake단계에서 HttpSessionHandShakeInterceptor가 호출되도록 등록한다.
        // 이는 HttpSession에 저장된 로그인 사용자 정보를 인터셉터가 Websocket에 전달하고, Websocket session에서 로그인한 사요자 정보를 사용할 수 있게 한다.
        registry.addHandler(stockChatWebSocketHandler, "/ws/chat")
                .addInterceptors(new org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor());
    }
}
