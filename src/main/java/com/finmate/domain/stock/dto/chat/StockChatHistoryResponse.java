package com.finmate.domain.stock.dto.chat;

import java.util.List;

// DB에서 대화기록을 조회하고, 이를 DTO에 담아서 뷰에 전달한다.
public record StockChatHistoryResponse(
        List<StockChatMessageResponse> messages,
        Long nextCursor, // 커서는 더 이전 대화기록을 받기 위해서 사용한다.
        boolean hasNext // 다음 페이지가 존재하는지를 확인한다.
) {
}
