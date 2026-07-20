package com.finmate.service.investment;

import com.finmate.domain.investment.CurrencyCode;
import com.finmate.domain.investment.Investment;
import com.finmate.domain.investment.InvestmentCashBalance;
import com.finmate.domain.investment.SecuritiesCompanyCode;
import com.finmate.domain.investment.cash.exchange.InvestmentCurrencyExchangeTransaction;
import com.finmate.domain.investment.dto.exchange.InvestmentCurrencyExchangePageInfo;
import com.finmate.domain.investment.dto.exchange.InvestmentCurrencyExchangeRequest;
import com.finmate.domain.investment.dto.exchange.InvestmentCurrencyExchangeTransactionPageInfo;
import com.finmate.domain.market.MarketIndicatorSymbol;
import com.finmate.domain.market.dto.MarketRealtimeMessage;
import com.finmate.domain.normal.account.transaction.TransactionPeriod;
import com.finmate.global.pagination.PaginationInfo;
import com.finmate.repository.investment.InvestmentCashBalanceRepository;
import com.finmate.repository.investment.InvestmentRepository;
import com.finmate.repository.investment.cash.exchange.InvestmentCurrencyExchangeTransactionRepository;
import com.finmate.service.market.MarketRealtimeQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.finmate.global.validation.NumericValidator.validatePositive;
import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 실시간 환율을 기반으로 환전을 수행하는 서비스
@Service
@RequiredArgsConstructor
public class InvestmentCurrencyExchangeService {
    private static final int TRANSACTION_PAGE_SIZE = 20;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final InvestmentRepository investmentRepository; // 증권계좌를 저장하고 있는 레포지터리
    private final InvestmentCashBalanceRepository investmentCashBalanceRepository; // 각 증권계좌의 통화별 잔액을 저장하고 있는 레포지터리
    private final InvestmentCurrencyExchangeTransactionRepository exchangeTransactionRepository; // 증권계좌의 환전내역을 저장하고 있는 레포지터리
    private final MarketRealtimeQuoteService marketRealtimeQuoteService; // 최신 환율 정보를 redis에서 꺼내거나, KIS API를 호출하여 Redis를 갱신하는 서비스

    // 환전 페이지에 필요한 정보를 InvestmentCurrencyExchangePageInfo dto에 담아서 리턴하는 메서드
    @Transactional(readOnly = true)
    public InvestmentCurrencyExchangePageInfo getCurrencyExchangePageInfo(Long userId,
                                                                          InvestmentCurrencyExchangeRequest request) {
        if (request == null) {
            request = new InvestmentCurrencyExchangeRequest();
        }
        // 환전 전 통화와 환전 후 통화 기본값을 각각 원화와 달러로 설정하는 메서드
        applyDefaultCurrencyPair(request);

        List<Investment> investments = investmentRepository.findByUserIdWithCashBalances(userId);
        // 사용자가 선택한 증권계좌가 있으면 selectedInvestment 설정
        Investment selectedInvestment = selectInvestment(investments, request.getInvestmentId());
        return new InvestmentCurrencyExchangePageInfo(
                request,
                investments,
                selectedInvestment,
                // 현재 환율정보를 담는다.(현재 환율정보가 없으면 null)
                findCurrentUsdKrwExchangeRateOrNull());
    }

    // 환전할 증권계좌가 선택되었을때, 해당 증권계좌가 미리 선택된 환전 페이지에 전달할 정보들을 InvestmentCurrencyExchangeRequest dto에 담는다.
    @Transactional(readOnly = true)
    public InvestmentCurrencyExchangeRequest prepareCurrencyExchange(Long userId,
                                                                     String investmentNumber,
                                                                     SecuritiesCompanyCode securitiesCompanyCode) {
        validateRequired(investmentNumber, "증권 계좌번호는 필수입니다.");
        validateRequired(securitiesCompanyCode, "증권사는 필수입니다.");

        Investment investment = investmentRepository
                .findByUser_IdAndAccountNumberAndSecuritiesCompanyCode(userId, investmentNumber, securitiesCompanyCode)
                .orElseThrow(() -> new RuntimeException("현재 사용자의 증권 계좌가 아닙니다."));

        InvestmentCurrencyExchangeRequest request = new InvestmentCurrencyExchangeRequest();
        request.setInvestmentId(investment.getId());
        request.setSecuritiesCompanyCode(investment.getSecuritiesCompanyCode());
        request.setFromCurrencyCode(CurrencyCode.KRW);
        request.setToCurrencyCode(CurrencyCode.USD);
        return request;
    }

