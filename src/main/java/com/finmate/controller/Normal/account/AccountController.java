package com.finmate.controller.Normal.account;

import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.BankCode;
import com.finmate.domain.normal.account.dto.OpenAccount;
import com.finmate.domain.normal.account.dto.PrimaryAccount;
import com.finmate.domain.normal.account.dto.TransferRequest;
import com.finmate.domain.normal.accountTransaction.AccountTransaction;
import com.finmate.domain.normal.accountTransaction.TransactionPeriod;
import com.finmate.domain.normal.accountTransaction.dto.TransactionSummary;
import com.finmate.domain.user.User;
import com.finmate.domain.user.dto.SessionUser;
import com.finmate.exception.DuplicatedId;
import com.finmate.global.Const;
import com.finmate.service.normal.account.AccountService;
import com.finmate.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final UserService userService;

    @GetMapping("")
    public String accountsHome(Model model,
                               // 세션에서 사용자 정보를 바로 꺼낼 수 있다.
                               @SessionAttribute(name = Const.LOGIN_USER) SessionUser user) {
        List<Account> accounts = accountService.findAccounts(user.getId());
        BigDecimal totalBalance = accounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PrimaryAccount primaryAccount = accountService.getPrimaryAccount(user).orElse(null);
        model.addAttribute("primaryAccount", primaryAccount);
        model.addAttribute("totalBalance", totalBalance);
        model.addAttribute("accountNumbers",accounts.size());
        return "accounts/home";
    }

    @GetMapping("/open")
    public String accountsOpen(Model model){
        model.addAttribute(new OpenAccount());
        // Thymeleaf template에서 은행들의 리스트를 받기 위해 model에 담아준다
        model.addAttribute("bankCodes", BankCode.values());
        return "accounts/open";
    }

    @PostMapping("/open")
    public String accountsOpen(@Valid @ModelAttribute OpenAccount openAccount, BindingResult bindingResult
            ,@SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser
            ,Model model){
        model.addAttribute("bankCodes", BankCode.values());

        if(bindingResult.hasErrors()){
            return "accounts/open";
        }
        try{
            User user = userService.findUser(sessionUser);
            // 이때 user는 Transaction이 끝났기 때문에 준영속 상태이다.
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

        // User가 준영속상태이기 때문에 user.getAccountList()호출 시 준영속 상태에서의 지연로딩 문제가 발생할 수 있다.
        // 현재는 Spring OSIV덕분에 영속성 컨텍스트의 생존주기가 view 영역까지 확장되었기 때문에 준영속 상태에서의 지연로딩 문제가 발생하지 않는다.
        // 다만 Spring OSIV를 사용하면 영속성컨텍스트를 오래잡기 때문에 성능이 하락할 수 있고, view영역에서 N+1문제가 발생할 수 있기 대문에 좋은 구조가 아니다.

        // User user = userService.findUser(sessionUser);
        // model.addAttribute("accounts", user.getAccountList());

        // 준영속상태에서의 지연로딩 문제를 해결하기 위해 AccountService를 통해 영속성상태에서 accountList를 조회
        List<Account> accounts = accountService.findAccounts(sessionUser.getId());
        model.addAttribute("accounts", accounts);
        return "accounts/list";
    }

    @PostMapping("/list")
    public String accountsLists(@RequestParam Long primaryAccountId ,Model model
            ,@SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser){

        try{
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

        if(from == null || from.isBlank() || fromBankCode == null)
            return "accounts/transfer";

        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setFromAccountNumber(from);
        transferRequest.setFromBankCode(fromBankCode);
        Account fromAccount = accountService.findOwnedAccount(sessionUser.getId(), from, fromBankCode);
        model.addAttribute("bankCodes", BankCode.values());
        addTransferLimitAttributes(model, fromAccount);
        model.addAttribute("transferRequest", transferRequest);
        return "accounts/transfer-target";
    }

    @PostMapping("/transfer")
    public String accountsTransfer(@Valid @ModelAttribute("transferRequest") TransferRequest transferRequest,
                                   @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser,
                                   BindingResult bindingResult,
                                   Model model){
        User user = userService.findUser(sessionUser);
        model.addAttribute("bankCodes", BankCode.values());
        addTransferLimitAttributes(
                model,
                sessionUser.getId(),
                transferRequest.getFromAccountNumber(),
                transferRequest.getFromBankCode());

        if(bindingResult.hasErrors()){
            return "accounts/transfer-target";
        }

        try{
            accountService.transfer(transferRequest, user);
        } catch(Exception e){
            model.addAttribute("errorMessage", e.getMessage());
            return "accounts/transfer-target";
        }

        return "redirect:/accounts";
    }

    // 출금 계좌의 일일 이체한도와 일회 이체한도 정보 추가
    private void addTransferLimitAttributes(Model model, Account account) {
        BigDecimal todayUsedTransferAmount = accountService.getTodayUsedTransferAmount(account.getId());
        BigDecimal remainingDailyTransferLimit = account.getDailyTransferLimit().subtract(todayUsedTransferAmount);
        if (remainingDailyTransferLimit.compareTo(BigDecimal.ZERO) < 0) {
            remainingDailyTransferLimit = BigDecimal.ZERO;
        }

        model.addAttribute("dailyTransferLimit", account.getDailyTransferLimit());
        model.addAttribute("singleTransferLimit", account.getSingleTransferLimit());
        model.addAttribute("todayUsedTransferAmount", todayUsedTransferAmount);
        model.addAttribute("remainingDailyTransferLimit", remainingDailyTransferLimit);
    }

    private void addTransferLimitAttributes(Model model, Long userId, String accountNumber, BankCode bankCode) {
        if (accountNumber == null || accountNumber.isBlank() || bankCode == null) {
            model.addAttribute("dailyTransferLimit", BigDecimal.ZERO);
            model.addAttribute("singleTransferLimit", BigDecimal.ZERO);
            model.addAttribute("todayUsedTransferAmount", BigDecimal.ZERO);
            model.addAttribute("remainingDailyTransferLimit", BigDecimal.ZERO);
            return;
        }

        Account account = accountService.findOwnedAccount(userId, accountNumber, bankCode);
        addTransferLimitAttributes(model, account);
    }

    @GetMapping("/transfer-limit")
    public String accountsTransferLimit(Model model,
              @RequestParam(required = false) String accountNumber,
              @RequestParam(required = false) BankCode bankCode,
              @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser){
        addTransferLimitPageAttributes(model, sessionUser.getId(), accountNumber, bankCode);
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
            addTransferLimitPageAttributes(model, sessionUser.getId(), accountNumber, bankCode);
            return "accounts/transfer-limit";
        }

        redirectAttributes.addAttribute("accountNumber", accountNumber);
        redirectAttributes.addAttribute("bankCode", bankCode);
        return "redirect:/accounts/transfer-limit";
    }

    private void addTransferLimitPageAttributes(Model model, Long userId, String accountNumber, BankCode bankCode) {
        List<Account> accounts = accountService.findAccounts(userId);
        model.addAttribute("accounts", accounts);

        if (accountNumber == null || accountNumber.isBlank() || bankCode == null) {
            return;
        }

        Account selectedAccount = accountService.findOwnedAccount(userId, accountNumber, bankCode);
        model.addAttribute("selectedAccount", selectedAccount);
        addTransferLimitAttributes(model, selectedAccount);
    }

    @GetMapping("/transactions")
    public String accountsTransactions(@RequestParam(required = false) String accountNumber,
                                       @RequestParam(required = false) BankCode bankCode,
                                       @RequestParam(required = false, defaultValue = "ONE_MONTH") TransactionPeriod period,
                                       @RequestParam(required = false, defaultValue = "0") int page,
                                       Model model,
                                       @SessionAttribute(name = Const.LOGIN_USER) SessionUser sessionUser){
        List<Account> accounts = accountService.findAccounts(sessionUser.getId());
        model.addAttribute("accounts", accounts);
        model.addAttribute("period", period);
        model.addAttribute("periods", TransactionPeriod.values());
        model.addAttribute("page", Math.max(page, 0));

        // 쿼리 파라미터값이 없으면 전체 계좌의 거래내역 표시
        if(accountNumber == null || accountNumber.isBlank() || bankCode == null){
            Page<AccountTransaction> transactionPage = accountService.findTransactions(sessionUser.getId(), period, page);
            TransactionSummary transactionSummary = accountService.summarizeTransactions(sessionUser.getId(), period);
            model.addAttribute("transactionPage", transactionPage);
            model.addAttribute("transactions", transactionPage.getContent());
            model.addAttribute("transactionSummary", transactionSummary);
            addPaginationAttributes(model, transactionPage);
        }
        // 쿼리 파라미터값이 있으면 해당 계좌의 거래내역 표시
        else{
            Account selectedAccount = accountService.findOwnedAccount(sessionUser.getId(), accountNumber, bankCode);
            Page<AccountTransaction> transactionPage = accountService.findTransactions(
                    sessionUser.getId(),
                    accountNumber,
                    bankCode,
                    period,
                    page);
            TransactionSummary transactionSummary = accountService.summarizeTransactions(
                    sessionUser.getId(),
                    accountNumber,
                    bankCode,
                    period);
            model.addAttribute("selectedAccount", selectedAccount);
            model.addAttribute("transactionPage", transactionPage);
            model.addAttribute("transactions", transactionPage.getContent());
            model.addAttribute("transactionSummary", transactionSummary);
            addPaginationAttributes(model, transactionPage);
        }
        return "accounts/transactions";
    }

    private void addPaginationAttributes(Model model, Page<?> page) {
        if (page.getTotalPages() == 0) {
            model.addAttribute("pageNumbers", List.of());
            return;
        }

        int currentPage = page.getNumber();
        int totalPages = page.getTotalPages();
        int startPage = Math.max(0, currentPage - 2);
        int endPage = Math.min(totalPages - 1, startPage + 4);
        startPage = Math.max(0, endPage - 4);

        List<Integer> pageNumbers = IntStream.rangeClosed(startPage, endPage)
                .boxed()
                .toList();

        model.addAttribute("pageNumbers", pageNumbers);
    }
}
