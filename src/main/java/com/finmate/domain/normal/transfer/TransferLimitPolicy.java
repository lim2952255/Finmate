package com.finmate.domain.normal.transfer;

import com.finmate.domain.investment.CurrencyCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Arrays;

// 통화별 이체한도 정책 관리
@Getter
public enum TransferLimitPolicy {
    // 원화
    KRW_LIMIT(
            CurrencyCode.KRW,
            BigDecimal.valueOf(1_000_000),
            BigDecimal.valueOf(5_000_000)
    ),
    // 달러
    USD_LIMIT(
            CurrencyCode.USD,
            BigDecimal.valueOf(1_000),
            BigDecimal.valueOf(5_000)
    ),
    // 엔화
    JPY_LIMIT(
            CurrencyCode.JPY,
            BigDecimal.valueOf(100_000),
            BigDecimal.valueOf(500_000)
    ),
    // 유로
    EUR_LIMIT(
            CurrencyCode.EUR,
            BigDecimal.valueOf(1_000),
            BigDecimal.valueOf(5_000)
    );

    private final CurrencyCode currencyCode;
    private final BigDecimal singleTransferLimit;
    private final BigDecimal dailyTransferLimit;

    TransferLimitPolicy(CurrencyCode currencyCode,
                        BigDecimal singleTransferLimit,
                        BigDecimal dailyTransferLimit) {
        this.currencyCode = currencyCode;
        this.singleTransferLimit = singleTransferLimit;
        this.dailyTransferLimit = dailyTransferLimit;
    }
    // 통화 코드를 입력받으면, 해당 통화코드에 맞는 이체한도 정책을 리턴하는 메서드
    public static TransferLimitPolicy from(CurrencyCode currencyCode) {
        return Arrays.stream(values())
                .filter(policy -> policy.currencyCode == currencyCode)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("지원하지 않는 통화입니다."));
    }
}