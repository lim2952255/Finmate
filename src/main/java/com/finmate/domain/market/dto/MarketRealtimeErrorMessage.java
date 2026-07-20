package com.finmate.domain.market.dto;

public record MarketRealtimeErrorMessage(
        String type,
        String message
) {
    private static final String ERROR_MESSAGE_TYPE = "ERROR";

    public static MarketRealtimeErrorMessage of(String message) {
        return new MarketRealtimeErrorMessage(ERROR_MESSAGE_TYPE, message);
    }
}
