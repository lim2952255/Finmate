package com.finmate.controller.normal.account;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.dto.cash.InvestmentDepositPageInfo;
import com.finmate.domain.investment.dto.cash.InvestmentDepositRequest;
import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.BankCode;
import com.finmate.domain.normal.account.dto.OpenAccount;
import com.finmate.domain.normal.account.dto.TransferLimitPageInfo;
import com.finmate.domain.normal.account.dto.TransferRequest;
import com.finmate.domain.normal.account.transaction.TransactionPeriod;
import com.finmate.domain.normal.account.transaction.dto.AccountTransactionPageInfo;
import com.finmate.domain.user.User;
import com.finmate.domain.user.dto.SessionUser;
import com.finmate.exception.DuplicatedId;
import com.finmate.global.constant.Const;
import com.finmate.service.investment.InvestmentService;
import com.finmate.service.normal.account.AccountService;
import com.finmate.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final UserService userService;
    private final InvestmentService investmentService;

    @GetMapping("")
    public String accountsHome(Model model,
                               // 세션에서 사용자 정보를 바로 꺼낼 수 있다.
                               @SessionAttribute(name = Const.LOGIN_USER) SessionUser user) {
        // 대표계좌 정보등을 dto에 담아서 view에 전달
        model.addAttribute("accountHomeInfo", accountService.getAccountHomeInfo(user.getId()));
        return "accounts/home";
    }

    @GetMapping("/open")
    public String accountsOpen(Model model){
        model.addAttribute(new OpenAccount());
        // 은행 종류를 담기 위해 BankCode 리스트를 모델에 담아서 전달
        model.addAttribute("bankCodes", BankCode.values());
        model.addAttribute("currencyCodes", CurrencyCode.values());
        return "accounts/open";
    }

    @PostMapping("/open")
    public String accountsOpen(@Valid @ModelAttribute OpenAccount openAccount, BindingResult bindingResult
            ,@SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser
            ,Model model){
        model.addAttribute("bankCodes", BankCode.values());
        model.addAttribute("currencyCodes", CurrencyCode.values());

        if(bindingResult.hasErrors()){
            return "accounts/open";
        }
        try{
            User user = userService.findUser(sessionUser);
            // 이때 user는 Transaction이 끝났기 때문에 준영속 상태이다.
            // 계좌 개설
            accountService.openAccount(openAccount, user);
        } catch(DuplicatedId id){ // 계좌번호 중복
            bindingResult.reject("errorMessage", "계좌번호가 중복되었습니다. 다시 시도해주세요.");
            return "accounts/open";
        } catch(Exception e){
            bindingResult.reject("errorMessage", e.getMessage());
            return "accounts/open";
        }
        return "redirect:/accounts";
    }

    @GetMapping("/list")
    public String accountsLists(Model model
            ,@SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser){
        // 준영속상태에서의 지연로딩 문제를 해결하기 위해 AccountService를 통해 영속 상태에서 accountList를 조회
        // 계좌 목록을 출력
        List<Account> accounts = accountService.findAccounts(sessionUser.getId());
        model.addAttribute("accounts", accounts);
        return "accounts/list";
    }

    @PostMapping("/list")
    public String accountsLists(@RequestParam Long primaryAccountId ,Model model
            ,@SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser){

        try{
            // 대표 계좌 변경
            accountService.setPrimary(primaryAccountId, sessionUser.getId());
        }  catch(Exception e){
            List<Account> accounts = accountService.findAccounts(sessionUser.getId());
            model.addAttribute("accounts", accounts);
            model.addAttribute("errorMessage", e.getMessage());
            return "accounts/list";
        }
        return "redirect:/accounts/list";
    }

    @GetMapping("/transfer")
    public String accountsTransfer(Model model,
                                   @RequestParam(required = false) String from,
                                   @RequestParam(required = false) BankCode fromBankCode,
                                   @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser){
        List<Account> accounts = accountService.findAccounts(sessionUser.getId());
        model.addAttribute("accounts", accounts);
        model.addAttribute("fromAccountNumber", from);

        // 출금할 계좌를 선택하는 페이지로 렌더링
        if(from == null || from.isBlank() || fromBankCode == null)
            return "accounts/transfer";

        TransferRequest transferRequest = accountService.prepareTransfer(sessionUser.getId(), from, fromBankCode);
        model.addAttribute("bankCodes", BankCode.values());
        model.addAttribute("transferRequest", transferRequest);
        return "accounts/transfer-target";
    }

    @PostMapping("/transfer")
    public String accountsTransfer(@Valid @ModelAttribute("transferRequest") TransferRequest transferRequest,
                                   BindingResult bindingResult,
                                   @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser,
                                   Model model){
        User user = userService.findUser(sessionUser);

        if(bindingResult.hasErrors()){
            model.addAttribute("bankCodes", BankCode.values());
            accountService.addTransferRequestDisplayInfo(sessionUser.getId(), transferRequest);
            return "accounts/transfer-target";
        }

        try{
            // 계좌이체 수행
            accountService.transfer(transferRequest, user);
        } catch(Exception e){
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("bankCodes", BankCode.values());
            accountService.addTransferRequestDisplayInfo(sessionUser.getId(), transferRequest);
            return "accounts/transfer-target";
        }

        return "redirect:/accounts";
    }

    @GetMapping("/transfer-investment")
    public String accountsTransferInvestment(Model model,
                                             @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser,
                                             @RequestParam(required = false) String from,
                                             @RequestParam(required = false) BankCode fromBankCode) {
        InvestmentDepositRequest investmentDepositRequest = new InvestmentDepositRequest();
        try {
            investmentDepositRequest = investmentService.prepareInvestmentDeposit(sessionUser.getId(), from, fromBankCode);
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
        }

        InvestmentDepositPageInfo pageInfo = investmentService.getInvestmentDepositPageInfo(
                sessionUser.getId(),
                investmentDepositRequest);
        model.addAttribute("investmentDepositPageInfo", pageInfo);
        model.addAttribute("investmentDepositRequest", pageInfo.getInvestmentDepositRequest());
        return "accounts/transfer-investment";
    }

    @PostMapping("/transfer-investment")
    public String accountsTransferInvestment(@Valid @ModelAttribute("investmentDepositRequest") InvestmentDepositRequest investmentDepositRequest,
                                             BindingResult bindingResult,
                                             Model model,
                                             @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser) {
        if (bindingResult.hasErrors()) {
            InvestmentDepositPageInfo pageInfo = investmentService.getInvestmentDepositPageInfo(
                    sessionUser.getId(),
                    investmentDepositRequest);
            model.addAttribute("investmentDepositPageInfo", pageInfo);
            return "accounts/transfer-investment";
        }

        try {
            investmentService.depositToInvestment(investmentDepositRequest, sessionUser.getId());
        } catch (Exception e) {
            bindingResult.reject("errorMessage", e.getMessage());
            InvestmentDepositPageInfo pageInfo = investmentService.getInvestmentDepositPageInfo(
                    sessionUser.getId(),
                    investmentDepositRequest);
            model.addAttribute("investmentDepositPageInfo", pageInfo);
            return "accounts/transfer-investment";
        }

        return "redirect:/investments";
    }

    @GetMapping("/transfer-limit")
    public String accountsTransferLimit(Model model,
                                         @RequestParam(required = false) String accountNumber,
                                         @RequestParam(required = false) BankCode bankCode,
                                         @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser){
        TransferLimitPageInfo pageInfo = accountService.getTransferLimitPageInfo(
                sessionUser.getId(),
                accountNumber,
                bankCode);
        model.addAttribute("transferLimitPageInfo", pageInfo);
        return "accounts/transfer-limit";
    }

    @PostMapping("/transfer-limit")
    public String accountsTransferLimit(@RequestParam String accountNumber,
                  @RequestParam BankCode bankCode,
                  @RequestParam BigDecimal dailyTransferLimit,
                  @RequestParam BigDecimal singleTransferLimit,
                  Model model,
                  @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser,
                  RedirectAttributes redirectAttributes){
        try {
            // 계좌별 이체한도 변경
            accountService.updateTransferLimit(
                    sessionUser.getId(),
                    accountNumber,
                    bankCode,
                    dailyTransferLimit,
                    singleTransferLimit);
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            TransferLimitPageInfo pageInfo = accountService.getTransferLimitPageInfo(
                    sessionUser.getId(),
                    accountNumber,
                    bankCode);
            model.addAttribute("transferLimitPageInfo", pageInfo);
            return "accounts/transfer-limit";
        }

        redirectAttributes.addAttribute("accountNumber", accountNumber);
        redirectAttributes.addAttribute("bankCode", bankCode);
        return "redirect:/accounts/transfer-limit";
    }

    // 거래내역 표시용
    @GetMapping("/transactions")
    public String accountsTransactions(@RequestParam(required = false) String accountNumber,
                                       @RequestParam(required = false) BankCode bankCode,
                                       @RequestParam(required = false, defaultValue = "ONE_MONTH") TransactionPeriod period,
                                       @RequestParam(required = false, defaultValue = "0") int page,
                                       Model model,
                                       @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser){
        AccountTransactionPageInfo pageInfo = accountService.getAccountTransactionPageInfo(
                sessionUser.getId(),
                accountNumber,
                bankCode,
                period,
                page);
        model.addAttribute("accountTransactionPageInfo", pageInfo);
        return "accounts/transactions";
    }
}
