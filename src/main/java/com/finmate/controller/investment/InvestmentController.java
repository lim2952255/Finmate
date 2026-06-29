package com.finmate.controller.investment;

import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.investment.dto.OpenInvestment;
import com.finmate.domain.investment.dto.PrimaryInvestment;
import com.finmate.domain.user.User;
import com.finmate.domain.user.dto.SessionUser;
import com.finmate.global.Const;
import com.finmate.service.investment.InvestmentService;
import com.finmate.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/investments")
public class InvestmentController {
    private final InvestmentService investmentService;
    private final UserService userService;

    @GetMapping
    public String investmentHome(Model model,
                                 @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser){
        List<Investment> investments = investmentService.findInvestments(sessionUser.getId());
        BigDecimal totalDepositBalance = investments.stream()
                .map(Investment::getDepositBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PrimaryInvestment primaryInvestment = investmentService.getPrimaryInvestment(sessionUser).orElse(null);

        model.addAttribute("investments", investments);
        model.addAttribute("primaryInvestment", primaryInvestment);
        model.addAttribute("totalDepositBalance", totalDepositBalance);
        model.addAttribute("investmentAccountNumbers", investments.size());

        return "investments/home";
    }

    @GetMapping("/open")
    public String openInvestment(Model model){
        model.addAttribute(new OpenInvestment());
        model.addAttribute("securitiesCompanyCodes", SecuritiesCompanyCode.values());
        return "investments/open";
    }

    @PostMapping("/open")
    public String openInvestment(@Valid @ModelAttribute OpenInvestment openInvestment
        , BindingResult bindingResult
        , @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser
        , Model model){
        model.addAttribute("securitiesCompanyCodes", SecuritiesCompanyCode.values());
        if(bindingResult.hasErrors()){
            return "investments/open";
        }

        try {
            User user = userService.findUser(sessionUser);
            investmentService.openInvestment(openInvestment, user);
        } catch (Exception e) {
            bindingResult.reject("errorMessage", e.getMessage());
            return "investments/open";
        }

        return "redirect:/investments";
    }

    @GetMapping("/list")
    public String investmentsList(Model model,
                  @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser){
        List<Investment> investments = investmentService.findInvestments(sessionUser.getId());
        model.addAttribute("investments", investments);

        return "investments/list";
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
            return "investments/list";
        }

        return "redirect:/investments/list";
    }
}
