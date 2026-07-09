package com.finmate.service.normal.transfer;

import com.finmate.domain.normal.account.Account;
import com.finmate.domain.normal.transfer.DailyTransferUsage;
import com.finmate.repository.normal.transfer.DailyTransferUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

// 일반 계좌에서 출금되는 이체의 일회/일일 이체한도 사용을 처리하는 서비스
@Service
@RequiredArgsConstructor
public class TransferLimitUsageService {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final DailyTransferUsageRepository dailyTransferUsageRepository;

    // 일일 or 일회 이체한도 초과여부 검사
    public void use(Account fromAccount, BigDecimal transferAmount) {
        if (transferAmount.compareTo(fromAccount.getSingleTransferLimit()) > 0) {
            throw new RuntimeException("일회 이체한도를 초과했습니다.");
        }

        LocalDate today = LocalDate.now(SERVICE_ZONE);
        DailyTransferUsage dailyTransferUsage = dailyTransferUsageRepository
                .findByAccountIdAndUsageDateForUpdate(fromAccount.getId(), today)
                .orElseGet(() -> dailyTransferUsageRepository.save(DailyTransferUsage.create(fromAccount, today)));

        // 일일 이체한도 검사 + 오늘 사용금액 증가
        dailyTransferUsage.use(transferAmount, fromAccount.getDailyTransferLimit());
    }
}
