package com.finmate.controller.investment;

import com.finmate.domain.investment.dto.InvestmentHomeInfo;
import com.finmate.domain.investment.dto.InvestmentOverviewResponse;
import com.finmate.global.security.FinMateAuthenticatedPrincipal;
import com.finmate.service.investment.InvestmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 기존 InvestmentService의 조회·대표계좌 변경 기능을 React용 JSON API로 연결한다.
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/investments")
public class InvestmentOverviewController {

    private final InvestmentService investmentService;

    @GetMapping
    public InvestmentOverviewResponse getOverview(
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal sessionUser) {
        return getOverview(sessionUser.getId());
    }

    @PostMapping("/primary")
    public InvestmentOverviewResponse setPrimary(
            @Valid @RequestBody PrimaryInvestmentRequest request,
            @AuthenticationPrincipal FinMateAuthenticatedPrincipal sessionUser) {
        investmentService.setPrimary(request.investmentId(), sessionUser.getId());
        return getOverview(sessionUser.getId());
    }

    private InvestmentOverviewResponse getOverview(Long userId) {
        InvestmentHomeInfo homeInfo = investmentService.getInvestmentHomeInfo(userId);
        return InvestmentOverviewResponse.from(homeInfo);
    }

    public record PrimaryInvestmentRequest(@NotNull(message = "대표 증권계좌는 필수입니다.") Long investmentId) {
    }
}
