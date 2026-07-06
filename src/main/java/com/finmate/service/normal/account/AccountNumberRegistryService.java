package com.finmate.service.normal.account;

import com.finmate.domain.normal.account.AccountNumberRegistry;
import com.finmate.domain.normal.account.AccountType;
import com.finmate.repository.normal.account.AccountNumberRegistryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountNumberRegistryService {
    private final AccountNumberRegistryRepository accountNumberRegistryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void register(String accountNumber, AccountType accountType) {
        accountNumberRegistryRepository.saveAndFlush(AccountNumberRegistry.create(accountNumber, accountType));
    }
}
