package com.finmate.service.investment;


import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.dto.OpenInvestment;
import com.finmate.domain.investment.dto.PrimaryInvestment;
import com.finmate.domain.normal.account.AccountType;
import com.finmate.domain.user.dto.SessionUser;
import com.finmate.domain.user.User;
import com.finmate.repository.investment.InvestmentRepository;
import com.finmate.service.normal.account.AccountNumberRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class InvestmentService {
    private static final int MAX_INVESTMENT_ACCOUNT_COUNT = 10;
    private static final int MAX_ACCOUNT_NUMBER_GENERATION_ATTEMPTS = 100;

    private final InvestmentRepository investmentRepository;
    private final AccountNumberRegistryService accountNumberRegistryService;

    @Transactional(readOnly = true)
    public List<Investment> findInvestments(Long userId) {
        return investmentRepository.findByUser_Id(userId);
    }

    @Transactional(readOnly = true)
    public Optional<PrimaryInvestment> getPrimaryInvestment(SessionUser user) {
        return investmentRepository.findByUser_IdAndPrimaryTrue(user.getId())
                .map(PrimaryInvestment::new);
    }

    @Transactional
    public Long openInvestment(OpenInvestment openInvestment, User user) {
        long investmentAccountCount = investmentRepository.countByUser_Id(user.getId());
        if (investmentAccountCount >= MAX_INVESTMENT_ACCOUNT_COUNT) {
            throw new RuntimeException("증권 계좌는 최대 10개까지만 개설할 수 있습니다.");
        }

        String accountNumber = registerUniqueAccountNumber();
        Investment investment = Investment.create(
                user,
                accountNumber,
                openInvestment.getSecuritiesCompanyCode());

        Investment savedInvestment = investmentRepository.save(investment);
        return savedInvestment.getId();
    }
    private String registerUniqueAccountNumber() {
        for (int i = 0; i < MAX_ACCOUNT_NUMBER_GENERATION_ATTEMPTS; i++) {
            String accountNumber = generateAccountNumber();
            try {
                accountNumberRegistryService.register(accountNumber, AccountType.INVESTMENT);
                return accountNumber;
            } catch (DataIntegrityViolationException e) {
                // 동시 요청에서 같은 번호가 먼저 등록된 경우 다른 번호로 재시도한다.
            }
        }

        throw new RuntimeException("증권 계좌번호 생성에 실패했습니다. 다시 시도해주세요.");
    }

    private String generateAccountNumber() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return String.format(
                "%06d-%02d-%06d",
                random.nextInt(1_000_000),
                random.nextInt(100),
                random.nextInt(1_000_000));
    }

    @Transactional
    public Long setPrimary(Long investmentId, Long userId) {
        List<Investment> investments = investmentRepository.findByUserIdForUpdate(userId);

        Investment newPrimary = investments.stream()
                .filter(investment -> investment.getId().equals(investmentId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("현재 사용자의 증권 계좌가 아닙니다."));

        if (newPrimary.isPrimary()) {
            return investmentId;
        }

        // 기존 대표계좌는 설정 해제
        investments.stream()
                .filter(Investment::isPrimary)
                .forEach(Investment::unmarkPrimary);

        newPrimary.markAsPrimary();

        return investmentId;
    }
}
