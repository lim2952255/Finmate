package com.finmate.controller.account;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.dto.cash.InvestmentDepositPageInfo;
import com.finmate.domain.investment.dto.cash.InvestmentDepositRequest;
import com.finmate.domain.investment.dto.cash.InvestmentWithdrawalPageInfo;
import com.finmate.domain.investment.dto.cash.InvestmentWithdrawalRequest;
import com.finmate.domain.investment.dto.exchange.InvestmentCurrencyExchangePageInfo;
import com.finmate.domain.investment.dto.exchange.InvestmentCurrencyExchangeRequest;
import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.BankCode;
import com.finmate.domain.normal.account.dto.TransferLimitPageInfo;
import com.finmate.domain.normal.account.dto.TransferRequest;
import com.finmate.domain.user.User;
import com.finmate.global.security.FinMateAuthenticatedPrincipal;
import com.finmate.service.investment.InvestmentCurrencyExchangeService;
import com.finmate.service.investment.InvestmentService;
import com.finmate.service.normal.account.AccountService;
import com.finmate.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/account-operations")
public class AccountOperationApiController {
    private final AccountService accountService;
    private final InvestmentService investmentService;
    private final InvestmentCurrencyExchangeService exchangeService;
    private final UserService userService;

    @GetMapping
    public OperationData data(@AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        List<Account> accounts = accountService.findAccounts(principal.getId());
        List<Investment> investments = investmentService.findInvestments(principal.getId());
        return new OperationData(accounts.stream().map(AccountInfo::from).toList(), investments.stream().map(InvestmentInfo::from).toList(),
                Arrays.stream(BankCode.values()).map(code -> new Option(code.name(), code.getDisplayName())).toList(),
                Arrays.stream(CurrencyCode.values()).map(code -> new Option(code.name(), code.getDisplayName())).toList());
    }

    @GetMapping("/transfer-limit")
    public TransferLimitData transferLimit(@RequestParam(required = false) String accountNumber,
                                           @RequestParam(required = false) BankCode bankCode,
                                           @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        TransferLimitPageInfo info = accountService.getTransferLimitPageInfo(principal.getId(), accountNumber, bankCode);
        return new TransferLimitData(info.getAccounts().stream().map(AccountInfo::from).toList(),
                info.getSelectedAccount() == null ? null : info.getSelectedAccount().getId(),
                info.getTransferLimitInfo().getDailyTransferLimit().toPlainString(),
                info.getTransferLimitInfo().getSingleTransferLimit().toPlainString(),
                info.getTransferLimitInfo().getTodayUsedTransferAmount().toPlainString());
    }

    @PostMapping("/transfer-limit")
    public ResponseEntity<Void> updateLimit(@RequestBody LimitRequest request,
                                            @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        accountService.updateTransferLimit(principal.getId(), request.accountNumber(), request.bankCode(), request.dailyTransferLimit(), request.singleTransferLimit());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/transfer")
    public ResponseEntity<Void> transfer(@Valid @RequestBody TransferRequest request,
                                         @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        User user = userService.findUser(principal.getId());
        accountService.transfer(request, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/deposit-investment")
    public ResponseEntity<Void> deposit(@Valid @RequestBody InvestmentDepositRequest request,
                                        @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        investmentService.depositToInvestment(request, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/withdraw-investment")
    public ResponseEntity<Void> withdraw(@Valid @RequestBody InvestmentWithdrawalRequest request,
                                         @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        investmentService.withdrawFromInvestment(request, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/exchange")
    public ExchangeData exchangeData(@AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        InvestmentCurrencyExchangePageInfo info = exchangeService.getCurrencyExchangePageInfo(principal.getId(), new InvestmentCurrencyExchangeRequest());
        return new ExchangeData(info.getInvestments().stream().map(InvestmentInfo::from).toList(),
                Arrays.stream(info.getCurrencyCodes()).map(code -> new Option(code.name(), code.getDisplayName())).toList(),
                info.getUsdKrwExchangeRate() == null ? null : info.getUsdKrwExchangeRate().toPlainString());
    }

    @PostMapping("/exchange")
    public ResponseEntity<Void> exchange(@Valid @RequestBody InvestmentCurrencyExchangeRequest request,
                                         @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        exchangeService.exchangeCurrency(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }

    public record OperationData(List<AccountInfo> accounts, List<InvestmentInfo> investments, List<Option> banks, List<Option> currencies) {}
    public record Option(String value, String label) {}
    public record AccountInfo(Long id, String accountNumber, String companyCode, String companyName, String currency, String balance) {
        static AccountInfo from(Account value) { return new AccountInfo(value.getId(), value.getAccountNumber(), value.getBankCode().name(), value.getBankCode().getDisplayName(), value.getCurrencyCode().name(), value.getBalance().toPlainString()); }
    }
    public record InvestmentInfo(Long id, String accountNumber, String companyCode, String companyName, List<BalanceInfo> balances) {
        static InvestmentInfo from(Investment value) { return new InvestmentInfo(value.getId(), value.getAccountNumber(), value.getSecuritiesCompanyCode().name(), value.getSecuritiesCompanyCode().getDisplayName(), value.getCashBalances().stream().map(balance -> new BalanceInfo(balance.getCurrencyCode().name(), balance.getAvailableBalance().toPlainString())).toList()); }
    }
    public record BalanceInfo(String currency, String amount) {}
    public record TransferLimitData(List<AccountInfo> accounts, Long selectedAccountId, String dailyLimit, String singleLimit, String todayUsed) {}
    public record LimitRequest(String accountNumber, BankCode bankCode, BigDecimal dailyTransferLimit, BigDecimal singleTransferLimit) {}
    public record ExchangeData(List<InvestmentInfo> investments, List<Option> currencies, String usdKrwExchangeRate) {}
}
