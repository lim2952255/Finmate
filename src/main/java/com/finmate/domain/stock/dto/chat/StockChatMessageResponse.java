package com.finmate.domain.stock.dto.chat;

import com.finmate.domain.stock.chat.StockChatMessage;

import java.time.LocalDateTime;

// DB에서 조회한 StockChatMessage를 웹소켓을 통해 클라이언트에 보내기 좋은 응답 DTO로 변환한다.
public record StockChatMessageResponse(
        String type,
        Long id,
        Long stockId,
        Long userId,
        String username,
        String content,
        LocalDateTime createdAt,
        Long parentMessageId,
        String replyToUsername,
        String replyToContent,
        boolean replyToDeleted,
        boolean edited,
        boolean deleted
) {
    private static final String MESSAGE_TYPE = "CHAT_MESSAGE";

    public static StockChatMessageResponse from(StockChatMessage message) {
        return new StockChatMessageResponse(
                MESSAGE_TYPE,
                message.getId(),
                message.getStock().getId(),
                message.getUser().getId(),
                message.getUser().getUsername(),
                message.isDeleted() ? null : message.getContent(), // 메세지가 삭제되지 않았으면 메세지를 보내고, 메세지가 삭제되었다면 null을 보낸다.
                message.getCreatedAt(),
                message.getParentMessage() == null ? null : message.getParentMessage().getId(), // 메세지가 답글이라면 ParentMessage 정보도 전달한다.
                message.getParentMessage() == null
                        ? null
                        : message.getParentMessage().getUser().getUsername(),
                message.getParentMessage() == null || message.getParentMessage().isDeleted() // 답글 대상 메세지의 원문을 전달한다.
                        ? null
                        : message.getParentMessage().getContent(),
                message.getParentMessage() != null && message.getParentMessage().isDeleted(), // 답글 대상 메세지가 삭제되었는지 여부를 저장
                message.isEdited(),
                message.isDeleted());
    }
}
