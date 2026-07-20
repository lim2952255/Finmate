package com.finmate.controller.investment;

import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.investment.dto.InvestmentHomeInfo;
import com.finmate.domain.investment.dto.OpenInvestment;
import com.finmate.domain.investment.dto.exchange.InvestmentCurrencyExchangePageInfo;
import com.finmate.domain.investment.dto.exchange.InvestmentCurrencyExchangeRequest;
import com.finmate.domain.investment.dto.exchange.InvestmentCurrencyExchangeTransactionPageInfo;
import com.finmate.domain.investment.dto.cash.InvestmentWithdrawalRequest;
import com.finmate.domain.investment.dto.cash.InvestmentWithdrawalPageInfo;
import com.finmate.domain.investment.dto.cash.SecuritiesCashTransactionPageInfo;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.domain.market.MarketIndicatorType;
import com.finmate.domain.market.dto.MarketDataChartPeriod;
import com.finmate.domain.market.dto.MarketIndicatorPageInfo;
import com.finmate.domain.market.dto.MarketRealtimeMessage;
import com.finmate.domain.normal.account.transaction.TransactionPeriod;
import com.finmate.domain.stock.dto.trading.StockPortfolioPageInfo;
import com.finmate.domain.stock.dto.trading.StockTradingHistoryPageInfo;
import com.finmate.domain.user.User;
import com.finmate.domain.user.dto.SessionUser;
import com.finmate.global.constant.Const;
import com.finmate.service.investment.InvestmentCurrencyExchangeService;
import com.finmate.service.investment.InvestmentService;
import com.finmate.service.market.MarketDataService;
import com.finmate.service.market.MarketRealtimeQuoteService;
import com.finmate.service.stock.trading.StockTradingQueryService;
import com.finmate.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/investments")
public class InvestmentController {
    private final InvestmentService investmentService;
    private final InvestmentCurrencyExchangeService investmentCurrencyExchangeService;
    private final UserService userService;
    private final StockTradingQueryService stockTradingQueryService;
    private final MarketDataService marketDataService;
    private final MarketRealtimeQuoteService marketRealtimeQuoteService;

    @GetMapping
    public String investmentHome(Model model,
                                 @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser){
        InvestmentHomeInfo investmentHomeInfo = investmentService.getInvestmentHomeInfo(sessionUser.getId());
        model.addAttribute("investmentHomeInfo", investmentHomeInfo);
        return "investments/home";
    }

    @GetMapping("/open")
    public String openInvestment(Model model){
        model.addAttribute(new OpenInvestment());
        model.addAttribute("securitiesCompanyCodes", SecuritiesCompanyCode.values());
        return "investments/accounts/open";
    }

    @PostMapping("/open")
    public String openInvestment(@Valid @ModelAttribute OpenInvestment openInvestment
        , BindingResult bindingResult
        , @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser
        , Model model){
        model.addAttribute("securitiesCompanyCodes", SecuritiesCompanyCode.values());
        if(bindingResult.hasErrors()){
            return "investments/accounts/open";
        }

        try {
            User user = userService.findUser(sessionUser);
            investmentService.openInvestment(openInvestment, user);
        } catch (Exception e) {
            bindingResult.reject("errorMessage", e.getMessage());
            return "investments/accounts/open";
        }

        return "redirect:/investments";
    }

    @GetMapping("/list")
    public String investmentsList(Model model,
                  @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser){
        List<Investment> investments = investmentService.findInvestments(sessionUser.getId());
        model.addAttribute("investments", investments);

        return "investments/accounts/list";
    }

    // 대표 증권계좌 설정
    @PostMapping("/list")
    public String investmentsList(@RequestParam Long primaryInvestmentId,
                                  Model model,
                                  @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        try {
            investmentService.setPrimary(primaryInvestmentId, sessionUser.getId());
        } catch (Exception e) {
            List<Investment> investments = investmentService.findInvestments(sessionUser.getId());
            model.addAttribute("investments", investments);
            model.addAttribute("errorMessage", e.getMessage());
            return "investments/accounts/list";
        }

        return "redirect:/investments/list";
    }

