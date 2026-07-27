package com.finmate.service.stock.chat;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.StockSecurityType;
import com.finmate.domain.stock.dto.chat.StockChatHistoryResponse;
import com.finmate.domain.stock.dto.chat.StockChatMessageResponse;
import com.finmate.domain.user.User;
import com.finmate.support.FinancialIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// TestContainer와 연동하고, 스프링 컨테이너를 로드해서 통합테스트를 수행한다.
class StockChatIntegrationTest extends FinancialIntegrationTestSupport {

    @Autowired
    private StockChatService stockChatService;

    private User user;
    private Stock stock;

    // 메 테스트가 수행되기 전에 테스트용 사용자와 Stock 데이터를 준비한다.
    @BeforeEach
    void setUpChatFixture() {
        user = persistUser("채팅사용자");
        stock = persistStock();
    }

    @Test
    @DisplayName("답글과 수정·소프트 삭제 상태를 MySQL에 저장하고 과거 대화로 조회한다")
    void persistsReplyEditAndSoftDelete() {
        // 원본 메세지 등록
        StockChatMessageResponse parent = stockChatService.saveMessage(
                stock.getId(), user.getId(), null, "원래 메시지");
        // 답글 메세지 작성
        StockChatMessageResponse reply = stockChatService.saveMessage(
                stock.getId(), user.getId(), parent.id(), "답글 메시지");

        // 답글 메세지 수정
        StockChatMessageResponse edited = stockChatService.editMessage(
                stock.getId(), reply.id(), user.getId(), "수정한 답글");
        // 원본 메세지 삭제
        StockChatMessageResponse deleted = stockChatService.deleteMessage(
                stock.getId(), parent.id(), user.getId());
        // 대화기록 불러오기
        StockChatHistoryResponse history = stockChatService.getHistory(
                stock.getId(), null, 50);

        // 댓글이 수정 / 삭제가 제대로 수행되었는지를 검사하고, 대화기록도 정상적으로 저장되는지를 검사한다.
        assertThat(edited.edited()).isTrue();
        assertThat(edited.content()).isEqualTo("수정한 답글");
        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.content()).isNull();
        assertThat(history.messages()).hasSize(2);
        assertThat(history.messages().get(0).deleted()).isTrue();
        assertThat(history.messages().get(1).parentMessageId()).isEqualTo(parent.id());
        assertThat(history.messages().get(1).replyToUsername()).isEqualTo(user.getUsername());
        assertThat(history.messages().get(1).replyToDeleted()).isTrue();
        assertThat(history.messages().get(1).replyToContent()).isNull();
        assertThat(history.messages().get(1).content()).isEqualTo("수정한 답글");
    }

    private Stock persistStock() {
        return transactionTemplate.execute(status -> {
            Stock created = Stock.create(
                    "005930",
                    "005930",
                    "KR7005930003",
                    "삼성전자",
                    "Samsung Electronics",
                    StockMarketType.KOSPI,
                    "KR",
                    "KRX",
                    "KRW",
                    StockSecurityType.COMMON_STOCK,
                    false,
                    LocalDate.of(1975, 6, 11),
                    LocalDateTime.now());
            entityManager.persist(created);
            entityManager.flush();
            return created;
        });
    }
}
