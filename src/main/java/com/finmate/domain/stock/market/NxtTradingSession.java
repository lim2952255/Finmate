package com.finmate.domain.stock.market;

import java.time.LocalTime;

public enum NxtTradingSession {
    // NXT 프리마켓 / 메인마켓 / 애프터마켓에 따라 허용 코드를 각각 1, 2, 4로 설정하고, 허용코드가 7이면 프리마켓 + 메인마켓 + 애프터마켓에서 모두 거래가능
    PRE_MARKET(1, "NXT 프리마켓", LocalTime.of(8, 0), LocalTime.of(8, 50)),
    MAIN_MARKET(2, "NXT 메인마켓", LocalTime.of(9, 0, 30), LocalTime.of(15, 20)),
    AFTER_MARKET(4, "NXT 애프터마켓", LocalTime.of(15, 40), LocalTime.of(20, 0));

    private final int permissionBit; // 허용 코드
    private final String displayName;
    private final LocalTime openTime;
    private final LocalTime closeTime;

    NxtTradingSession(int permissionBit,
                      String displayName,
                      LocalTime openTime,
                      LocalTime closeTime) {
        this.permissionBit = permissionBit;
        this.displayName = displayName;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    public boolean isAllowed(Integer permissionCode) {
        return permissionCode != null
                && (permissionCode & permissionBit) != 0;
    }

    public boolean isTradingTime(Integer permissionCode, LocalTime time) {
        return isAllowed(permissionCode)
                && !time.isBefore(openTime)
                && !time.isAfter(closeTime);
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }
}
