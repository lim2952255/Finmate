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

    public void send(WebSocketSession session, Object message, String messageName) {
        if (session == null || !session.isOpen()) {
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(message);
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