    // 실제 사용자 입력값을 기반으로 환전을 처리하는 메서드 (사용자가 입력한 정보는 InvestmentCurrencyExchangeRequest dto에 담겨있다.)
    @Transactional
    public InvestmentCurrencyExchangeTransaction exchangeCurrency(Long userId,
                                                                  InvestmentCurrencyExchangeRequest request) {
        validateRequired(request, "환전 요청은 필수입니다.");
        validateRequired(request.getInvestmentId(), "환전할 증권 계좌는 필수입니다.");
        validateRequired(request.getSecuritiesCompanyCode(), "증권사는 필수입니다.");
        CurrencyCode fromCurrencyCode = request.getFromCurrencyCode(); // 환전 전 통화 (A 통화)
        CurrencyCode toCurrencyCode = request.getToCurrencyCode(); // 환전 후 통화 (B 통화)
        BigDecimal fromAmount = request.getFromAmount(); // 환전 금액 (A 통화 기준)
        validateExchangeRequest(fromCurrencyCode, toCurrencyCode, fromAmount);

        BigDecimal exchangeRate = getCurrentUsdKrwExchangeRate(); // 실시간 환율 데이터
        BigDecimal toAmount = calculateToAmount(fromCurrencyCode, toCurrencyCode, fromAmount, exchangeRate); // 환전 금액 (B 통화 기준)
        validateExchangeResult(toCurrencyCode, toAmount);

        // 환전할 증권계좌를 레파지터리에서 조회
        Investment investment = investmentRepository.findByIdForUpdate(request.getInvestmentId())
                .orElseThrow(() -> new RuntimeException("증권 계좌를 찾을 수 없습니다."));
        validateOwnedInvestment(userId, investment);
        validateSecuritiesCompanyCode(request.getSecuritiesCompanyCode(), investment);

        // InvestmentCashBalance에 데드락을 방지하기 위해 1. KRW, 2. USD 순서로 통화에 lock을 걸고, record에 담아서 리턴한다.
        LockedCurrencyBalances lockedCurrencyBalances = lockKrwThenUsd(investment.getId());
        InvestmentCashBalance fromCashBalance = lockedCurrencyBalances.get(fromCurrencyCode);
        InvestmentCashBalance toCashBalance = lockedCurrencyBalances.get(toCurrencyCode);

        // 현재 증권 계좌의 A 통화와 B 통화에 lock을 걸고, 현재 거래가능한 통화 잔고가 얼마인지를 반환한다.
        BigDecimal fromBalanceBeforeExchange = fromCashBalance.getAvailableBalance();
        BigDecimal toBalanceBeforeExchange = toCashBalance.getAvailableBalance();

        // A 통화 잔고에서 환전금액을 출금한다.(fromAmount: A 통화 기준 환전금액)
        fromCashBalance.withdraw(fromAmount);
        // B 통화 잔고에서 환전금액을 입금한다. (toAmount: B 통화 기준 환전금액)
        toCashBalance.deposit(toAmount);

        // 환전내역을 InvestmentCurrencyExchangeTransaction 엔티티에 담아서 저장한다.
        InvestmentCurrencyExchangeTransaction transaction = InvestmentCurrencyExchangeTransaction.create(
                investment,
                fromCurrencyCode,
                fromAmount,
                toCurrencyCode,
                toAmount,
                exchangeRate,
                fromBalanceBeforeExchange,
                fromCashBalance.getAvailableBalance(),
                toBalanceBeforeExchange,
                toCashBalance.getAvailableBalance());
        return exchangeTransactionRepository.save(transaction);
    }

