package com.finmate.domain.stock.dto.disclosure;

import java.time.LocalDateTime;
import java.util.List;

// 종목별 최신 시황/공시 목록과 마지막 정상 갱신시각을 묶은 API 응답이다.
public record StockDisclosureResponse(
        Long stockId,
        String stockName,
        LocalDateTime updatedAt, // 목록 갱신 시각
        List<StockDisclosureItem> items // 시황/공시정보 목록
) {
    public StockDisclosureResponse {
        // 호출자가 null 검사 없이 빈 목록을 처리하고 응답 목록을 수정하지 못하게 한다.
        items = items == null ? List.of() : List.copyOf(items);
    }
}
