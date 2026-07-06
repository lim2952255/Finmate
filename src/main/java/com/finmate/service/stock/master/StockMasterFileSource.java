package com.finmate.service.stock.master;

import com.finmate.domain.stock.StockMarketType;

public enum StockMasterFileSource {
    // 코스피 마스터파일 주소 및 파일명
    KOSPI(
            StockMarketType.KOSPI,
            "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip",
            "kospi_code.mst"),
    // 코스닥 마스터파일 주소 및 파일명
    KOSDAQ(
            StockMarketType.KOSDAQ,
            "https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip",
            "kosdaq_code.mst"),
    // 나스닥 마스터파일 주소 및 파일명
    NASDAQ(
            StockMarketType.NASDAQ,
            "https://new.real.download.dws.co.kr/common/master/nasmst.cod.zip",
            "nasmst.cod");

    private final StockMarketType marketType;
    private final String zipUrl;
    private final String extractedFileName;

    StockMasterFileSource(StockMarketType marketType, String zipUrl, String extractedFileName) {
        this.marketType = marketType;
        this.zipUrl = zipUrl;
        this.extractedFileName = extractedFileName;
    }

    public StockMarketType getMarketType() {
        return marketType;
    }

    public String getZipUrl() {
        return zipUrl;
    }

    public String getExtractedFileName() {
        return extractedFileName;
    }

    public boolean isDomestic() {
        return this == KOSPI || this == KOSDAQ;
    }
}
