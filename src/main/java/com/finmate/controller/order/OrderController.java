package com.finmate.controller.order;

import com.finmate.domain.stock.dto.trading.StockOrderPageInfo;
import com.finmate.domain.stock.dto.trading.StockOrderRequest;
import com.finmate.domain.stock.dto.trading.StockOrderReservationRequest;
import com.finmate.domain.user.dto.SessionUser;
import com.finmate.global.constant.Const;
import com.finmate.service.stock.trading.StockTradingCommandService;
import com.finmate.service.stock.trading.StockTradingQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/investments/stocks/order")
public class OrderController {
    private final StockTradingCommandService stockTradingCommandService; // 주문을 접수 및 체결하는 서비스
    private final StockTradingQueryService stockTradingQueryService; // 주문 페이지, 포트폴리오, 주문/체결내역 조회용 페이지에 필요한 정보들을 DTO에 담아서 반환하는 서비스

    @GetMapping("/{stockId}")
    public String order(@PathVariable Long stockId,
                        @RequestParam(required = false) Long investmentId,
                        Model model,
                        @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        // StockTradingQueryService를 활용하여 주문 페이지에 필요한 정보들을 DTO에 담아서 model로 전달한다.
        addOrderPageAttributes(sessionUser.getId(), stockId, investmentId, model, new StockOrderRequest(), new StockOrderReservationRequest());
        return "investments/stocks/order";
    }

    // 일반 주문 요청시
    @PostMapping
    public String submitOrder(@ModelAttribute StockOrderRequest stockOrderRequest,
                              Model model,
                              @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        try {
            // StockTradingCommandService를 통해 일반 주문이 들어오면 일반 주문을 접수 및 체결하는 메서드를 호출
            stockTradingCommandService.submitOrder(sessionUser.getId(), stockOrderRequest);
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", e.getMessage());
            addOrderPageAttributes(
                    sessionUser.getId(),
                    stockOrderRequest.getStockId(),
                    stockOrderRequest.getInvestmentId(),
                    model,
                    stockOrderRequest,
                    new StockOrderReservationRequest());
            return "investments/stocks/order";
        }

        return "redirect:/investments/orders?investmentId=" + stockOrderRequest.getInvestmentId();
    }

    // 예약주문 요청시
    @PostMapping("/reservations")
    public String submitReservation(@ModelAttribute StockOrderReservationRequest reservationRequest,
                                    Model model,
                                    @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        try {
            // StockTradingCommandService를 통해 예약 주문이 들어오면 예약 주문을 접수 및 체결하는 메서드를 호출
            stockTradingCommandService.submitReservation(sessionUser.getId(), reservationRequest);
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", e.getMessage());
            addOrderPageAttributes(
                    sessionUser.getId(),
                    reservationRequest.getStockId(),
                    reservationRequest.getInvestmentId(),
                    model,
                    new StockOrderRequest(),
                    reservationRequest);
            return "investments/stocks/order";
        }

        return "redirect:/investments/orders?investmentId=" + reservationRequest.getInvestmentId();
    }

    // 일반 주문 취소 요청이 들어오는 경우
    @PostMapping("/{orderId}/cancel")
    public String cancelOrder(@PathVariable Long orderId,
                              @RequestParam Long investmentId,
                              @RequestParam(required = false, defaultValue = "false") boolean allAccounts,
                              @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        // StockTradingCommandService를 통해 일반 주문을 취소하는 로직을 호출한다.
        stockTradingCommandService.cancelOrder(sessionUser.getId(), orderId);
        if (allAccounts) {
            return "redirect:/investments/orders";
        }
        return "redirect:/investments/orders?investmentId=" + investmentId;
    }
    // 예약 주문 취소 요청이 들어오는 경우
    @PostMapping("/reservations/{reservationId}/cancel")
    public String cancelReservation(@PathVariable Long reservationId,
                                    @RequestParam Long investmentId,
                                    @RequestParam(required = false, defaultValue = "false") boolean allAccounts,
                                    @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        // StockTradingCommandService를 통해 예약 주문을 취소하는 로직을 호출한다.
        stockTradingCommandService.cancelReservation(sessionUser.getId(), reservationId);
        if (allAccounts) {
            return "redirect:/investments/orders";
        }
        return "redirect:/investments/orders?investmentId=" + investmentId;
    }

    private void addOrderPageAttributes(Long userId,
                                        Long stockId,
                                        Long investmentId,
                                        Model model,
                                        StockOrderRequest stockOrderRequest,
                                        StockOrderReservationRequest reservationRequest) {
        StockOrderPageInfo pageInfo = stockTradingQueryService.getOrderPageInfo(userId, stockId, investmentId);
        stockOrderRequest.setStockId(stockId);
        reservationRequest.setStockId(stockId);
        if (stockOrderRequest.getInvestmentId() == null) {
            stockOrderRequest.setInvestmentId(pageInfo.getDefaultInvestmentId());
        }
        if (reservationRequest.getInvestmentId() == null) {
            reservationRequest.setInvestmentId(pageInfo.getDefaultInvestmentId());
        }

        model.addAttribute("stockOrderPageInfo", pageInfo);
        model.addAttribute("stockOrderRequest", stockOrderRequest);
        model.addAttribute("stockOrderReservationRequest", reservationRequest);
    }
}
