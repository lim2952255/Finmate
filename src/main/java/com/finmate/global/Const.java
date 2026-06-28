package com.finmate.global;

import java.math.BigDecimal;

public final class Const {

    public static final String LOGIN_USER = "loginUser";

    public static final BigDecimal INITIAL_BALANCE = BigDecimal.valueOf(3_000_000);

    public static final BigDecimal DAILY_TRANSFER_LIMIT = BigDecimal.valueOf(5_000_000);

    public static final BigDecimal SINGLE_TRANSFER_LIMIT = BigDecimal.valueOf(1_000_000);

    private Const() {
    }
}
