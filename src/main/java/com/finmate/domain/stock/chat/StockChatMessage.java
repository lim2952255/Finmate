package com.finmate.domain.stock.chat;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "stock_chat_message"
)
public class StockChatMessage {
    // 한번 채팅에는 총 500문자만 입력이 가능하다.
    public static final int MAX_CONTENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 종목페이지에 작성된 채팅인지를 기록하기 위해 연관관계를 설정한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false, updatable = false)
    private Stock stock;

    // 어떤 사용자가 작성한 채팅인지를 기록하기 위해 연관관계를 설정한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    // 대댓글 기능을 사용하기 위해 자기자신과 연관관계를 설정한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_message_id", updatable = false)
    private StockChatMessage parentMessage;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime editedAt;

    private LocalDateTime deletedAt;

    public static StockChatMessage create(Stock stock, User user, String content) {
        return create(stock, user, null, content);
    }

    public static StockChatMessage create(
            Stock stock,
            User user,
            StockChatMessage parentMessage,
            String content) {
        validateRequired(stock, "종목 정보는 필수입니다.");
        validateRequired(user, "사용자 정보는 필수입니다.");
        validateParentMessage(stock, parentMessage);

        String normalizedContent = normalizeContent(content);
        StockChatMessage message = new StockChatMessage();
        message.stock = stock;
        message.user = user;
        message.parentMessage = parentMessage;
        message.content = normalizedContent;
        return message;
    }

    // 채팅을 수정하며 수정일자를 업데이트한다.
    public void edit(String content) {
        validateActive();
        this.content = normalizeContent(content);
        this.editedAt = LocalDateTime.now();
    }
    // 채팅을 삭제하며 삭제일자를 업데이트한다.
    public void delete() {
        validateActive();
        this.deletedAt = LocalDateTime.now();
    }

    // 수정여부를 기록한다.
    public boolean isEdited() {
        return editedAt != null;
    }

    // 삭제 여부를 결정한다.
    public boolean isDeleted() {
        return deletedAt != null;
    }

    // 대댓글을 작성할때, parentMessage에 댓글이 삭제되지는 않았는지, 다른 종목의 메세지이지는 않는지를 검사한다.
    private static void validateParentMessage(Stock stock, StockChatMessage parentMessage) {
        if (parentMessage == null) {
            return;
        }
        if (parentMessage.isDeleted()) {
            throw new IllegalArgumentException("삭제된 메시지에는 답글을 작성할 수 없습니다.");
        }
        if (parentMessage.getStock() == null
                || parentMessage.getStock().getId() == null
                || !parentMessage.getStock().getId().equals(stock.getId())) {
            throw new IllegalArgumentException("같은 종목의 메시지에만 답글을 작성할 수 있습니다.");
        }
    }

    // 댓글을 수정할떄, 해당 댓글이 삭제되었는지를 검사한다.
    private void validateActive() {
        if (isDeleted()) {
            throw new IllegalArgumentException("삭제된 메시지는 변경할 수 없습니다.");
        }
    }

    // content 정규화
    private static String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("채팅 메시지는 필수입니다.");
        }

        String normalized = content.trim();
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("채팅 메시지는 500자 이하여야 합니다.");
        }
        return normalized;
    }

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
