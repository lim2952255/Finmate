package com.finmate.controller.stock;

import com.finmate.domain.stock.dto.chat.StockChatHistoryResponse;
import com.finmate.domain.user.dto.SessionUser;
import com.finmate.global.constant.Const;
import com.finmate.service.stock.chat.StockChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks/{stockId}/chat")
public class StockChatController {
    private final StockChatService stockChatService;

    @GetMapping("/messages")
    public StockChatHistoryResponse getMessages(
            @PathVariable Long stockId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false, defaultValue = "50") Integer size,
            @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        return stockChatService.getHistory(stockId, beforeId, size);
    }
}
