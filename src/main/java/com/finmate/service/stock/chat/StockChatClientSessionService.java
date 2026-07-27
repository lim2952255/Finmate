package com.finmate.service.stock.chat;

import com.finmate.domain.stock.dto.chat.StockChatMessageResponse;
import com.finmate.domain.user.dto.SessionUser;
import com.finmate.global.websocket.WebSocketJsonMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

// 웹소켓 채팅 요청을 받아서 세션 관리, DB 작업, 채팅방 전체 전송을 연결하는 중간 서비스이다.
@Slf4j
@Service
@RequiredArgsConstructor
public class StockChatClientSessionService {
        // 서버에서 클라이언트로 전달하는 웹소켓 메세지의 종류
        private static final String CONNECTED = "CONNECTED"; // 웹소켓 연결 완료
        private static final String SUBSCRIBED = "SUBSCRIBED"; // 종목 채팅방 참가 완료
        private static final String UNSUBSCRIBED = "UNSUBSCRIBED"; // 종목 채팅방 퇴장 완료
        private static final String PRESENCE = "PRESENCE"; // 현재 채팅방 접속 인원 갱신
        private static final String ERROR = "ERROR"; // 요청 처리 오류

        private final StockChatService stockChatService; // 대화기록을 조회하고, 채팅을 수정 및 삭제한다.
        private final StockChatSessionRegistry sessionRegistry; // 세션 관리
        private final WebSocketJsonMessageSender messageSender; // 웹소켓으로 Json메세지를 전달하는 객체

        // 웹소켓 연결 등록
        public void register(WebSocketSession session, SessionUser user) {
            sessionRegistry.register(session, user); // 세션과 사용자 정보 저장
            // 클라이언트에게 웹소켓이 연결되었다는 메세지를 전달한다.
            messageSender.send(session, Map.of(
                    "type", CONNECTED,
                    "userId", user.getId(),
                    "username", user.getUsername()), "종목 채팅");
        }

        // 종목 채팅방 입장
        public void joinRoom(WebSocketSession session, Long stockId) {
            try {
                // 해당 종목의 가장 최신 메세지를 조회한다.
                Long latestMessageId = stockChatService.getLatestMessageId(stockId);
                // 세션을 채팅방에 등록한다.
                Long previousStockId = sessionRegistry.join(session, stockId);
                if (previousStockId != null && !previousStockId.equals(stockId)) {
                    // 사용자가 이전 채팅방에서 나오게 되면 사용자수가 update되기 때문에 해당 사용자수 정보를 해당 종목채팅방에 broadcast한다.
                    broadcastPresence(previousStockId);
                }
                // 새로운 종목 페이지에 연결되었음을 메세지로 전달한다.
                messageSender.send(session, nullableMap(
                        "type", SUBSCRIBED,
                        "stockId", stockId,
                        "latestMessageId", latestMessageId,
                        "onlineCount", sessionRegistry.participantCount(stockId)), "종목 채팅");
                // 새로운 채팅방에 속해있는 클라이언트들에게도 update된 사용자수 정보를 전달한다.
                broadcastPresence(stockId);
            } catch (IllegalArgumentException e) {
                sendError(session, e.getMessage());
            }
        }

        // 특정 세션이 특정 종목 채팅방에서 나가는 경우를 처리한다.
        public void leaveRoom(WebSocketSession session, Long stockId) {
            // 만약 해당세션이 해당 종목페이지에 접속상태가 아니라면 바로 return한다.
            if (!sessionRegistry.isJoined(session, stockId)) {
                return;
            }
            // 세션을 종목 채팅방에서 제거한다.
            Long leftStockId = sessionRegistry.leave(session);
            messageSender.send(session, Map.of(
                    "type", UNSUBSCRIBED,
                    "stockId", stockId), "종목 채팅");
            // 트겆ㅇ 세션이 종목 채팅방에 나갔기 때문에 사용자수가 update되며, 해당 사용자수 정보를 broadcast해서 알려준다.
            if (leftStockId != null) {
                broadcastPresence(leftStockId);
            }
        }

