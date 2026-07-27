package com.finmate.service.stock.chat;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.chat.StockChatMessage;
import com.finmate.domain.stock.dto.chat.StockChatHistoryResponse;
import com.finmate.domain.user.User;
import com.finmate.repository.stock.StockRepository;
import com.finmate.repository.stock.chat.StockChatMessageRepository;
import com.finmate.repository.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class StockChatServiceTest {
    // Mock 가짜 객체 생성
    private final StockChatMessageRepository messageRepository = mock(StockChatMessageRepository.class);
    private final StockRepository stockRepository = mock(StockRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    // StockChatService에서 댓글 작성,수정,삭제가 정상적으로 처리되는지를 검사한다.
    private final StockChatService service =
            new StockChatService(messageRepository, stockRepository, userRepository);

    @Test
    @DisplayName("과거 대화는 오래된 메시지부터 표시하고 다음 커서를 제공한다")
    void returnsHistoryInDisplayOrder() {
        // 3개의 메세지 생성
        List<StockChatMessage> fetched = List.of(message(5L), message(4L), message(3L));
        // 10L stock의 대화내역을 조회하면 fetched가 리턴되도록 설정
        when(stockRepository.existsById(10L)).thenReturn(true);
        when(messageRepository.findHistory(eq(10L), eq(null), any(Pageable.class)))
                .thenReturn(fetched);

        // history가 fetched와 일치해야 한다.
        // 이때 getHistory에서 requestedSize가 2이기 때문에 5L, 4L이 리턴되고, 다음 커서를 제공한다.
        StockChatHistoryResponse history = service.getHistory(10L, null, 2);

        assertThat(history.messages()).extracting(response -> response.id())
                .containsExactly(4L, 5L);
        assertThat(history.hasNext()).isTrue();
        assertThat(history.nextCursor()).isEqualTo(4L);
    }

    @Test
    @DisplayName("채팅 저장 시 요청 사용자와 종목을 서버에서 조회한다")
    void savesMessageWithServerSideUser() {
        Stock stock = mock(Stock.class);
        User user = mock(User.class);
        when(stock.getId()).thenReturn(10L);
        when(user.getId()).thenReturn(20L);
        when(user.getUsername()).thenReturn("사용자");
        when(stockRepository.findById(10L)).thenReturn(Optional.of(stock));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(messageRepository.save(any(StockChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 채팅을 저장한다.
        var response = service.saveMessage(10L, 20L, null, "  메시지  ");

        // 채팅 저장시에 사용자 정보와 종목정보, 컨텐츠가 저장되는지를 검사한다.
        assertThat(response.stockId()).isEqualTo(10L);
        assertThat(response.userId()).isEqualTo(20L);
        assertThat(response.username()).isEqualTo("사용자");
        assertThat(response.content()).isEqualTo("메시지");
        verify(messageRepository).save(any(StockChatMessage.class));
    }

    @Test
    @DisplayName("존재하지 않는 종목의 대화 기록은 조회하지 않는다")
    void rejectsUnknownStockHistory() {
        when(stockRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.getHistory(999L, null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("종목을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("같은 종목의 메시지를 대상으로 답글을 저장한다")
    void savesReply() {
        Stock stock = mock(Stock.class);
        User user = mock(User.class);
        StockChatMessage parentMessage = message(7L);
        when(stock.getId()).thenReturn(10L);
        when(user.getId()).thenReturn(20L);
        when(user.getUsername()).thenReturn("답글작성자");
        when(stockRepository.findById(10L)).thenReturn(Optional.of(stock));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(messageRepository.findByIdWithRelations(7L)).thenReturn(Optional.of(parentMessage));
        when(messageRepository.save(any(StockChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 답글을 작성하고자 할때, 원본 댓글이 가은 종목의 메세지여야 한다.
        var response = service.saveMessage(10L, 20L, 7L, "답글");

        assertThat(response.parentMessageId()).isEqualTo(7L);
        assertThat(response.replyToUsername()).isEqualTo("user-7");
        assertThat(response.replyToContent()).isEqualTo("message-7");
        assertThat(response.replyToDeleted()).isFalse();
        assertThat(response.content()).isEqualTo("답글");
    }

    @Test
    @DisplayName("작성자는 본인 메시지를 수정할 수 있다")
    void editsOwnedMessage() {
        StockChatMessage message = message(8L);
        when(messageRepository.findByIdWithRelations(8L)).thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);

        // 댓글을 작성한 당사자만 뎃글을 수정할 수 있다.
        service.editMessage(10L, 8L, 8L, "수정");

        verify(message).edit("수정");
        verify(messageRepository).save(message);
    }

    @Test
    @DisplayName("다른 사용자의 메시지는 수정할 수 없다")
    void rejectsEditingAnotherUsersMessage() {
        StockChatMessage message = message(8L);
        when(messageRepository.findByIdWithRelations(8L)).thenReturn(Optional.of(message));

        // 다른사용자가 작성한 메세지를 수정하려고 하면 예외가 발생해야 한다.
        assertThatThrownBy(() -> service.editMessage(10L, 8L, 99L, "수정"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("본인이 작성한 메시지만 변경할 수 있습니다.");
        verify(message, never()).edit(any());
    }

    @Test
    @DisplayName("작성자가 삭제하면 메시지는 소프트 삭제된다")
    void deletesOwnedMessage() {
        StockChatMessage message = message(8L);
        when(message.isDeleted()).thenReturn(true);
        when(messageRepository.findByIdWithRelations(8L)).thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);

        // 작성자가 메세지 삭제
        var response = service.deleteMessage(10L, 8L, 8L);

        // 작성자가 메세지를 삭제하게 되면 해당 메세지는 deleted표시가 되며, content가 사라진다.
        verify(message).delete();
        assertThat(response.deleted()).isTrue();
        assertThat(response.content()).isNull();
    }

    // 테스트용 메세지 정보 생성
    private StockChatMessage message(Long id) {
        StockChatMessage message = mock(StockChatMessage.class);
        Stock stock = mock(Stock.class);
        User user = mock(User.class);
        when(message.getId()).thenReturn(id);
        when(message.getStock()).thenReturn(stock);
        when(message.getUser()).thenReturn(user);
        when(message.getContent()).thenReturn("message-" + id);
        when(message.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 7, 26, 20, id.intValue()));
        when(stock.getId()).thenReturn(10L);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn("user-" + id);
        return message;
    }
}
