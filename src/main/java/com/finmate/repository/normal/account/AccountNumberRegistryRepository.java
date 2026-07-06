package com.finmate.repository.normal.account;

import com.finmate.domain.normal.account.AccountNumberRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 계좌번호 엔티티 관리 repository
@Repository
public interface AccountNumberRegistryRepository extends JpaRepository<AccountNumberRegistry, Long> {
}
