package com.finmate.controller.order;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// 주문 화면의 최초 문서 요청만 React 진입 문서로 전달한다.
@Controller
@RequestMapping("/investments/stocks/order")
public class OrderController {

    @GetMapping("/{stockId}")
    public String order() {
        return "forward:/react/index.html";
    }
}