    @GetMapping("/transfer")
    public String securityCashTransfer(Model model,
                                       @RequestParam(required = false) String from,
                                       @RequestParam(required = false) SecuritiesCompanyCode fromSecuritiesCompanyCode,
                                       @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser
    ){
        if (from == null || from.isBlank() || fromSecuritiesCompanyCode == null) {
            InvestmentWithdrawalPageInfo pageInfo = investmentService.getInvestmentWithdrawalPageInfo(sessionUser.getId());
            model.addAttribute("investmentWithdrawalPageInfo", pageInfo);
            return "investments/cash/transfer";
        }

        InvestmentWithdrawalRequest investmentWithdrawalRequest = new InvestmentWithdrawalRequest();
        try {
            investmentWithdrawalRequest = investmentService.prepareInvestmentWithdrawal(
                    sessionUser.getId(),
                    from,
                    fromSecuritiesCompanyCode);
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            InvestmentWithdrawalPageInfo pageInfo = investmentService.getInvestmentWithdrawalPageInfo(sessionUser.getId());
            model.addAttribute("investmentWithdrawalPageInfo", pageInfo);
            return "investments/cash/transfer";
        }

        InvestmentWithdrawalPageInfo pageInfo = investmentService.getInvestmentWithdrawalPageInfo(
                sessionUser.getId(),
                investmentWithdrawalRequest);
        model.addAttribute("investmentWithdrawalPageInfo", pageInfo);
        model.addAttribute("investmentWithdrawalRequest", pageInfo.getInvestmentWithdrawalRequest());
        return "investments/cash/transfer-target";
    }

    @PostMapping("/transfer")
    public String securityCashTransfer(@Valid @ModelAttribute("investmentWithdrawalRequest") InvestmentWithdrawalRequest investmentWithdrawalRequest,
                                       BindingResult bindingResult,
                                       Model model,
                                       @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        if (bindingResult.hasErrors()) {
            InvestmentWithdrawalPageInfo pageInfo = investmentService.getInvestmentWithdrawalPageInfo(
                    sessionUser.getId(),
                    investmentWithdrawalRequest);
            model.addAttribute("investmentWithdrawalPageInfo", pageInfo);
            return "investments/cash/transfer-target";
        }

        try {
            investmentService.withdrawFromInvestment(investmentWithdrawalRequest, sessionUser.getId());
        } catch (Exception e) {
            bindingResult.reject("errorMessage", e.getMessage());
            InvestmentWithdrawalPageInfo pageInfo = investmentService.getInvestmentWithdrawalPageInfo(
                    sessionUser.getId(),
                    investmentWithdrawalRequest);
            model.addAttribute("investmentWithdrawalPageInfo", pageInfo);
            return "investments/cash/transfer-target";
        }

        return "redirect:/investments";
    }

    // 예수금 입출금 내역 출력
    @GetMapping("/securityCashTransaction")
    public String securityCashTransactions(@RequestParam(required = false) String investmentNumber,
                                           @RequestParam(required = false) SecuritiesCompanyCode securitiesCompanyCode,
                                           @RequestParam(required = false, defaultValue = "ONE_MONTH") TransactionPeriod period,
                                           @RequestParam(required = false, defaultValue = "0") int page,
                                           Model model,
                                           @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser){
        SecuritiesCashTransactionPageInfo pageInfo = investmentService.getSecuritiesCashTransactionPageInfo(
                sessionUser.getId(),
                investmentNumber,
                securitiesCompanyCode,
                period,
                page);
        model.addAttribute("securitiesCashTransactionPageInfo", pageInfo);
        return "investments/cash/transactions";
    }

    // 통화 환전 페이지 이동
    @GetMapping("/currency-exchange")
    public String currencyExchange(@RequestParam(required = false) String investmentNumber,
                                   @RequestParam(required = false) SecuritiesCompanyCode securitiesCompanyCode,
                                   Model model,
                                   @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        InvestmentCurrencyExchangeRequest request = new InvestmentCurrencyExchangeRequest();

        // 사용자가 특정 증권계좌를 선택한 경우, 해당 정보를 InvestmentCurrencyExchangeRequest에 담는다.
        if (investmentNumber != null && !investmentNumber.isBlank() && securitiesCompanyCode != null) {
            try {
                request = investmentCurrencyExchangeService.prepareCurrencyExchange(
                        sessionUser.getId(),
                        investmentNumber,
                        securitiesCompanyCode);
            } catch (Exception e) {
                model.addAttribute("errorMessage", e.getMessage());
            }
        }

        addCurrencyExchangeModel(sessionUser.getId(), request, model);
        return "investments/cash/exchange";
    }

    // 통화 환전 기능 처리
    @PostMapping("/currency-exchange")
    public String currencyExchange(@Valid @ModelAttribute("investmentCurrencyExchangeRequest") InvestmentCurrencyExchangeRequest request,
                                   BindingResult bindingResult,
                                   Model model,
                                   @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        if (bindingResult.hasErrors()) {
            addCurrencyExchangeModel(sessionUser.getId(), request, model);
            return "investments/cash/exchange";
        }

        try {
            // 환전 처리
            investmentCurrencyExchangeService.exchangeCurrency(sessionUser.getId(), request);
        } catch (Exception e) {
            bindingResult.reject("errorMessage", e.getMessage());
            addCurrencyExchangeModel(sessionUser.getId(), request, model);
            return "investments/cash/exchange";
        }

        return "redirect:/investments/currency-exchange/transactions?investmentId=" + request.getInvestmentId();
    }

