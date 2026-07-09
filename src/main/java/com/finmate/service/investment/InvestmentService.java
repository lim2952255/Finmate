package com.finmate.service.investment;


import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.InvestmentCashBalance;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.investment.dto.InvestmentHomeInfo;
import com.finmate.domain.investment.dto.OpenInvestment;
import com.finmate.domain.investment.dto.PrimaryInvestment;
import com.finmate.domain.investment.dto.cash.InvestmentDepositPageInfo;
import com.finmate.domain.investment.dto.cash.InvestmentDepositRequest;
import com.finmate.domain.investment.dto.cash.InvestmentWithdrawalRequest;
import com.finmate.domain.investment.dto.cash.InvestmentWithdrawalPageInfo;
import com.finmate.domain.investment.dto.cash.SecuritiesCashTransactionPageInfo;
import com.finmate.domain.investment.cash.transaction.SecuritiesCashTransaction;
import com.finmate.domain.investment.cash.transaction.SecuritiesCashTransactionType;
import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.account.AccountType;
import com.finmate.domain.normal.account.BankCode;
import com.finmate.domain.normal.account.transaction.AccountTransaction;
import com.finmate.domain.normal.account.transaction.AccountTransactionType;
import com.finmate.domain.normal.account.transaction.TransactionPeriod;
import com.finmate.domain.normal.account.transaction.dto.TransactionSummary;
import com.finmate.domain.normal.transfer.Transfer;
import com.finmate.domain.user.User;
import com.finmate.global.pagination.PaginationInfo;
import com.finmate.repository.investment.InvestmentCashBalanceRepository;
import com.finmate.repository.investment.InvestmentRepository;
import com.finmate.repository.investment.cash.transaction.SecuritiesCashTransactionRepository;
import com.finmate.repository.normal.account.AccountRepository;
import com.finmate.repository.normal.account.transaction.AccountTransactionRepository;
import com.finmate.repository.normal.transfer.TransferRepository;
import com.finmate.service.normal.account.AccountNumberRegistryService;
import com.finmate.service.normal.transfer.TransferLimitUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvestmentService {
    private static final int MAX_INVESTMENT_ACCOUNT_COUNT = 10;
    private static final int TRANSACTION_PAGE_SIZE = 20;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final InvestmentRepository investmentRepository;
    private final AccountNumberRegistryService accountNumberRegistryService;
    private final AccountRepository accountRepository;
    private final InvestmentCashBalanceRepository investmentCashBalanceRepository;
    private final TransferRepository transferRepository;
    private final AccountTransactionRepository accountTransactionRepository;
    private final SecuritiesCashTransactionRepository securitiesCashTransactionRepository;
    private final TransferLimitUsageService transferLimitUsageService;

    private static final List<SecuritiesCashTransactionType> DEPOSIT_TYPES = List.of(
            SecuritiesCashTransactionType.DEPOSIT);
    private static final List<SecuritiesCashTransactionType> WITHDRAWAL_TYPES = List.of(
            SecuritiesCashTransactionType.WITHDRAW);

    // user의 모든 증권 계좌 리턴
    @Transactional(readOnly = true)
    public List<Investment> findInvestments(Long userId) {
        return investmentRepository.findByUserIdWithCashBalances(userId);
    }

    // 증권 홈페이지에 총 금액, 대표계좌, 계좌수 정보를 dto에 담아서 리턴
    @Transactional(readOnly = true)
    public InvestmentHomeInfo getInvestmentHomeInfo(Long userId) {
        List<Investment> investments = findInvestments(userId);
        Map<CurrencyCode, BigDecimal> totalDepositBalancesByCurrency =
                summarizeInvestmentCashBalancesByCurrency(investments);
        PrimaryInvestment primaryInvestment = investments.stream()
                .filter(Investment::isPrimary)
                .findFirst()
                .map(PrimaryInvestment::new)
                .orElse(null);

        return new InvestmentHomeInfo(
                investments,
                primaryInvestment,
                totalDepositBalancesByCurrency,
                investments.size());
    }

    // 일반 계좌 -> 증권 계좌로 예수금 입금을 위해 dto에 정보를 담는다.
    @Transactional(readOnly = true)
    public InvestmentDepositRequest prepareInvestmentDeposit(Long userId, String fromAccountNumber, BankCode fromBankCode) {
        InvestmentDepositRequest investmentDepositRequest = new InvestmentDepositRequest();

        if (fromAccountNumber == null || fromAccountNumber.isBlank() || fromBankCode == null) {
            return investmentDepositRequest;
        }

        Account fromAccount = findOwnedAccount(userId, fromAccountNumber, fromBankCode);

        investmentDepositRequest.setFromAccountId(fromAccount.getId());
        investmentDepositRequest.setFromBankCode(fromAccount.getBankCode());

        return investmentDepositRequest;
    }

    // 일반 계좌 -> 증권 계좌 예수금 이체 페이지 정보를 dto에 담아서 리턴
    @Transactional(readOnly = true)
    public InvestmentDepositPageInfo getInvestmentDepositPageInfo(Long userId,
                                                                  InvestmentDepositRequest investmentDepositRequest) {
        List<Investment> investments = findInvestments(userId);
        List<Account> accounts = accountRepository.findByUser_Id(userId);
        Account fromAccount = null;

        if (investmentDepositRequest.getFromAccountId() != null) {
            fromAccount = accounts.stream()
                    .filter(account -> account.getId().equals(investmentDepositRequest.getFromAccountId()))
                    .findFirst()
                    .orElse(null);
        }

        return new InvestmentDepositPageInfo(investmentDepositRequest, accounts, investments, fromAccount);
    }

    @Transactional(readOnly = true)
    public InvestmentWithdrawalPageInfo getInvestmentWithdrawalPageInfo(Long userId) {
        return getInvestmentWithdrawalPageInfo(userId, new InvestmentWithdrawalRequest());
    }

    // 증권 계좌 -> 일반 계좌로 투자금 이체 정보를 담은 dto 리턴
    @Transactional(readOnly = true)
    public InvestmentWithdrawalRequest prepareInvestmentWithdrawal(Long userId,
                                                                  String fromInvestmentNumber,
                                                                  SecuritiesCompanyCode fromSecuritiesCompanyCode) {
        InvestmentWithdrawalRequest investmentWithdrawalRequest = new InvestmentWithdrawalRequest();

        if (fromInvestmentNumber == null || fromInvestmentNumber.isBlank() || fromSecuritiesCompanyCode == null) {
            return investmentWithdrawalRequest;
        }

        Investment fromInvestment = findOwnedInvestment(userId, fromInvestmentNumber, fromSecuritiesCompanyCode);

        investmentWithdrawalRequest.setFromInvestmentId(fromInvestment.getId());
        investmentWithdrawalRequest.setFromSecuritiesCompanyCode(fromInvestment.getSecuritiesCompanyCode());

        return investmentWithdrawalRequest;
    }

    @Transactional(readOnly = true)
    public InvestmentWithdrawalPageInfo getInvestmentWithdrawalPageInfo(Long userId,
                                                                        InvestmentWithdrawalRequest investmentWithdrawalRequest) {
        List<Investment> investments = findInvestments(userId);
        List<Account> accounts = accountRepository.findByUser_Id(userId);
        Investment fromInvestment = null;

        if (investmentWithdrawalRequest.getFromInvestmentId() != null) {
            fromInvestment = investments.stream()
                    .filter(investment -> investment.getId().equals(investmentWithdrawalRequest.getFromInvestmentId()))
                    .findFirst()
                    .orElse(null);
        }

        return new InvestmentWithdrawalPageInfo(investmentWithdrawalRequest, investments, accounts, fromInvestment);
    }

    // user의 특정 증권 계좌 리턴
    private Investment findOwnedInvestment(Long userId,
                                           String accountNumber,
                                           SecuritiesCompanyCode securitiesCompanyCode) {
        return investmentRepository
                .findByUser_IdAndAccountNumberAndSecuritiesCompanyCode(userId, accountNumber, securitiesCompanyCode)
                .orElseThrow(() -> new RuntimeException("현재 사용자의 증권 계좌가 아닙니다."));
    }

    private Account findOwnedAccount(Long userId, String accountNumber, BankCode bankCode) {
        return accountRepository.findByUser_IdAndAccountNumberAndBankCode(userId, accountNumber, bankCode)
                .orElseThrow(() -> new RuntimeException("현재 사용자의 계좌가 아닙니다."));
    }

    // 특정 기간동안의 사용자의 모든 예수금 이체 내역을 Page단위로 리턴
    private Page<SecuritiesCashTransaction> findSecuritiesCashTransactions(Long userId,
                                                                           TransactionPeriod period,
                                                                           int page) {
        TransactionPeriod safePeriod = TransactionPeriod.defaultIfNull(period);
        LocalDateTime endDateTime = LocalDateTime.now(SERVICE_ZONE);
        LocalDateTime startDateTime = safePeriod.getStartDateTime(endDateTime);

        return securitiesCashTransactionRepository.findAllByUserIdAndCreatedAtBetween(
                userId,
                startDateTime,
                endDateTime,
                PageRequest.of(PaginationInfo.safePage(page), TRANSACTION_PAGE_SIZE));
    }

    // 사용자의 전체 계좌를 기준으로 총 예수금 입금액 / 총 예수금 출금액 계산
    private TransactionSummary summarizeSecuritiesCashTransactions(Long userId, TransactionPeriod period) {
        TransactionPeriod safePeriod = TransactionPeriod.defaultIfNull(period);
        LocalDateTime endDateTime = LocalDateTime.now(SERVICE_ZONE);
        LocalDateTime startDateTime = safePeriod.getStartDateTime(endDateTime);

        BigDecimal totalDepositAmount = securitiesCashTransactionRepository.sumAmountByUserIdAndTypes(
                userId,
                DEPOSIT_TYPES,
                startDateTime,
                endDateTime);
        BigDecimal totalWithdrawalAmount = securitiesCashTransactionRepository.sumAmountByUserIdAndTypes(
                userId,
                WITHDRAWAL_TYPES,
                startDateTime,
                endDateTime);

        return new TransactionSummary(totalDepositAmount, totalWithdrawalAmount);
    }

    // 예수금 이체내역 페이지정보를 dto에 담아서 리턴
    @Transactional(readOnly = true)
    public SecuritiesCashTransactionPageInfo getSecuritiesCashTransactionPageInfo(Long userId,
                                                                                  String investmentNumber,
                                                                                  SecuritiesCompanyCode securitiesCompanyCode,
                                                                                  TransactionPeriod period,
                                                                                  int page) {
        List<Investment> investments = findInvestments(userId);
        TransactionPeriod safePeriod = TransactionPeriod.defaultIfNull(period);
        int safePage = PaginationInfo.safePage(page);

        Investment selectedInvestment = null;
        Page<SecuritiesCashTransaction> transactionPage;
        TransactionSummary transactionSummary;

        if (investmentNumber == null || investmentNumber.isBlank() || securitiesCompanyCode == null) {
            transactionPage = findSecuritiesCashTransactions(userId, safePeriod, safePage);
            transactionSummary = summarizeSecuritiesCashTransactions(userId, safePeriod);
        } else {
            selectedInvestment = findOwnedInvestment(userId, investmentNumber, securitiesCompanyCode);
            transactionPage = findSecuritiesCashTransactions(
                    userId,
                    investmentNumber,
                    securitiesCompanyCode,
                    safePeriod,
                    safePage);
            transactionSummary = summarizeSecuritiesCashTransactions(
                    userId,
                    investmentNumber,
                    securitiesCompanyCode,
                    safePeriod);
        }

        return new SecuritiesCashTransactionPageInfo(
                investments,
                selectedInvestment,
                safePeriod,
                TransactionPeriod.values(),
                transactionPage,
                transactionSummary);
    }

    // 특정 증권 계좌의 예수금 이체 내역을 page단위로 조회
    private Page<SecuritiesCashTransaction> findSecuritiesCashTransactions(Long userId,
                                                                           String investmentNumber,
                                                                           SecuritiesCompanyCode securitiesCompanyCode,
                                                                           TransactionPeriod period,
                                                                           int page) {
        Investment investment = findOwnedInvestment(userId, investmentNumber, securitiesCompanyCode);
        TransactionPeriod safePeriod = TransactionPeriod.defaultIfNull(period);
        LocalDateTime endDateTime = LocalDateTime.now(SERVICE_ZONE);
        LocalDateTime startDateTime = safePeriod.getStartDateTime(endDateTime);

        return securitiesCashTransactionRepository.findAllByInvestmentIdAndCreatedAtBetween(
                investment.getId(),
                startDateTime,
                endDateTime,
                PageRequest.of(PaginationInfo.safePage(page), TRANSACTION_PAGE_SIZE));
    }

    // 특정 계좌의 총 예수금 입금액 / 출금액을 계산하여 리턴
    private TransactionSummary summarizeSecuritiesCashTransactions(Long userId,
                                                                   String investmentNumber,
                                                                   SecuritiesCompanyCode securitiesCompanyCode,
                                                                   TransactionPeriod period) {
        Investment investment = findOwnedInvestment(userId, investmentNumber, securitiesCompanyCode);
        TransactionPeriod safePeriod = TransactionPeriod.defaultIfNull(period);
        LocalDateTime endDateTime = LocalDateTime.now(SERVICE_ZONE);
        LocalDateTime startDateTime = safePeriod.getStartDateTime(endDateTime);

        BigDecimal totalDepositAmount = securitiesCashTransactionRepository.sumAmountByInvestmentIdAndTypes(
                investment.getId(),
                DEPOSIT_TYPES,
                startDateTime,
                endDateTime);
        BigDecimal totalWithdrawalAmount = securitiesCashTransactionRepository.sumAmountByInvestmentIdAndTypes(
                investment.getId(),
                WITHDRAWAL_TYPES,
                startDateTime,
                endDateTime);

        return new TransactionSummary(totalDepositAmount, totalWithdrawalAmount);
    }

    // 증권 계좌 개설
    @Transactional
    public Long openInvestment(OpenInvestment openInvestment, User user) {
        long investmentAccountCount = investmentRepository.countByUser_Id(user.getId());
        if (investmentAccountCount >= MAX_INVESTMENT_ACCOUNT_COUNT) {
            throw new RuntimeException("증권 계좌는 최대 10개까지만 개설할 수 있습니다.");
        }

        String accountNumber = accountNumberRegistryService.issueUniqueAccountNumber(AccountType.INVESTMENT);
        Investment investment = Investment.create(
                user,
                accountNumber,
                openInvestment.getSecuritiesCompanyCode());

        Investment savedInvestment = investmentRepository.save(investment);
        return savedInvestment.getId();
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
    // 일반 계좌 -> 증권 계좌로 예수금 입금
    @Transactional
    public void depositToInvestment(InvestmentDepositRequest investmentDepositRequest, Long userId) {
        BigDecimal amount = investmentDepositRequest.getAmount();

        // 계좌에 lock을 획득할때 데드락 방지를 위해 항상 일반계좌 먼저 lock을 획득하도록 한다.
        LockedAccountAndInvestment lockedAccountAndInvestment = lockAccountAndInvestmentAvoidingDeadlock(
                investmentDepositRequest.getFromAccountId(),
                investmentDepositRequest.getToInvestmentId());
        Account fromAccount = lockedAccountAndInvestment.account();
        Investment toInvestment = lockedAccountAndInvestment.investment();

        if (!fromAccount.getUser().getId().equals(userId)) {
            throw new RuntimeException("출금 계좌가 현재 사용자의 계좌가 아닙니다.");
        }

        if (!toInvestment.getUser().getId().equals(userId)) {
            throw new RuntimeException("입금할 증권 계좌가 현재 사용자의 계좌가 아닙니다.");
        }

        validateInvestmentDepositCode(investmentDepositRequest, fromAccount, toInvestment);

        // 일반 계좌가 사용하는 통화 정보를 얻고, 증권 계좌의 해당 통화 잔고를 업데이트한다.
        CurrencyCode currencyCode = fromAccount.getCurrencyCode();
        InvestmentCashBalance cashBalance = findOrCreateCashBalanceForUpdate(toInvestment, currencyCode);
        transferLimitUsageService.use(fromAccount, amount);

        BigDecimal accountBalanceBeforeTransaction = fromAccount.getBalance();
        BigDecimal investmentBalanceBeforeTransaction = cashBalance.getAvailableBalance();

        fromAccount.withdraw(amount); // 일반 계좌에서 해당 금액만큼을 출금
        cashBalance.deposit(amount); // 증권 계좌의 해당 통화 잔고에서 해당 금액 만큼을 입금

        // UUID를 사용하면 사실상 중복 x + 유니크 제약조건이 있어서 괜찮다.
        // 오류가 발생해도 트랜잭션이 롤백되기 때문에 문제가 발생하지 않는다.
        Transfer transfer = Transfer.createInvestmentDeposit(
                UUID.randomUUID().toString(),
                fromAccount,
                toInvestment,
                currencyCode,
                amount);
        transferRepository.save(transfer);

        AccountTransaction withdrawalTransaction = AccountTransaction.createWithSecuritiesCounterparty(
                fromAccount,
                transfer,
                AccountTransactionType.WITHDRAW,
                amount,
                accountBalanceBeforeTransaction,
                fromAccount.getBalance(),
                toInvestment.getSecuritiesCompanyCode(),
                toInvestment.getAccountNumber(),
                fromAccount.getUser().getUsername(),
                "증권계좌 예수금 입금");

        SecuritiesCashTransaction depositTransaction = SecuritiesCashTransaction.create(
                toInvestment,
                transfer,
                SecuritiesCashTransactionType.DEPOSIT,
                amount,
                investmentBalanceBeforeTransaction,
                cashBalance.getAvailableBalance(),
                fromAccount.getBankCode(),
                fromAccount.getAccountNumber(),
                fromAccount.getUser().getUsername(),
                "증권계좌 예수금 입금");

        accountTransactionRepository.save(withdrawalTransaction);
        securitiesCashTransactionRepository.save(depositTransaction);
    }

    // 증권 계좌 -> 일반 계좌 예수금 출금
    @Transactional
    public void withdrawFromInvestment(InvestmentWithdrawalRequest investmentWithdrawalRequest, Long userId) {
        BigDecimal amount = investmentWithdrawalRequest.getAmount();

        // 계좌에 lock을 획득할때 데드락 방지를 위해 항상 일반계좌 먼저 lock을 획득하도록 한다.
        LockedAccountAndInvestment lockedAccountAndInvestment = lockAccountAndInvestmentAvoidingDeadlock(
                investmentWithdrawalRequest.getToAccountId(),
                investmentWithdrawalRequest.getFromInvestmentId());
        Account toAccount = lockedAccountAndInvestment.account();
        Investment fromInvestment = lockedAccountAndInvestment.investment();

        if (!toAccount.getUser().getId().equals(userId)) {
            throw new RuntimeException("입금 계좌가 현재 사용자의 계좌가 아닙니다.");
        }

        if (!fromInvestment.getUser().getId().equals(userId)) {
            throw new RuntimeException("출금할 증권 계좌가 현재 사용자의 계좌가 아닙니다.");
        }

        validateInvestmentWithdrawalCode(investmentWithdrawalRequest, fromInvestment, toAccount);

        // 일반 계좌가 사용하는 통화 정보를 얻고, 증권 계좌에서 해당 통화의 잔고 정보를 얻는다.
        CurrencyCode currencyCode = toAccount.getCurrencyCode();
        InvestmentCashBalance cashBalance = findOrCreateCashBalanceForUpdate(fromInvestment, currencyCode);

        BigDecimal accountBalanceBeforeTransaction = toAccount.getBalance();
        BigDecimal investmentBalanceBeforeTransaction = cashBalance.getAvailableBalance();

        cashBalance.withdraw(amount); // 증권 계좌에서 해당 통화의 잔고에서 해당 금액 만큼을 출금
        toAccount.deposit(amount); // 일반 계좌에 해당 금액 만큼을 입금

        Transfer transfer = Transfer.createInvestmentWithdrawal(
                UUID.randomUUID().toString(),
                fromInvestment,
                toAccount,
                currencyCode,
                amount);
        transferRepository.save(transfer);

        AccountTransaction depositTransaction = AccountTransaction.createWithSecuritiesCounterparty(
                toAccount,
                transfer,
                AccountTransactionType.DEPOSIT,
                amount,
                accountBalanceBeforeTransaction,
                toAccount.getBalance(),
                fromInvestment.getSecuritiesCompanyCode(),
                fromInvestment.getAccountNumber(),
                toAccount.getUser().getUsername(),
                "증권계좌 예수금 출금");

        SecuritiesCashTransaction withdrawalTransaction = SecuritiesCashTransaction.create(
                fromInvestment,
                transfer,
                SecuritiesCashTransactionType.WITHDRAW,
                amount,
                investmentBalanceBeforeTransaction,
                cashBalance.getAvailableBalance(),
                toAccount.getBankCode(),
                toAccount.getAccountNumber(),
                toAccount.getUser().getUsername(),
                "증권계좌 예수금 출금");

        accountTransactionRepository.save(depositTransaction);
        securitiesCashTransactionRepository.save(withdrawalTransaction);
    }

    // 일반계좌와 증권계좌 사이의 자금 이동은 입금/출금 방향과 관계없이 항상 일반계좌를 먼저 lock한다.
    // 양방향 이체 요청이 동시에 들어와도 lock 획득 순서가 같아야 서로 다른 순서로 row lock을 기다리는 데드락 가능성을 줄일 수 있다.
    private LockedAccountAndInvestment lockAccountAndInvestmentAvoidingDeadlock(Long accountId, Long investmentId) {
        Account lockedAccount = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new RuntimeException("계좌를 찾을 수 없습니다."));

        Investment lockedInvestment = investmentRepository.findByIdForUpdate(investmentId)
                .orElseThrow(() -> new RuntimeException("증권 계좌를 찾을 수 없습니다."));

        return new LockedAccountAndInvestment(lockedAccount, lockedInvestment);
    }

    private void validateInvestmentDepositCode(InvestmentDepositRequest investmentDepositRequest,
                                               Account fromAccount,
                                               Investment toInvestment) {
        BankCode fromBankCode = investmentDepositRequest.getFromBankCode();
        if (!fromAccount.getBankCode().equals(fromBankCode)) {
            throw new RuntimeException("출금 계좌의 은행 정보가 일치하지 않습니다.");
        }

        if (!toInvestment.getSecuritiesCompanyCode().equals(investmentDepositRequest.getToSecuritiesCompanyCode())) {
            throw new RuntimeException("입금 증권계좌의 증권사 정보가 일치하지 않습니다.");
        }
    }

    private void validateInvestmentWithdrawalCode(InvestmentWithdrawalRequest investmentWithdrawalRequest,
                                                  Investment fromInvestment,
                                                  Account toAccount) {
        if (!fromInvestment.getSecuritiesCompanyCode().equals(investmentWithdrawalRequest.getFromSecuritiesCompanyCode())) {
            throw new RuntimeException("출금 증권계좌의 증권사 정보가 일치하지 않습니다.");
        }

        if (!toAccount.getBankCode().equals(investmentWithdrawalRequest.getToBankCode())) {
            throw new RuntimeException("입금 계좌의 은행 정보가 일치하지 않습니다.");
        }
    }

    private InvestmentCashBalance findOrCreateCashBalanceForUpdate(Investment investment, CurrencyCode currencyCode) {
        return investmentCashBalanceRepository
                .findByInvestmentIdAndCurrencyCodeForUpdate(investment.getId(), currencyCode)
                .orElseGet(() -> investmentCashBalanceRepository.save(investment.addCashBalance(currencyCode)));
    }

    private Map<CurrencyCode, BigDecimal> summarizeInvestmentCashBalancesByCurrency(List<Investment> investments) {
        Map<CurrencyCode, BigDecimal> totalBalancesByCurrency = new EnumMap<>(CurrencyCode.class);
        for (Investment investment : investments) {
            for (InvestmentCashBalance cashBalance : investment.getCashBalances()) {
                totalBalancesByCurrency.merge(
                        cashBalance.getCurrencyCode(),
                        cashBalance.getAvailableBalance(),
                        BigDecimal::add);
            }
        }
        return totalBalancesByCurrency;
    }

    private record LockedAccountAndInvestment(Account account, Investment investment) {
    }
}
