package com.finmate.service.normal.account;

import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.AccountType;
import com.finmate.domain.normal.account.BankCode;
import com.finmate.domain.normal.account.dto.AccountHomeInfo;
import com.finmate.domain.normal.account.dto.OpenAccount;
import com.finmate.domain.normal.account.dto.PrimaryAccount;
import com.finmate.domain.normal.account.dto.TransferLimitInfo;
import com.finmate.domain.normal.account.dto.TransferLimitPageInfo;
import com.finmate.domain.normal.account.dto.TransferRequest;
import com.finmate.domain.normal.account.transaction.AccountTransaction;
import com.finmate.domain.normal.account.transaction.AccountTransactionType;
import com.finmate.domain.normal.account.transaction.TransactionPeriod;
import com.finmate.domain.normal.account.transaction.dto.AccountTransactionPageInfo;
import com.finmate.domain.normal.account.transaction.dto.TransactionSummary;
import com.finmate.domain.normal.transfer.DailyTransferUsage;
import com.finmate.domain.normal.transfer.Transfer;
import com.finmate.domain.user.User;
import com.finmate.global.pagination.PaginationInfo;
import com.finmate.repository.normal.account.AccountRepository;
import com.finmate.repository.normal.account.transaction.AccountTransactionRepository;
import com.finmate.repository.normal.transfer.DailyTransferUsageRepository;
import com.finmate.repository.normal.transfer.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {
    private static final int MAX_ACCOUNT_COUNT = 10;
    private static final int MAX_ACCOUNT_NUMBER_GENERATION_ATTEMPTS = 100;
    private static final int TRANSACTION_PAGE_SIZE = 20;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final AccountRepository accountRepository;
    private final AccountNumberRegistryService accountNumberRegistryService;
    private final TransferRepository transferRepository;
    private final AccountTransactionRepository accountTransactionRepository;
    private final DailyTransferUsageRepository dailyTransferUsageRepository;

    // 입금액 계산 타입
    private static final List<AccountTransactionType> DEPOSIT_TYPES = List.of(
            AccountTransactionType.TRANSFER_IN,
            AccountTransactionType.DEPOSIT);
    // 출금액 계산 타입
    private static final List<AccountTransactionType> WITHDRAWAL_TYPES = List.of(
            AccountTransactionType.TRANSFER_OUT,
            AccountTransactionType.WITHDRAW);

    @Transactional(readOnly = true)
    public AccountHomeInfo getAccountHomeInfo(Long userId) {
        // 총 금액 + 대표계좌 + 총 계좌 수 정보를 dto에 담는다.
        List<Account> accounts = accountRepository.findByUser_Id(userId);
        BigDecimal totalBalance = accounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PrimaryAccount primaryAccount = accounts.stream()
                .filter(Account::isPrimary)
                .findFirst()
                .map(PrimaryAccount::new)
                .orElse(null);

        return new AccountHomeInfo(primaryAccount, totalBalance, accounts.size());
    }

    // 계좌 개설
    @Transactional
    public Long openAccount(OpenAccount openAccount, User user) {
        long accountCount = accountRepository.countByUser_Id(user.getId());
        if (accountCount >= MAX_ACCOUNT_COUNT) {
            throw new RuntimeException("계좌는 최대 10개까지만 개설할 수 있습니다.");
        }

        // Registry에 새로운 계좌번호를 등록한 후 계좌를 개설한다.
        String accountNumber = registerUniqueAccountNumber(AccountType.NORMAL);
        Account account = Account.create(accountNumber, openAccount.getBankCode());

        // 이때 user는 준영속상태이다.
        // 하지만 연관관계의 주인은 Account이기 때문에, user가 준영속상태라고 해도, 양방향 연관관계를 설정한다면,
        // Account에 user정보가 업데이트되기 때문에 상관없다.
        user.addAccount(account);

        Account savedAccount = accountRepository.save(account);
        return savedAccount.getId();
    }

    // 고유한 계좌번호 생성 후 계좌 생성 전에 Registry에 먼저 등록한다.
    private String registerUniqueAccountNumber(AccountType accountType) {
        for (int i = 0; i < MAX_ACCOUNT_NUMBER_GENERATION_ATTEMPTS; i++) {
            String accountNumber = generateAccountNumber();
            try {
                accountNumberRegistryService.register(accountNumber, accountType);
                return accountNumber;
                // DB수준에서 unique제약조건을 설정했기 때문 최종적으로는 동시성 문제가 발생하지 않는다.
            } catch (DataIntegrityViolationException e) {
                // 동시 요청에서 같은 번호가 먼저 등록된 경우 다른 번호로 재시도한다.
            }
        }

        throw new RuntimeException("계좌번호 생성에 실패했습니다. 다시 시도해주세요.");
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

    @Transactional
    public Long setPrimary(Long accountId, Long userId) {
        List<Account> accounts = accountRepository.findByUserIdForUpdate(userId);

        // primary로 설정하려는 계좌가 현재 사용자 계좌가 아니라면 오류 출력
        Account newPrimary = accounts.stream()
                .filter(account -> account.getId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("현재 사용자의 계좌가 아닙니다."));

        if (newPrimary.isPrimary()) {
            return accountId;
        }

        // primary인 계좌를 찾아서 unmark 시키는 코드
        accounts.stream()
                .filter(Account::isPrimary)
                .forEach(Account::unmarkPrimary);

        newPrimary.markAsPrimary();

        return accountId;
    }

    // 특정 사용자의 계좌목록 리턴
    @Transactional(readOnly = true)
    public List<Account> findAccounts(Long userId) {
        return accountRepository.findByUser_Id(userId);
    }

    // 금일 이체액 출력
    private BigDecimal getTodayUsedTransferAmount(Long accountId) {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        return dailyTransferUsageRepository.findByAccount_IdAndUsageDate(accountId, today)
                .map(DailyTransferUsage::getUsedAmount)
                .orElse(BigDecimal.ZERO);
    }

    // 일일 이체한도 / 일회 이체한도 정보를 dto에 담아서 반환
    private TransferLimitInfo getTransferLimitInfo(Long userId, String accountNumber, BankCode bankCode) {
        Account account = accountRepository.findByUser_IdAndAccountNumberAndBankCode(userId, accountNumber, bankCode)
                .orElseThrow(() -> new RuntimeException("현재 사용자의 계좌가 아닙니다."));
        return createTransferLimitInfo(account);
    }

    // TransferRequest에는 입금은행 정보 / 출금은행 정보가 담겨있으며, 이를 통해 입출금계좌정보를 설정
    @Transactional(readOnly = true)
    public TransferRequest prepareTransfer(Long userId, String fromAccountNumber, BankCode fromBankCode) {
        Account fromAccount = accountRepository.findByUser_IdAndAccountNumberAndBankCode(
                        userId,
                        fromAccountNumber,
                        fromBankCode)
                .orElseThrow(() -> new RuntimeException("현재 사용자의 계좌가 아닙니다."));

        TransferRequest transferRequest = new TransferRequest();
        transferRequest.setFromAccountNumber(fromAccount.getAccountNumber());
        transferRequest.setFromBankCode(fromAccount.getBankCode());
        transferRequest.setTransferLimitInfo(createTransferLimitInfo(fromAccount));

        return transferRequest;
    }

    //TransferRequest에 데이터 설정
    @Transactional(readOnly = true)
    public void addTransferRequestDisplayInfo(Long userId, TransferRequest transferRequest) {
        if (transferRequest.getFromAccountNumber() == null
                || transferRequest.getFromAccountNumber().isBlank()
                || transferRequest.getFromBankCode() == null) {
            transferRequest.setTransferLimitInfo(TransferLimitInfo.zero());
            return;
        }

        try {
            TransferLimitInfo transferLimitInfo = getTransferLimitInfo(
                    userId,
                    transferRequest.getFromAccountNumber(),
                    transferRequest.getFromBankCode());
            transferRequest.setTransferLimitInfo(transferLimitInfo);
        } catch (Exception e) {
            transferRequest.setTransferLimitInfo(TransferLimitInfo.zero());
        }
    }

    // 이체한도 정보를 담은 dto 리턴
    @Transactional(readOnly = true)
    public TransferLimitPageInfo getTransferLimitPageInfo(Long userId, String accountNumber, BankCode bankCode) {
        List<Account> accounts = accountRepository.findByUser_Id(userId);
        if (accountNumber == null || accountNumber.isBlank() || bankCode == null) {
            return new TransferLimitPageInfo(accounts, null, TransferLimitInfo.zero());
        }

        Account selectedAccount = accountRepository.findByUser_IdAndAccountNumberAndBankCode(userId, accountNumber, bankCode)
                .orElseThrow(() -> new RuntimeException("현재 사용자의 계좌가 아닙니다."));

        return new TransferLimitPageInfo(accounts, selectedAccount, createTransferLimitInfo(selectedAccount));
    }

    private TransferLimitInfo createTransferLimitInfo(Account account) {
        BigDecimal todayUsedTransferAmount = getTodayUsedTransferAmount(account.getId());
        return new TransferLimitInfo(
                account.getDailyTransferLimit(),
                account.getSingleTransferLimit(),
                todayUsedTransferAmount);
    }

    private Account findOwnedAccount(Long userId, String accountNumber, BankCode bankCode) {
        return accountRepository.findByUser_IdAndAccountNumberAndBankCode(userId, accountNumber, bankCode)
                .orElseThrow(() -> new RuntimeException("현재 사용자의 계좌가 아닙니다."));
    }

    // 특정 사용자 또는 특정 계좌의 거래내역정보를 담은 dto 리턴
    @Transactional(readOnly = true)
    public AccountTransactionPageInfo getAccountTransactionPageInfo(Long userId,
                                                                    String accountNumber,
                                                                    BankCode bankCode,
                                                                    TransactionPeriod period,
                                                                    int page) {
        List<Account> accounts = accountRepository.findByUser_Id(userId);
        TransactionPeriod safePeriod = getSafePeriod(period);
        int safePage = PaginationInfo.safePage(page);
        TransactionDateRange dateRange = getTransactionDateRange(safePeriod);

        Account selectedAccount = null;
        Page<AccountTransaction> transactionPage;
        TransactionSummary transactionSummary;

        if (accountNumber == null || accountNumber.isBlank() || bankCode == null) {
            transactionPage = findUserTransactionPage(userId, dateRange, safePage);
            transactionSummary = summarizeUserTransactions(userId, dateRange);
        } else {
            selectedAccount = findOwnedAccount(userId, accountNumber, bankCode);
            transactionPage = findAccountTransactionPage(selectedAccount.getId(), dateRange, safePage);
            transactionSummary = summarizeAccountTransactions(selectedAccount.getId(), dateRange);
        }

        return new AccountTransactionPageInfo(
                accounts,
                selectedAccount,
                safePeriod,
                TransactionPeriod.values(),
                transactionPage,
                transactionSummary);
    }

    private TransactionDateRange getTransactionDateRange(TransactionPeriod period) {
        LocalDateTime endDateTime = LocalDateTime.now(SERVICE_ZONE);
        return new TransactionDateRange(period.getStartDateTime(endDateTime), endDateTime);
    }
    // 특정 사용자의 모든 계좌의 거래내역 정보를 담은 dto 반환
    private Page<AccountTransaction> findUserTransactionPage(Long userId, TransactionDateRange dateRange, int page) {
        return accountTransactionRepository.findAllByUserIdAndCreatedAtBetween(
                userId,
                dateRange.startDateTime(),
                dateRange.endDateTime(),
                PageRequest.of(page, TRANSACTION_PAGE_SIZE));
    }
    // 특정 계좌의 거래내역 정보를 담은 dto 반환
    private Page<AccountTransaction> findAccountTransactionPage(Long accountId,
                                                                TransactionDateRange dateRange,
                                                                int page) {
        return accountTransactionRepository.findAllByAccountIdAndCreatedAtBetween(
                accountId,
                dateRange.startDateTime(),
                dateRange.endDateTime(),
                PageRequest.of(page, TRANSACTION_PAGE_SIZE));
    }

    private TransactionSummary summarizeUserTransactions(Long userId, TransactionDateRange dateRange) {
        // 모든 계좌의 총 입금 금액 계산
        BigDecimal totalDepositAmount = accountTransactionRepository.sumAmountByUserIdAndTypes(
                userId,
                DEPOSIT_TYPES,
                dateRange.startDateTime(),
                dateRange.endDateTime());
        // 모든 계좌의 총 출금 금액 계산
        BigDecimal totalWithdrawalAmount = accountTransactionRepository.sumAmountByUserIdAndTypes(
                userId,
                WITHDRAWAL_TYPES,
                dateRange.startDateTime(),
                dateRange.endDateTime());

        return new TransactionSummary(totalDepositAmount, totalWithdrawalAmount);
    }

    private TransactionSummary summarizeAccountTransactions(Long accountId, TransactionDateRange dateRange) {
        // 총 입금액 계산
        BigDecimal totalDepositAmount = accountTransactionRepository.sumAmountByAccountIdAndTypes(
                accountId,
                DEPOSIT_TYPES,
                dateRange.startDateTime(),
                dateRange.endDateTime());
        // 총 출금액 계산
        BigDecimal totalWithdrawalAmount = accountTransactionRepository.sumAmountByAccountIdAndTypes(
                accountId,
                WITHDRAWAL_TYPES,
                dateRange.startDateTime(),
                dateRange.endDateTime());

        return new TransactionSummary(totalDepositAmount, totalWithdrawalAmount);
    }

    // 조회 기간 설정
    private TransactionPeriod getSafePeriod(TransactionPeriod period) {
        if (period == null) {
            return TransactionPeriod.ONE_MONTH;
        }

        return period;
    }

    // 이체한도 업데이트
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

        // 계좌이체의 경우 입금 계좌와 출금 계좌 모두에 대해서 lock을 획득해야 한다.
        // 이때 lock을 획득하는 순서를 지정하지 않으면 deadlock이 발생할 수 있기 때문에 AccountId가 작은 순서대로 lock을 획득하도록 강제한다
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
        // 거래 내역을 Transfer에 저장하고, 각 계좌입장에서의 거래 내역을 각각 저장한다.
        transferRepository.save(transfer);
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

    // record는 두 객체를 묶을 때 사용한다.
    private record TransactionDateRange(LocalDateTime startDateTime, LocalDateTime endDateTime) {
    }

    private record LockedTransferAccounts(Account fromAccount, Account toAccount) {
    }
}
