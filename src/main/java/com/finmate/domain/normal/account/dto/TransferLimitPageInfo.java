package com.finmate.domain.normal.account.dto;

import com.finmate.domain.normal.account.Account;
import lombok.Getter;

import java.util.List;

// 이체한도 변경 화면에 필요한 정보 DTO
@Getter
public class TransferLimitPageInfo {
    private final List<Account> accounts;
    private final Account selectedAccount;
    private final TransferLimitInfo transferLimitInfo;

    public TransferLimitPageInfo(List<Account> accounts,
                                 Account selectedAccount,
                                 TransferLimitInfo transferLimitInfo) {
        this.accounts = accounts;
        this.selectedAccount = selectedAccount;
        this.transferLimitInfo = transferLimitInfo;
    }
}