        // 클라이언트들이 작성한 채팅을 전송한다.
        public void sendMessage(
                WebSocketSession session,
                Long stockId,
                Long parentMessageId,
                String content) {

            handleMessageChange(session, stockId, userId ->
                    stockChatService.saveMessage(stockId, userId, parentMessageId, content));
        }

        // 클라이언트들이 수정한 채팅을 전달한다.
        public void editMessage(
                WebSocketSession session,
                Long stockId,
                Long messageId,
                String content) {
            handleMessageChange(session, stockId, userId ->
                    stockChatService.editMessage(stockId, messageId, userId, content));
        }

        // 클라이언트들이 삭제한 채팅을 전달한다.
        public void deleteMessage(WebSocketSession session, Long stockId, Long messageId) {
            handleMessageChange(session, stockId, userId ->
                    stockChatService.deleteMessage(stockId, messageId, userId));
        }

        // operation은 채팅을 작성 / 수정 / 삭제한경우에, 이에 맞는 stockChatService의 메서드를 호출한다.
        private void handleMessageChange(
                WebSocketSession session,
                Long stockId,
                java.util.function.Function<Long, StockChatMessageResponse> operation) {
            // 댓글을 작성 / 수정 / 삭제하기 위해서는 해당 종목 채팅방에 접속해있어야 한다.
            if (!sessionRegistry.isJoined(session, stockId)) {
                sendError(session, "채팅방에 먼저 입장해야 합니다.");
                return;
            }
            // 해당 세션과 연결된 사용자 로그인정보를 받는다.
            SessionUser user = sessionRegistry.user(session).orElse(null);
            if (user == null) {
                sendError(session, "로그인 정보가 없습니다.");
                return;
            }

            try {
                // 사용자가 새로 작성 / 수정 / 삭제한 메세지 정보를 StockChatMessageResponse DTO에 담아, 해당 종목채팅방의 사용자들에게 메세지를 broadcast한다.
                StockChatMessageResponse changed = operation.apply(user.getId());
                broadcast(stockId, changed);
            } catch (IllegalArgumentException e) {
                sendError(session, e.getMessage());
            } catch (RuntimeException e) {
                log.warn("종목 채팅 메시지 변경에 실패했습니다. stockId={}, sessionId={}",
                        stockId, session.getId(), e);
                sendError(session, "채팅 메시지를 변경하지 못했습니다.");
            }
        }

        // 특정 세션의 웹소켓 연결을 해제한다.
        public void unregister(WebSocketSession session) {
            Long stockId = sessionRegistry.unregister(session);
            if (stockId != null) {
                // 특정 세션이 웹소켓 연결을 해제하기 때문에, 해당 종목 채팅방의 다른 사용자들에게 사용자수 정보를 broadcast한다.
                broadcastPresence(stockId);
            }
        }

        // 해당 종목 채팅방에 있는 사용자들에게 채팅방 사용자수를 메세지에 담아 전송한다.
        private void broadcast(Long stockId, Object message) {
            sessionRegistry.sessionIds(stockId).forEach(sessionId ->
                    sessionRegistry.session(sessionId)
                            .ifPresent(session -> messageSender.send(session, message, "종목 채팅")));
        }

        // 채팅방 사용자수를 update해서 해당 종목 채팅방에 연결된 클라이언트에게 메세지를 전송한다.
        private void broadcastPresence(Long stockId) {
            broadcast(stockId, Map.of(
                    "type", PRESENCE,
                    "stockId", stockId,
                    "onlineCount", sessionRegistry.participantCount(stockId)));
        }

        // 에러 메세지를 클라이언트에게 전달한다.
        private void sendError(WebSocketSession session, String message) {
            messageSender.send(session, Map.of(
                    "type", ERROR,
                    "message", message == null ? "채팅 요청을 처리할 수 없습니다." : message), "종목 채팅");
        }

        private Map<String, Object> nullableMap(
                String key1, Object value1,
                String key2, Object value2,
                String key3, Object value3,
                String key4, Object value4) {
            Map<String, Object> values = new java.util.LinkedHashMap<>();
            values.put(key1, value1);
            values.put(key2, value2);
            values.put(key3, value3);
            values.put(key4, value4);
            return values;
        }
}
