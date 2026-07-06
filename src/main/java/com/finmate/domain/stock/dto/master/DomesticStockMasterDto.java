package com.finmate.domain.stock.dto.master;

import com.finmate.domain.stock.StockMarketType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
// 마스터 파일에서 파싱한 국내 주식 데이터들을 저장할 dto
public class DomesticStockMasterDto {
    private final StockMarketType marketType;
    private final String symbol;
    private final String standardCode;
    private final String nameKo;

    private String securityGroupClassCode;
    private String marketCapScaleClassCode;
    private String sectorLargeDivisionCode;
    private String sectorMediumDivisionCode;
    private String sectorSmallDivisionCode;
    private String manufacturingClassYn;
    private String ventureIssueYn;
    private String lowLiquidityYn;
    private String governanceIndexIssueYn;
    private String kospi200SectorCode;
    private String kospi100IssueYn;
    private String kospi50IssueYn;
    private String krxIssueYn;
    private String etpProductClassCode;
    private String elwPublishedYn;
    private String krx100IssueYn;
    private String krxCarYn;
    private String krxSemiconductorYn;
    private String krxBioYn;
    private String krxBankYn;
    private String spacYn;
    private String krxEnergyChemicalYn;
    private String krxSteelYn;
    private String shortOverClassCode;
    private String krxMediaCommunicationYn;
    private String krxConstructionYn;
    private String krxFinancialServiceYn;
    private String investmentAlertYn;
    private String krxSecuritiesYn;
    private String krxShipYn;
    private String krxInsuranceYn;
    private String krxTransportationYn;
    private String sriIndexYn;
    private String kosdaq150IndexYn;
    private String stockBasePrice;
    private String regularMarketTradeQuantityUnit;
    private String afterHoursMarketTradeQuantityUnit;
    private String tradingHaltYn;
    private String liquidationTradeYn;
    private String managedIssueYn;
    private String marketAlertClassCode;
    private String marketAlertRiskNoticeYn;
    private String unfaithfulDisclosureYn;
    private String backdoorListingYn;
    private String lockClassCode;
    private String faceValueChangeClassCode;
    private String capitalIncreaseClassCode;
    private String marginRate;
    private String creditAvailableYn;
    private String creditDays;
    private String previousDayVolume;
    private String stockFaceValue;
    private String stockListedDate;
    private String listedShares;
    private String capital;
    private String settlementMonth;
    private String publicOfferingPrice;
    private String preferredStockClassCode;
    private String shortSaleOverheatedYn;
    private String abnormalRunupYn;
    private String krx300IssueYn;
    private String kospiIssueYn;
    private String sales;
    private String operatingProfit;
    private String ordinaryProfit;
    private String netIncome;
    private String roe;
    private String baseDate;
    private String previousDayMarketCap;
    private String groupCode;
    private String companyCreditLimitOverYn;
    private String securitiesLendingAvailableYn;
    private String stockLoanAvailableYn;

    public DomesticStockMasterDto(StockMarketType marketType, String symbol, String standardCode, String nameKo) {
        this.marketType = marketType;
        this.symbol = symbol;
        this.standardCode = standardCode;
        this.nameKo = nameKo;
    }
}
