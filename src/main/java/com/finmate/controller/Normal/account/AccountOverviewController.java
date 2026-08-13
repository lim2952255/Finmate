package com.finmate.controller.normal.account;

import com.finmate.domain.normal.account.dto.AccountHomeInfo;
import com.finmate.domain.normal.account.dto.AccountOverviewResponse;
import com.finmate.global.security.FinMateAuthenticatedPrincipal;
import com.finmate.service.normal.account.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 기존 AccountService의 조회·대표계좌 변경 기능을 React용 JSON API로 연결한다.
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/accounts")
public class AccountOverviewController {

    private final AccountService accountService;

    @GetMapping
    public AccountOverviewResponse getOverview(
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal sessionUser) {
        return getOverview(sessionUser.getId());
    }

    @PostMapping("/primary")
    public AccountOverviewResponse setPrimary(
            @RequestBody PrimaryAccountRequest request,
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal sessionUser) {
        accountService.setPrimary(request.accountId(), sessionUser.getId());
        return getOverview(sessionUser.getId());
    }

    private AccountOverviewResponse getOverview(Long userId) {
        AccountHomeInfo homeInfo = accountService.getAccountHomeInfo(userId);
        return AccountOverviewResponse.from(homeInfo);
    }

    public record PrimaryAccountRequest(Long accountId) {
    }
}
