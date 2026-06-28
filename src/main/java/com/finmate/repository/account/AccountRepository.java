package com.finmate.repository.account;

import com.finmate.domain.account.Account;
import com.finmate.domain.account.BankCode;
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

    Optional<Account> findByAccountNumber(String accountNumber);

    // 계좌번호와 은행기반으로 계좌 검색
    Optional<Account> findByAccountNumberAndBankCode(String accountNumber, BankCode bankCode);

    @Query("select a.id from Account a where a.accountNumber = :accountNumber and a.bankCode = :bankCode")
    Optional<Long> findIdByAccountNumberAndBankCode(@Param("accountNumber") String accountNumber,
                                                    @Param("bankCode") BankCode bankCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    Optional<Account> findByUser_IdAndAccountNumber(Long userId, String accountNumber);

    Optional<Account> findByUser_IdAndAccountNumberAndBankCode(Long userId, String accountNumber, BankCode bankCode);

    List<Account> findByUser_Id(Long userId);

    long countByUser_Id(Long userId);
}
