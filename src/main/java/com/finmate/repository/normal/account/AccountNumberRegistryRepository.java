package com.finmate.repository.normal.account;

import com.finmate.domain.normal.account.AccountNumberRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountNumberRegistryRepository extends JpaRepository<AccountNumberRegistry, Long> {
    boolean existsByAccountNumber(String accountNumber);
}
