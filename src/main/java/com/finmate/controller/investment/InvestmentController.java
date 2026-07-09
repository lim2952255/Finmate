package com.finmate.controller.investment;

import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.investment.dto.InvestmentHomeInfo;
import com.finmate.domain.investment.dto.OpenInvestment;
import com.finmate.domain.investment.dto.cash.InvestmentWithdrawalRequest;
import com.finmate.domain.investment.dto.cash.InvestmentWithdrawalPageInfo;
import com.finmate.domain.investment.dto.cash.SecuritiesCashTransactionPageInfo;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.normal.account.transaction.TransactionPeriod;
import com.finmate.domain.stock.dto.trading.StockPortfolioPageInfo;
import com.finmate.domain.stock.dto.trading.StockTradingHistoryPageInfo;
import com.finmate.domain.user.User;
import com.finmate.domain.user.dto.SessionUser;
import com.finmate.global.constant.Const;
import com.finmate.service.investment.InvestmentService;
import com.finmate.service.stock.trading.StockTradingQueryService;
import com.finmate.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final UserService userService;
    private final StockTradingQueryService stockTradingQueryService;

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

    @GetMapping("/portfolio")
    public String portfolio(@RequestParam(required = false) Long investmentId,
                            Model model,
                            @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        StockPortfolioPageInfo pageInfo = stockTradingQueryService.getPortfolioPageInfo(sessionUser.getId(), investmentId);
        model.addAttribute("stockPortfolioPageInfo", pageInfo);
        return "investments/portfolio";
    }

    @GetMapping("/orders")
    public String orders(@RequestParam(required = false) Long investmentId,
                         Model model,
                         @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        StockTradingHistoryPageInfo pageInfo = stockTradingQueryService.getTradingHistoryPageInfo(sessionUser.getId(), investmentId);
        model.addAttribute("stockTradingHistoryPageInfo", pageInfo);
        return "investments/orders";
    }
}
