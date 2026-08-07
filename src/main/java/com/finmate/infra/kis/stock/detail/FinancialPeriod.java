package com.finmate.infra.kis.stock.detail;

// KIS API 재무 요청 기간 구분 enum
public enum FinancialPeriod {
    ANNUAL("0"), // 연간
    QUARTERLY("1"); // 분기

    private final String kisCode;

    FinancialPeriod(String kisCode) {
        this.kisCode = kisCode;
    }

    public String kisCode() {
        return kisCode;
    }
}
