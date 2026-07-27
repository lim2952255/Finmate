package com.finmate.service.stock.chat;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.chat.StockChatMessage;
import com.finmate.domain.stock.dto.chat.StockChatHistoryResponse;
import com.finmate.domain.stock.dto.chat.StockChatMessageResponse;
import com.finmate.domain.user.User;
import com.finmate.repository.stock.StockRepository;
import com.finmate.repository.stock.chat.StockChatMessageRepository;
import com.finmate.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockChatService {
    private static final int DEFAULT_HISTORY_SIZE = 50;
    private static final int MAX_HISTORY_SIZE = 100;

    private final StockChatMessageRepository stockChatMessageRepository; // 종목별 사용자 대화기록을 저장한다.
    private final StockRepository stockRepository;
    private final UserRepository userRepository;

    // 특정 종목의 과거메세지를 커서 방식으로 조회한다.
    @Transactional(readOnly = true)
    public StockChatHistoryResponse getHistory(Long stockId, Long beforeId, Integer requestedSize) {
        requireStockExists(stockId); // 종목이 존재하는지를 검사한다.

        int size = normalizeHistorySize(requestedSize); // 조회할 대화기록 크기 설정

        // repository에서 대화기록을 불러온다.
        List<StockChatMessage> fetched = stockChatMessageRepository.findHistory(
                stockId,
                beforeId,
                PageRequest.of(0, size + 1)); // 실제로 repository에서 데이터를 조회할때에는 size+1개를 조회함으로서 다음페이지가 존재하는지를 확인한다.

        // fetched.size() > size라면 다음 페이지가 존재한다는 의미이다.
        boolean hasNext = fetched.size() > size;
        // 실제로 데이터를 사용자에게 보여줄때에는 size만의 데이터만 전달해야 한다. (size+1개를 조회했기 때문에 맨 마지막 1개를 제거한다)
        List<StockChatMessage> page = new ArrayList<>(
                hasNext ? fetched.subList(0, size) : fetched);
        // DB에서는 최신 데이터부터 조회하기 때문에, 사용자들에게 보여줄때에는 오래된 데이터부터 전달하기 위해서 메세지의 순서를 뒤집는다.
        Collections.reverse(page);

        // 엔티티 정보를 StockChatMessageResponse라는 DTO로 변환한다.
        List<StockChatMessageResponse> messages = page.stream()
                .map(StockChatMessageResponse::from)
                .toList();
        // 다음 페이지가 존재한다면, 다음 Cursor id를 등록한다.
        Long nextCursor = hasNext && !page.isEmpty() ? page.get(0).getId() : null;
        return new StockChatHistoryResponse(messages, nextCursor, hasNext);
    }

    // 가장 최신 메세지를 찾는다.
    @Transactional(readOnly = true)
    public Long getLatestMessageId(Long stockId) {
        requireStockExists(stockId);
        return stockChatMessageRepository.findLatestMessageId(stockId).orElse(null);
    }

    // 메세지 작성 / 수정 / 삭제시에는 항상 DB에 우선적으로 메세지를 저장한다음 다른 세션들에 메세지정보를 broadcast한다.
    // 따라서 다른 세션들에게 메세지 정보를 broadcast한 다음에, DB저장에 문제가 생겨 정합성이 깨지는 문제가 발생하지않는다.
    // 사용자가 입력한 메세지를 DB에 저장한 다음, 메시지를 웹소켓에 전달할 형태(StockChatMessageResponse)로 DTO로 변환해서 리턴한다.
    @Transactional
    public StockChatMessageResponse saveMessage(
            Long stockId,
            Long userId,
            Long parentMessageId,
            String content) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("종목을 찾을 수 없습니다."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        StockChatMessage parentMessage = findParentMessage(stockId, parentMessageId);
        StockChatMessage saved = stockChatMessageRepository.save(
                StockChatMessage.create(stock, user, parentMessage, content));
        return StockChatMessageResponse.from(saved);
    }

    // 메세지를 수정해서 DB에 저장한 다음, 메세지를 웹소켓에 전달할 형태(StockChatMessageResponse)로 DTO로 변환해서 리턴한다.
    @Transactional
    public StockChatMessageResponse editMessage(
            Long stockId,
            Long messageId,
            Long userId,
            String content) {
        StockChatMessage message = findOwnedMessage(stockId, messageId, userId);
        message.edit(content);
        return StockChatMessageResponse.from(stockChatMessageRepository.save(message));
    }

    // 메세지를 삭제한 다음, 메세지를 웹소켓에 전달할 형태(StockCahtMessageResponse)로 DTO로 변환해서 리턴한다.
    @Transactional
    public StockChatMessageResponse deleteMessage(Long stockId, Long messageId, Long userId) {
        StockChatMessage message = findOwnedMessage(stockId, messageId, userId);
        message.delete();
        return StockChatMessageResponse.from(stockChatMessageRepository.save(message));
    }

    // 부모 댓글을 찾는다.
    private StockChatMessage findParentMessage(Long stockId, Long parentMessageId) {
        if (parentMessageId == null) {
            return null;
        }
        StockChatMessage parentMessage = stockChatMessageRepository
                .findByIdWithRelations(parentMessageId)
                .orElseThrow(() -> new IllegalArgumentException("답글 대상 메시지를 찾을 수 없습니다."));
        validateMessageStock(parentMessage, stockId);
        return parentMessage;
    }

    // 댓글을 수정 또는 삭제할때 사용자 본인이 작성한 메세지인지를 검사한다.
    private StockChatMessage findOwnedMessage(Long stockId, Long messageId, Long userId) {
        if (messageId == null) {
            throw new IllegalArgumentException("메시지 정보는 필수입니다.");
        }
        StockChatMessage message = stockChatMessageRepository.findByIdWithRelations(messageId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));
        validateMessageStock(message, stockId);
        if (userId == null || !message.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("본인이 작성한 메시지만 변경할 수 있습니다.");
        }
        return message;
    }

    // 종목 채팅인지를 검사한다.
    private void validateMessageStock(StockChatMessage message, Long stockId) {
        if (stockId == null || !message.getStock().getId().equals(stockId)) {
            throw new IllegalArgumentException("해당 종목의 메시지가 아닙니다.");
        }
    }

    // 종목이 존재하는지를 검사한다.
    private void requireStockExists(Long stockId) {
        if (stockId == null || !stockRepository.existsById(stockId)) {
            throw new IllegalArgumentException("종목을 찾을 수 없습니다.");
        }
    }

    private int normalizeHistorySize(Integer requestedSize) {
        if (requestedSize == null) {
            return DEFAULT_HISTORY_SIZE;
        }
        if (requestedSize < 1) {
            throw new IllegalArgumentException("조회 크기는 1 이상이어야 합니다.");
        }
        return Math.min(requestedSize, MAX_HISTORY_SIZE);
    }
}
