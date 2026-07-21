package com.finmate.controller.stock;

import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.dto.favorite.FavoriteStockPageInfo;
import com.finmate.domain.stock.dto.detail.StockChartPeriod;
import com.finmate.domain.stock.dto.detail.StockDetailPageInfo;
import com.finmate.domain.stock.dto.ranking.StockMarketMoversPageInfo;
import com.finmate.domain.stock.dto.search.StockSearchPageInfo;
import com.finmate.domain.stock.dto.search.StockSearchType;
import com.finmate.domain.user.dto.SessionUser;
import com.finmate.global.constant.Const;
import com.finmate.service.stock.StockDetailService;
import com.finmate.service.stock.StockService;
import com.finmate.service.stock.ranking.StockMarketMoverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/investments/stocks")
public class StockController {
    private final StockService stockService;
    private final StockDetailService stockDetailService;
    private final StockMarketMoverService stockMarketMoverService;

    @GetMapping("/search")
    public String searchStock(@RequestParam(required = false) String keyword,
                              @RequestParam(required = false, defaultValue = "STOCK") StockSearchType searchType,
                              @RequestParam(required = false) StockMarketType marketType,
                              @RequestParam(required = false, defaultValue = "0") int page,
                              Model model,
                              @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        StockSearchPageInfo pageInfo = stockService.getStockSearchPageInfo(
                sessionUser.getId(),
                keyword,
                searchType, // 검색시 업종 / 종목으로 나눌 수 있다.
                marketType,
                page);
        model.addAttribute("stockSearchPageInfo", pageInfo);
        model.addAttribute("watchlistStockIds", stockService.findFavoriteStockIds(sessionUser.getId()));

        return "investments/stocks/search";
    }

    @PostMapping("/favorite")
    public String toggleFavoriteStock(@RequestParam Long stockId,
                                      @RequestParam String redirectUrl,
                                      @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        stockService.toggleFavoriteStock(sessionUser.getId(), stockId);

        return "redirect:" + redirectUrl;
    }

    @GetMapping("/watchlist")
    public String watchlistStock(@SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser,
                                 @RequestParam(required = false, defaultValue = "0") int page,
                                 Model model) {
        FavoriteStockPageInfo pageInfo = stockService.getFavoriteStockPageInfo(sessionUser.getId(), page);
        model.addAttribute("favoriteStockPageInfo", pageInfo);

        return "investments/stocks/watchlist";
    }

    @GetMapping("/detail")
    public String stockDetail(@RequestParam Long stockId,
                              @RequestParam(required = false, defaultValue = "ONE_YEAR") StockChartPeriod period,
                              Model model) {
        // chart 조회기간 설정
        StockDetailPageInfo pageInfo = stockDetailService.getStockDetailPageInfo(stockId, period);
        model.addAttribute("stockDetailPageInfo", pageInfo);

        return "investments/stocks/detail";
    }

    @GetMapping("/market-movers")
    public String marketMovers(Model model) {
        StockMarketMoversPageInfo pageInfo = stockMarketMoverService.getStockMarketMoversPageInfo();
        model.addAttribute("stockMarketMoversPageInfo", pageInfo);

        return "investments/stocks/market-movers";
    }

    @ResponseBody
    @GetMapping("/market-movers/data")
    public StockMarketMoversPageInfo marketMoversData() {
        return stockMarketMoverService.getStockMarketMoversPageInfo();
    }
}