    // 통화 환전 내역 출력 페이지 이동
    @GetMapping("/currency-exchange/transactions")
    public String currencyExchangeTransactions(@RequestParam(required = false) Long investmentId,
                                               @RequestParam(required = false, defaultValue = "ONE_MONTH") TransactionPeriod period,
                                               @RequestParam(required = false, defaultValue = "0") int page,
                                               Model model,
                                               @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        InvestmentCurrencyExchangeTransactionPageInfo pageInfo =
                investmentCurrencyExchangeService.getCurrencyExchangeTransactionPageInfo(
                        sessionUser.getId(),
                        investmentId,
                        period,
                        page);
        model.addAttribute("investmentCurrencyExchangeTransactionPageInfo", pageInfo);
        return "investments/cash/exchange-transactions";
    }

    // 사용자의 포트폴리오, 또는 특정 증권계좌의 포트폴리오 출력
    @GetMapping("/portfolio")
    public String portfolio(@RequestParam(required = false) Long investmentId,
                            Model model,
                            @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        StockPortfolioPageInfo pageInfo = stockTradingQueryService.getPortfolioPageInfo(sessionUser.getId(), investmentId);
        model.addAttribute("stockPortfolioPageInfo", pageInfo);
        return "investments/portfolio";
    }

    // 주문 페이지 출력
    @GetMapping("/orders")
    public String orders(@RequestParam(required = false) Long investmentId,
                         Model model,
                         @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        StockTradingHistoryPageInfo pageInfo = stockTradingQueryService.getTradingHistoryPageInfo(sessionUser.getId(), investmentId);
        model.addAttribute("stockTradingHistoryPageInfo", pageInfo);
        return "investments/orders";
    }

    // 실시간 환율/지수 정보 메뉴
    @GetMapping("/market-data")
    public String marketData() {
        return "investments/market-data/index";
    }

    // 실시간 환율정보 출력
    @GetMapping("/exchanges")
    public String exchanges(@RequestParam(required = false) MarketIndicatorSymbol indicator,
                            @RequestParam(required = false, defaultValue = "ONE_YEAR") MarketDataChartPeriod period,
                            Model model) {
        MarketIndicatorPageInfo pageInfo = marketDataService.getMarketIndicatorPageInfo(
                MarketIndicatorType.EXCHANGE_RATE,
                indicator,
                period);
        addMarketDataDetailModel(model, pageInfo, "실시간 환율", "/investments/exchanges");
        return "investments/market-data/detail";
    }

    // 실시간 주가지수 시세 출력
    @GetMapping("/indices")
    public String indices(@RequestParam(required = false) MarketIndicatorSymbol indicator,
                          @RequestParam(required = false, defaultValue = "ONE_YEAR") MarketDataChartPeriod period,
                          Model model) {
        MarketIndicatorPageInfo pageInfo = marketDataService.getMarketIndicatorPageInfo(
                MarketIndicatorType.STOCK_INDEX,
                indicator,
                period);
        addMarketDataDetailModel(model, pageInfo, "실시간 지수 시세", "/investments/indices");
        return "investments/market-data/detail";
    }

    @ResponseBody
    @GetMapping("/market-data/realtime")
    public ResponseEntity<MarketRealtimeMessage> marketDataRealtime(@RequestParam MarketIndicatorSymbol indicator) {
        return marketRealtimeQuoteService.getLatest(indicator)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private void addMarketDataDetailModel(Model model,
                                          MarketIndicatorPageInfo pageInfo,
                                          String pageTitle,
                                          String actionPath) {
        model.addAttribute("marketIndicatorPageInfo", pageInfo);
        model.addAttribute("marketDataPeriods", MarketDataChartPeriod.values());
        model.addAttribute("marketDataPageTitle", pageTitle);
        model.addAttribute("marketDataActionPath", actionPath);
        model.addAttribute("marketRealtimeMode", pageInfo.selectedIndicator().getRealtimeMode());
    }

    private void addCurrencyExchangeModel(Long userId,
                                          InvestmentCurrencyExchangeRequest request,
                                          Model model) {
        InvestmentCurrencyExchangePageInfo pageInfo =
                investmentCurrencyExchangeService.getCurrencyExchangePageInfo(userId, request);
        model.addAttribute("investmentCurrencyExchangePageInfo", pageInfo);
        model.addAttribute("investmentCurrencyExchangeRequest", pageInfo.getInvestmentCurrencyExchangeRequest());
    }
}
