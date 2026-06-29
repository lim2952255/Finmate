package com.finmate.repository.normal.account;

import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.BankCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByUser_IdAndPrimaryTrue(Long userId);

    // 계좌번호와 은행기반으로 계좌 검색
    @Query("select a.id from Account a where a.accountNumber = :accountNumber and a.bankCode = :bankCode")
    Optional<Long> findIdByAccountNumberAndBankCode(@Param("accountNumber") String accountNumber,
                                                    @Param("bankCode") BankCode bankCode);

    // 대표계좌 설정을 위해 조회하는 계좌에 비관적 락을 설정한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.user.id = :userId order by a.id")
    List<Account> findByUserIdForUpdate(@Param("userId") Long userId);

    Optional<Account> findByUser_IdAndAccountNumber(Long userId, String accountNumber);

    Optional<Account> findByUser_IdAndAccountNumberAndBankCode(Long userId, String accountNumber, BankCode bankCode);

    List<Account> findByUser_Id(Long userId);

    long countByUser_Id(Long userId);
}
