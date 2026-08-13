package com.finmate.controller.account;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.investment.dto.OpenInvestment;
import com.finmate.domain.normal.account.BankCode;
import com.finmate.domain.normal.account.dto.OpenAccount;
import com.finmate.domain.user.User;
import com.finmate.global.security.FinMateAuthenticatedPrincipal;
import com.finmate.service.investment.InvestmentService;
import com.finmate.service.normal.account.AccountService;
import com.finmate.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/account-setup")
public class AccountSetupApiController {
    private final AccountService accountService;
    private final InvestmentService investmentService;
    private final UserService userService;

    @GetMapping
    public SetupOptions options() {
        return new SetupOptions(
                Arrays.stream(BankCode.values()).map(code -> new Option(code.name(), code.getDisplayName())).toList(),
                Arrays.stream(CurrencyCode.values()).map(code -> new Option(code.name(), code.getDisplayName())).toList(),
                Arrays.stream(SecuritiesCompanyCode.values()).map(code -> new Option(code.name(), code.getDisplayName())).toList());
    }

    @PostMapping("/accounts")
    public ResponseEntity<Void> openAccount(
            @Valid @RequestBody OpenAccount request,
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        User user = userService.findUser(principal.getId());
        accountService.openAccount(request, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/investments")
    public ResponseEntity<Void> openInvestment(
            @Valid @RequestBody OpenInvestment request,
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal principal) {
        User user = userService.findUser(principal.getId());
        investmentService.openInvestment(request, user);
        return ResponseEntity.noContent().build();
    }

    public record SetupOptions(List<Option> banks, List<Option> currencies, List<Option> securitiesCompanies) {
    }

    public record Option(String value, String label) {
    }
}
