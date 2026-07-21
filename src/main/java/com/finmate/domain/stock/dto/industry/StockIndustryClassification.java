package com.finmate.domain.stock.dto.industry;

import static com.finmate.domain.stock.dto.industry.StockIndustryDisplayUtils.NONE_DISPLAY_VALUE;
import static com.finmate.domain.stock.dto.industry.StockIndustryDisplayUtils.UNKNOWN_DISPLAY_VALUE;
import static com.finmate.domain.stock.dto.industry.StockIndustryDisplayUtils.unknownIfBlank;

//
public record StockIndustryClassification(
        String displayName, // 업종명
        String allocationGroupName, // 국내종목: 국내, 해외종목: 거래소명
        String allocationIndustryName // 포트폴리오 비중을 계산할때 사용하는 업종명
) {
    public static final String DOMESTIC_GROUP_NAME = "국내";
    public static final String OVERSEAS_GROUP_NAME = "해외";
    public static final String UNCLASSIFIED_NAME = "미분류";

    public StockIndustryClassification {
        displayName = unknownIfBlank(displayName);
        allocationGroupName = unknownIfBlank(allocationGroupName);
        allocationIndustryName = unknownIfBlank(allocationIndustryName);
    }

    public static StockIndustryClassification domestic(String industryName) {
        return new StockIndustryClassification(industryName, DOMESTIC_GROUP_NAME, industryName);
    }

    public static StockIndustryClassification overseas(String exchangeName, String industryName) {
        return new StockIndustryClassification(industryName, exchangeName, industryName);
    }

    public static StockIndustryClassification unclassified() {
        return new StockIndustryClassification(UNCLASSIFIED_NAME, UNCLASSIFIED_NAME, UNCLASSIFIED_NAME);
    }

    public String allocationGroupNameOrDefault() {
        if (UNKNOWN_DISPLAY_VALUE.equals(allocationGroupName)) {
            return UNCLASSIFIED_NAME;
        }

        return allocationGroupName;
    }

    public String allocationIndustryNameOrDefault() {
        if (UNKNOWN_DISPLAY_VALUE.equals(allocationIndustryName) || NONE_DISPLAY_VALUE.equals(allocationIndustryName)) {
            return UNCLASSIFIED_NAME;
        }

        return allocationIndustryName;
    }
}
