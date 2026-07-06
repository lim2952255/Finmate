package com.finmate.domain.normal.account.transaction;


public enum AccountTransactionType {
    // 일반 계좌간 거래
    TRANSFER_IN,
    TRANSFER_OUT,
    // 증권 계좌 - 일반 계좌 간 거래
    DEPOSIT,
    WITHDRAW
}
