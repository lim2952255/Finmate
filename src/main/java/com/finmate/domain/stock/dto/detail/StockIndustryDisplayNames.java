package com.finmate.domain.stock.dto.detail;

import java.util.Locale;
import java.util.Map;

// 업종코드별 업종명 매핑
public record StockIndustryDisplayNames(
        Map<String, String> domesticSectorNames,
        String overseasIndustryName
) {
    public StockIndustryDisplayNames {
        domesticSectorNames = domesticSectorNames == null ? Map.of() : Map.copyOf(domesticSectorNames);
    }

    public static StockIndustryDisplayNames empty() {
        return new StockIndustryDisplayNames(Map.of(), null);
    }

    public String domesticSectorName(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        return domesticSectorNames.get(code.trim().toUpperCase(Locale.ROOT));
    }
}