    // 환전내역 페이지에 필요한 정보들을 InvestmentCurrencyExchangeTransactionPageInfo dto에 담아서 리턴한다.
    @Transactional(readOnly = true)
    public InvestmentCurrencyExchangeTransactionPageInfo getCurrencyExchangeTransactionPageInfo(Long userId,
                                                                                                Long investmentId,
                                                                                                TransactionPeriod period,
                                                                                                int page) {
        List<Investment> investments = investmentRepository.findByUserIdWithCashBalances(userId);
        Investment selectedInvestment = selectInvestment(investments, investmentId);
        TransactionPeriod safePeriod = TransactionPeriod.defaultIfNull(period);
        LocalDateTime endDateTime = LocalDateTime.now(SERVICE_ZONE);
        LocalDateTime startDateTime = safePeriod.getStartDateTime(endDateTime);
        PageRequest pageRequest = PageRequest.of(PaginationInfo.safePage(page), TRANSACTION_PAGE_SIZE);

        Page<InvestmentCurrencyExchangeTransaction> transactionPage = selectedInvestment == null
                // 선택된 증권계좌가 없으면 사용자의 모든 증권계좌에서 환전 내역을 받아온다.
                ? exchangeTransactionRepository.findAllByUserIdAndCreatedAtBetween(
                userId,
                startDateTime,
                endDateTime,
                pageRequest)
                // 선택된 증권계좌가 있으면 사용자의 해당 증권계좌에서 환전 내역을 받아온다.
                : exchangeTransactionRepository.findAllByUserIdAndInvestmentIdAndCreatedAtBetween(
                userId,
                selectedInvestment.getId(),
                startDateTime,
                endDateTime,
                pageRequest);

        return new InvestmentCurrencyExchangeTransactionPageInfo(
                investments,
                selectedInvestment,
                safePeriod,
                TransactionPeriod.values(),
                transactionPage);
    }

    // 환전 요청 검증
    private void validateExchangeRequest(CurrencyCode fromCurrencyCode,
                                         CurrencyCode toCurrencyCode,
                                         BigDecimal fromAmount) {
        validateRequired(fromCurrencyCode, "환전 전 통화는 필수입니다.");
        validateRequired(toCurrencyCode, "환전 후 통화는 필수입니다.");
        validateSupportedCurrencyPair(fromCurrencyCode, toCurrencyCode);
        fromCurrencyCode.validateAmountScale(fromAmount);
        validatePositive(fromAmount, "환전 금액은 0보다 커야 합니다.");
    }

    // request의 환전 전 통화와 환전 후 통화의 기본값을 각각 원화와 달러로 설정
    private void applyDefaultCurrencyPair(InvestmentCurrencyExchangeRequest request) {
        if (request.getFromCurrencyCode() == null) {
            request.setFromCurrencyCode(CurrencyCode.KRW);
        }

        if (request.getToCurrencyCode() == null) {
            request.setToCurrencyCode(CurrencyCode.USD);
        }
    }

    // 환전 통화 검증
    private void validateSupportedCurrencyPair(CurrencyCode fromCurrencyCode, CurrencyCode toCurrencyCode) {
        if (fromCurrencyCode == toCurrencyCode) {
            throw new RuntimeException("같은 통화로는 환전할 수 없습니다.");
        }

        boolean supportedPair = (fromCurrencyCode == CurrencyCode.KRW && toCurrencyCode == CurrencyCode.USD)
                || (fromCurrencyCode == CurrencyCode.USD && toCurrencyCode == CurrencyCode.KRW);
        if (!supportedPair) {
            throw new RuntimeException("현재 KRW/USD 환전만 지원합니다.");
        }
    }

    // 환전 금액 계산 (B 통화 기준)
    private BigDecimal calculateToAmount(CurrencyCode fromCurrencyCode,
                                         CurrencyCode toCurrencyCode,
                                         BigDecimal fromAmount, // A 통화 기준 환전 금액
                                         // 실시간 환율
                                         BigDecimal exchangeRate ) {
        if (fromCurrencyCode == CurrencyCode.KRW && toCurrencyCode == CurrencyCode.USD) {
            // 만약 A 통화: KRW, B 통화: USD라면, B 통화 기준 환전 금액은 (fromAmount / 환율) 이 된다.
            return fromAmount.divide(exchangeRate, CurrencyCode.USD.getFractionDigits(), RoundingMode.DOWN);
        }

        // 만약 A 통화: USD, B 통화: KRW라면, B 통화 기준 환전 금액은 (fromAmount * 환율) 이 된다.
        return fromAmount.multiply(exchangeRate)
                .setScale(CurrencyCode.KRW.getFractionDigits(), RoundingMode.DOWN);
    }

