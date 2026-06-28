package com.finmate.service.account;

import com.finmate.domain.account.Account;
import com.finmate.domain.account.BankCode;
import com.finmate.domain.account.dto.OpenAccount;
import com.finmate.domain.account.dto.PrimaryAccount;
import com.finmate.domain.account.dto.TransferRequest;
import com.finmate.domain.accountTransaction.AccountTransaction;
import com.finmate.domain.accountTransaction.AccountTransactionType;
import com.finmate.domain.transfer.DailyTransferUsage;
import com.finmate.domain.transfer.Transfer;
import com.finmate.domain.user.User;
import com.finmate.domain.user.dto.SessionUser;
import com.finmate.exception.DuplicatedId;
import com.finmate.global.Const;
import com.finmate.repository.account.AccountRepository;
import com.finmate.repository.accountTransaction.AccountTransactionRepository;
import com.finmate.repository.transfer.DailyTransferUsageRepository;
import com.finmate.repository.transfer.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {
    private static final int MAX_ACCOUNT_COUNT = 10;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final AccountTransactionRepository accountTransactionRepository;
    private final DailyTransferUsageRepository dailyTransferUsageRepository;

    @Transactional(readOnly = true)
    public Optional<PrimaryAccount> getPrimaryAccount(SessionUser user) {
        return accountRepository.findByUser_IdAndPrimaryTrue(user.getId())
                .map(PrimaryAccount::new);
    }

    // 계좌 개설
    @Transactional
    public Long openAccount(OpenAccount openAccount, User user) {
        long accountCount = accountRepository.countByUser_Id(user.getId());
        if (accountCount >= MAX_ACCOUNT_COUNT) {
            throw new RuntimeException("계좌는 최대 10개까지만 개설할 수 있습니다.");
        }

        Optional<Account> duplicatedId = accountRepository.findByAccountNumber(openAccount.getAccountNumber());
        if (duplicatedId.isPresent()) { // ID 중복
            throw new DuplicatedId(openAccount.getAccountNumber());
        }

        Account account = new Account();
        account.setAccountNumber(openAccount.getAccountNumber());
        account.setBalance(Const.INITIAL_BALANCE);
        account.setBankCode(openAccount.getBankCode());

        // 이때 user는 준영속상태이다.
        // 하지만 연관관계의 주인은 Account이기 때문에, user가 준영속상태라고 해도, 양방향 연관관계를 설정한다면,
        // Account에 user정보가 업데이트되기 때문에 상관없다.
        user.addAccount(account);

        Account savedAccount = accountRepository.save(account);
        return savedAccount.getId();
    }

    @Transactional
    public String setPrimary(String accountNumber, User user) {
        // 현재 사용자의 계좌가 아닐 경우 예외 처리
        Account newPrimary = accountRepository.findByUser_IdAndAccountNumber(user.getId(), accountNumber)
                .orElseThrow(() -> new RuntimeException("현재 사용자의 계좌가 아닙니다."));

        // 이전 대표계좌 설정해제
        Optional<Account> previousPrimary = accountRepository.findByUser_IdAndPrimaryTrue(user.getId());
        if (previousPrimary.isPresent()) {
            Account previous =  previousPrimary.get();
            if(previous.getAccountNumber().equals(accountNumber)) {
                return accountNumber;
            }
            previous.setPrimary(false);
        }

        // 새로운 대표계좌 설정
        newPrimary.setPrimary(true);

        return accountNumber;
    }

    @Transactional(readOnly = true)
    public List<Account> findAccounts(Long userId) {
        return accountRepository.findByUser_Id(userId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTodayUsedTransferAmount(Long accountId) {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        return dailyTransferUsageRepository.findByAccount_IdAndUsageDate(accountId, today)
                .map(DailyTransferUsage::getUsedAmount)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public Account findOwnedAccount(Long userId, String accountNumber, BankCode bankCode) {
        return accountRepository.findByUser_IdAndAccountNumberAndBankCode(userId, accountNumber, bankCode)
                .orElseThrow(() -> new RuntimeException("현재 사용자의 계좌가 아닙니다."));
    }

    @Transactional
    public void updateTransferLimit(Long userId,
                                    String accountNumber,
                                    BankCode bankCode,
                                    BigDecimal dailyTransferLimit,
                                    BigDecimal singleTransferLimit) {
        Account account = accountRepository.findByUser_IdAndAccountNumberAndBankCode(userId, accountNumber, bankCode)
                .orElseThrow(() -> new RuntimeException("현재 사용자의 계좌가 아닙니다."));

        account.updateTransferLimit(dailyTransferLimit, singleTransferLimit);
    }

    private Long findAccountId(String accountNumber, BankCode bankCode, String errorMessage) {
        return accountRepository.findIdByAccountNumberAndBankCode(accountNumber, bankCode)
                .orElseThrow(() -> new RuntimeException(errorMessage));
    }

    // 계좌이체처럼 "조회 -> 검증 -> 변경"이 하나의 논리적 작업인 경우,
    // 조회 시점부터 SELECT FOR UPDATE 기반의 비관적 락을 잡아야 잔고 정합성을 지킬 수 있다.
    @Transactional
    public void transfer(TransferRequest transferRequest, User user) {
        String fromAccountNumber = transferRequest.getFromAccountNumber();
        String toAccountNumber = transferRequest.getToAccountNumber();
        BigDecimal transferAmount = transferRequest.getAmount();

        Long fromAccountId = findAccountId(
                fromAccountNumber,
                transferRequest.getFromBankCode(),
                "출금 계좌를 찾을 수 없습니다.");

        Long toAccountId = findAccountId(
                toAccountNumber,
                transferRequest.getToBankCode(),
                "입금 계좌를 찾을 수 없습니다.");

        if(fromAccountId.equals(toAccountId))
            throw new RuntimeException("같은 계좌로는 이체할 수 없습니다.");

        LockedTransferAccounts lockedAccounts = lockAccountsForTransferAvoidingDeadlock(fromAccountId, toAccountId);
        Account fromAccount = lockedAccounts.fromAccount();
        Account toAccount = lockedAccounts.toAccount();

        if(!fromAccount.getUser().getId().equals(user.getId()))
            throw new RuntimeException("출금 계좌가 현재 사용자의 계좌가 아닙니다.");

        useDailyTransferLimit(fromAccount, transferAmount);

        BigDecimal fromBalanceBefore = fromAccount.getBalance();
        BigDecimal toBalanceBefore = toAccount.getBalance();

        fromAccount.withdraw(transferAmount);
        toAccount.deposit(transferAmount);

        // 계좌이체 정보 추가
        Transfer transfer = Transfer.createCompleted(
                UUID.randomUUID().toString(),
                fromAccount,
                toAccount,
                transferAmount);
        transferRepository.save(transfer);

        // 출금 계좌 거래내역 추가
        AccountTransaction withdrawalTransaction = AccountTransaction.create(
                fromAccount,
                transfer,
                AccountTransactionType.TRANSFER_OUT,
                transferAmount,
                fromBalanceBefore,
                fromAccount.getBalance(),
                toAccount.getBankCode(),
                toAccount.getAccountNumber(),
                toAccount.getUser().getUsername(),
                "계좌이체");

        // 입금 계좌 거래내역 추가
        AccountTransaction depositTransaction = AccountTransaction.create(
                toAccount,
                transfer,
                AccountTransactionType.TRANSFER_IN,
                transferAmount,
                toBalanceBefore,
                toAccount.getBalance(),
                fromAccount.getBankCode(),
                fromAccount.getAccountNumber(),
                fromAccount.getUser().getUsername(),
                "계좌이체");

        accountTransactionRepository.save(withdrawalTransaction);
        accountTransactionRepository.save(depositTransaction);

    }

    // 일일 or 일회 이체한도 초과여부 검사
    private void useDailyTransferLimit(Account fromAccount, BigDecimal transferAmount) {
        if(transferAmount.compareTo(fromAccount.getSingleTransferLimit()) > 0)
            throw new RuntimeException("일회 이체한도를 초과했습니다.");

        LocalDate today = LocalDate.now(SERVICE_ZONE);
        DailyTransferUsage dailyTransferUsage = dailyTransferUsageRepository
                .findByAccountIdAndUsageDateForUpdate(fromAccount.getId(), today)
                .orElseGet(() -> dailyTransferUsageRepository.save(DailyTransferUsage.create(fromAccount, today)));

        // 일일 이체한도 검사 + 오늘 사용금액 증가
        dailyTransferUsage.use(transferAmount, fromAccount.getDailyTransferLimit());
    }

    // 데드락 방지를 위해 id가 작은 계좌부터 lock을 얻는다.
    private LockedTransferAccounts lockAccountsForTransferAvoidingDeadlock(Long fromAccountId, Long toAccountId) {
        Long firstLockId = Math.min(fromAccountId, toAccountId);
        Long secondLockId = Math.max(fromAccountId, toAccountId);

        Account firstLockedAccount = accountRepository.findByIdForUpdate(firstLockId)
                .orElseThrow(() -> new RuntimeException("계좌를 찾을 수 없습니다."));
        Account secondLockedAccount = accountRepository.findByIdForUpdate(secondLockId)
                .orElseThrow(() -> new RuntimeException("계좌를 찾을 수 없습니다."));

        Account fromAccount = firstLockedAccount.getId().equals(fromAccountId)
                ? firstLockedAccount
                : secondLockedAccount;
        Account toAccount = firstLockedAccount.getId().equals(toAccountId)
                ? firstLockedAccount
                : secondLockedAccount;

        return new LockedTransferAccounts(fromAccount, toAccount);
    }

    private record LockedTransferAccounts(Account fromAccount, Account toAccount) {
    }
}
