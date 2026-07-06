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
    private final StockRealtimeWebSocketHandler stockRealtimeWebSocketHandler;

    // /ws/stocks 로 들어오는 WebSocket 연결은 stockRealtimeWebSocketHandler가 처리하도록 핸들러 등록
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(stockRealtimeWebSocketHandler, "/ws/stocks");
    }
}
