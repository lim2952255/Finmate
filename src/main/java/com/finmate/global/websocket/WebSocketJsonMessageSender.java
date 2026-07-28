package com.finmate.global.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketJsonMessageSender {
    private final ObjectMapper objectMapper;

    // 특정 웹소켓 세션에 JSON 메세지를 전달하는 메서드
    public void send(WebSocketSession session, Object message, String messageName) {
        // 메세지를 전송할 세션이 존재해야 한다.
        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            // ObjectMapper를 통해 세션에 전송할 메세지를 Json 문자열로 변환한다.
            String payload = objectMapper.writeValueAsString(message);
            // 세션에 메세지를 동시에 전송하면 안되기 때문에, synchronized를 통해 메세지를 동기화해서 전송한다.
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload));
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(messageName + " 메시지 직렬화에 실패했습니다.", e);
        } catch (IOException e) {
            log.warn("{} 메시지 전송에 실패했습니다. sessionId={}", messageName, session.getId(), e);
        }
    }
}