    // 환전이 성공적으로 수행됐는지 검사
    private void validateExchangeResult(CurrencyCode toCurrencyCode, BigDecimal toAmount) {
        toCurrencyCode.validateAmountScale(toAmount);
        if (toAmount.compareTo(toCurrencyCode.getMinimumAmount()) < 0) {
            throw new RuntimeException("환전 후 금액이 최소 단위보다 작습니다.");
        }
    }

    // 현재 환율데이터를 조회하는 메서드
    private BigDecimal getCurrentUsdKrwExchangeRate() {
        // marketRealtimeQuoteService를 통해 현재 환율 데이터를 받아오고, 받아오지 못하면 예외를 발생시킨다.
        // 이때 getLatest메서드는 Redis에서 실시간 환율 데이터를 꺼내오고, 만약 Redis에 환율 데이터가 없으면 KIS API를 호출하여 실시간 환율 데이터를 받아서 Redis에 저장하고, 이를 반환한다.
        MarketRealtimeMessage message = marketRealtimeQuoteService.getLatest(MarketIndicatorSymbol.USD_KRW)
                .orElseThrow(() -> new RuntimeException("USD/KRW 환율을 조회할 수 없습니다."));
        BigDecimal exchangeRate = message.currentPrice(); //MargetRealtimeMessage에서 환율정보만 추출한다.
        validatePositive(exchangeRate, "USD/KRW 환율을 조회할 수 없습니다.");
        return exchangeRate.setScale(10, RoundingMode.HALF_UP);
    }

    // 현재 환율데이터를 받고, 예외가 발생하면 null을 리턴한다.
    private BigDecimal findCurrentUsdKrwExchangeRateOrNull() {
        try {
            return getCurrentUsdKrwExchangeRate();
        } catch (RuntimeException e) {
            return null;
        }
    }
    // 사용자가 선택한 증권계좌가 없으면 null 리턴, 있으면 해당 증권계좌 리턴
    private Investment selectInvestment(List<Investment> investments, Long investmentId) {
        if (investmentId == null) {
            return null;
        }

        return investments.stream()
                .filter(investment -> investment.getId().equals(investmentId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("현재 사용자의 증권 계좌가 아닙니다."));
    }

    private void validateOwnedInvestment(Long userId, Investment investment) {
        if (!investment.getUser().getId().equals(userId)) {
            throw new RuntimeException("현재 사용자의 증권 계좌가 아닙니다.");
        }
    }

    private void validateSecuritiesCompanyCode(SecuritiesCompanyCode securitiesCompanyCode, Investment investment) {
        if (!investment.getSecuritiesCompanyCode().equals(securitiesCompanyCode)) {
            throw new RuntimeException("증권 계좌의 증권사 정보가 일치하지 않습니다.");
        }
    }

    // 환전을 위해 InvestmentCashBalance에 lock을 걸때에는 데드락을 방지하기 위해 항상 KRW -> USD 순서로 lock을 획득한다.
    private LockedCurrencyBalances lockKrwThenUsd(Long investmentId) {
        InvestmentCashBalance krwBalance = findCashBalanceForUpdate(investmentId, CurrencyCode.KRW);
        InvestmentCashBalance usdBalance = findCashBalanceForUpdate(investmentId, CurrencyCode.USD);
        return new LockedCurrencyBalances(krwBalance, usdBalance);
    }

    // InvestmentCashBalanceRepository에서 lock을 획득한채 InvestmentCashBalance를 조회한다.
    private InvestmentCashBalance findCashBalanceForUpdate(Long investmentId, CurrencyCode currencyCode) {
        return investmentCashBalanceRepository.findByInvestmentIdAndCurrencyCodeForUpdate(investmentId, currencyCode)
                .orElseThrow(() -> new RuntimeException(currencyCode.name() + " 예수금 잔고가 없습니다."));
    }

    // 조회한 KRW Balance와 USD Balance를 저장하고 있는 레코드
    private record LockedCurrencyBalances(InvestmentCashBalance krwBalance,
                                          InvestmentCashBalance usdBalance) {
        InvestmentCashBalance get(CurrencyCode currencyCode) {
            return switch (currencyCode) {
                case KRW -> krwBalance;
                case USD -> usdBalance;
            };
        }
    }
}
