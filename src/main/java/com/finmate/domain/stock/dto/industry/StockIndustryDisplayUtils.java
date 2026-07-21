package com.finmate.domain.stock.dto.industry;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;

// 귝내 종목의 소업종 - 중업종 - 대업종 순서대로 조회하면서 가장 세부적 유효업종을 선택해서 화면에 표시하는 역할을 수행하는 유틸리티 객체
public final class StockIndustryDisplayUtils {
    public static final String NONE_DISPLAY_VALUE = "없음";
    public static final String UNKNOWN_DISPLAY_VALUE = "-";

    private StockIndustryDisplayUtils() {
    }

    // 가장 세부적인 유효 업종을 조회해서 화면 표시값을 반환하는 메서드
    public static String mostSpecificDomesticDisplayName(String largeCode,
                                                         String mediumCode,
                                                         String smallCode,
                                                         Function<String, String> sectorNameResolver) {
        // 소분주 -> 중분류 -> 대분류 순서대로 세부적인 순서대로 업종을 조회한다.
        return Arrays.asList(smallCode, mediumCode, largeCode).stream()
                // 각 업종 코드를 화면에 표시가능한 값으 바꾼다.
                .map(code -> classifiedDisplayNameOrCode(code, sectorNameResolver))
                // 반환 결과중 null, 빈 문자열, 공백 문자열을 제거한다.
                .filter(StockIndustryDisplayUtils::hasText)
                // 가장 첫번째 업종, 즉 가장 세부적인 유효 업종을 조회해서 리턴한다.
                .findFirst()
                .orElse(NONE_DISPLAY_VALUE);
    }
    // 업종 코드와 업종명을 입력받아, 업종명이나 업종코드가 유효한지 확인하고 값을 리턴한다.
    public static String displayNameOrCode(String code, String resolvedName) {
        String normalizedName = normalizeText(resolvedName);
        if (hasText(normalizedName)) {
            return normalizedName;
        }

        String normalizedCode = normalizeCode(code);
        if (!hasText(normalizedCode)) {
            return null;
        }
        if (isNoneCode(normalizedCode)) {
            return NONE_DISPLAY_VALUE;
        }

        return "코드 " + normalizedCode;
    }

    public static String unknownIfBlank(String value) {
        String normalizedValue = normalizeText(value);
        return hasText(normalizedValue) ? normalizedValue : UNKNOWN_DISPLAY_VALUE;
    }

    public static boolean isNoneCode(String code) {
        String normalizedCode = normalizeCode(code);
        return hasText(normalizedCode) && normalizedCode.matches("0+");
    }

    public static String normalizeCode(String value) {
        String normalizedValue = normalizeText(value);
        return hasText(normalizedValue) ? normalizedValue.toUpperCase(Locale.ROOT) : null;
    }

    // 업종 코드와 업종명이 유효한지 확인하고, 업종코드와 매핑되어 있는 업종명을 리턴한다.
    private static String classifiedDisplayNameOrCode(String code, Function<String, String> sectorNameResolver) {
        String normalizedCode = normalizeCode(code);
        if (!hasText(normalizedCode) || isNoneCode(normalizedCode)) {
            return null;
        }

        return displayNameOrCode(normalizedCode, sectorNameResolver.apply(normalizedCode));
    }

    private static String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
