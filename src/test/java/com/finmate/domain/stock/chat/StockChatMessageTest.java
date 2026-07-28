package com.finmate.domain.stock.chat;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockChatMessageTest {
    // Stock과 User는 실제 모든 기능이 필요하진 않기 때문에 Mock으로 생성한다.
    private final Stock stock = mock(Stock.class);
    private final User user = mock(User.class);

    // 채팅메세지가 정규화되는지를 검사한다.
    @Test
    @DisplayName("채팅 메시지는 앞뒤 공백을 제거해 생성한다")
    void createsNormalizedMessage() {
        StockChatMessage message = StockChatMessage.create(stock, user, "  안녕하세요  ");

        assertThat(message.getContent()).isEqualTo("안녕하세요");
        assertThat(message.getStock()).isSameAs(stock);
        assertThat(message.getUser()).isSameAs(user);
    }

    // 빈 채팅메세지는 거부한다.
    @Test
    @DisplayName("빈 채팅 메시지는 거부한다")
    void rejectsBlankContent() {
        assertThatThrownBy(() -> StockChatMessage.create(stock, user, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("채팅 메시지는 필수입니다.");
    }

    // 채팅메세지는 500자 이하여야 한다.
    @Test
    @DisplayName("500자를 초과하는 채팅 메시지는 거부한다")
    void rejectsTooLongContent() {
        // 가를 501자만큼 작성
        String content = "가".repeat(StockChatMessage.MAX_CONTENT_LENGTH + 1);

        // 채팅 메세지가 500자를 초과하면 예외가 발생한다.
        assertThatThrownBy(() -> StockChatMessage.create(stock, user, content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("채팅 메시지는 500자 이하여야 합니다.");
    }

    @Test
    @DisplayName("같은 종목의 메시지를 답글 대상으로 지정할 수 있다")
    void createsReply() {
        when(stock.getId()).thenReturn(1L);
        // 같은 종목의 댓글에 대해서만 답글을 작성할 수 있다.
        StockChatMessage parentMessage = StockChatMessage.create(stock, user, "원문");

        StockChatMessage reply = StockChatMessage.create(stock, user, parentMessage, "답글");

        assertThat(reply.getParentMessage()).isSameAs(parentMessage);
        assertThat(reply.getContent()).isEqualTo("답글");
    }

    @Test
    @DisplayName("메시지를 수정하면 수정 상태가 기록된다")
    void editsMessage() {
        // 댓글이 정상적으로 수정되는지를 검사한다.
        StockChatMessage message = StockChatMessage.create(stock, user, "수정 전");

        message.edit("  수정 후  ");

        assertThat(message.getContent()).isEqualTo("수정 후");
        assertThat(message.isEdited()).isTrue();
    }

    // 삭제된 메세지에 대해서는 댓글 수정 및 답글을 작성할 수 없다.
    @Test
    @DisplayName("삭제된 메시지는 내용 수정과 답글 작성을 거부한다")
    void rejectsChangesAfterDeletion() {
        when(stock.getId()).thenReturn(1L);
        StockChatMessage message = StockChatMessage.create(stock, user, "삭제할 메시지");
        // 메세지를 삭제한다.
        message.delete();

        // 메세지가 정상적으로 삭제되는지를 검사한다.
        assertThat(message.isDeleted()).isTrue();
        // 삭제된 메세지를 수정 또는 답글을 작성하려고 하면 예외가 발생해야 한다.
        assertThatThrownBy(() -> message.edit("수정"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("삭제된 메시지는 변경할 수 없습니다.");
        assertThatThrownBy(() -> StockChatMessage.create(stock, user, message, "답글"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("삭제된 메시지에는 답글을 작성할 수 없습니다.");
    }
}
