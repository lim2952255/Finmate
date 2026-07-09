package com.finmate.service.normal.account;

import com.finmate.domain.normal.account.AccountNumberRegistry;
import com.finmate.domain.normal.account.AccountType;
import com.finmate.repository.normal.account.AccountNumberRegistryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AccountNumberRegistryService {
    private static final int MAX_ACCOUNT_NUMBER_GENERATION_ATTEMPTS = 100;

    private final AccountNumberRegistryRepository accountNumberRegistryRepository;
    private final PlatformTransactionManager transactionManager;

    // 고유한 계좌번호를 생성하고 Registry에 먼저 등록한다.
    public String issueUniqueAccountNumber(AccountType accountType) {
        for (int i = 0; i < MAX_ACCOUNT_NUMBER_GENERATION_ATTEMPTS; i++) {
            String accountNumber = generateAccountNumber();
            try {
                registerInNewTransaction(accountNumber, accountType);
                return accountNumber;
            } catch (DataIntegrityViolationException e) {
                // 동시 요청에서 같은 번호가 먼저 등록된 경우 다른 번호로 재시도한다.
            }
        }

        throw new RuntimeException("계좌번호 생성에 실패했습니다. 다시 시도해주세요.");
    }

    private void registerInNewTransaction(String accountNumber, AccountType accountType) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.executeWithoutResult(status ->
                accountNumberRegistryRepository.saveAndFlush(AccountNumberRegistry.create(accountNumber, accountType)));
    }

    // 고유한 계좌 번호 생성기
    private String generateAccountNumber() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return String.format(
                "%06d-%02d-%06d",
                random.nextInt(1_000_000),
                random.nextInt(100),
                random.nextInt(1_000_000));
    }
}
