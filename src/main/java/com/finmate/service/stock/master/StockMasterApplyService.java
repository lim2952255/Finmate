package com.finmate.service.stock.master;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.StockSecurityType;
import com.finmate.domain.stock.dto.master.DomesticStockMasterDto;
import com.finmate.domain.stock.dto.master.OverseasStockMasterDto;
import com.finmate.domain.stock.metadata.DomesticStockMetadata;
import com.finmate.domain.stock.metadata.OverseasStockMetadata;
import com.finmate.repository.stock.StockRepository;
import com.finmate.repository.stock.metadata.DomesticStockMetadataRepository;
import com.finmate.repository.stock.metadata.OverseasStockMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.finmate.infra.kis.parser.KisValueParser.parseNullableDate;
import static com.finmate.infra.kis.parser.KisValueParser.parseYesNo;

// 파싱된 종목 마스터 DTO 목록을 DB의 Stock, DomesticStockMetadata, OverseasStockMetadata에 반영하는 동기화 서비스 로직
@Service
@RequiredArgsConstructor
public class StockMasterApplyService {
    private final StockRepository stockRepository;
    private final DomesticStockMetadataRepository domesticStockMetadataRepository;
    private final OverseasStockMetadataRepository overseasStockMetadataRepository;

    // 국내 주식종목 동기화 (KOSPI / KOSDAQ)
    @Transactional
    public void applyDomestic(StockMarketType marketType, // KOSPI 또는 KOSDAQ
                              List<DomesticStockMasterDto> rows, // 마스터파일에서 파싱한 국내 종목 목록 DTO
                              LocalDateTime syncedAt) { // 동기화 시각
        // 현재 db에 저장되어 있는 국내 시장 내의(KOSPI or KOSDAQ) 종목들을 기반으로 (symbol, stock) Map을 만든다.
        Map<String, Stock> existingStocks = stockRepository.findByMarketType(marketType).stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity()));
        // 마스터파일에 들어있는 Symbol 목록 만들기
        Set<String> syncedSymbols = rows.stream()
                .map(DomesticStockMasterDto::getSymbol)
                .collect(Collectors.toSet());

        for (DomesticStockMasterDto row : rows) {
            Stock stock = existingStocks.get(row.getSymbol());
            if (stock == null) {
                // 마스터파일에 들어 있는 Symbol이 database에 없는 경우(insert)
                stock = Stock.create(
                        row.getSymbol(),
                        row.getSymbol(),
                        row.getStandardCode(),
                        row.getNameKo(),
                        null,
                        row.getMarketType(),
                        "KR",
                        "KRX",
                        "KRW",
                        convertDomesticSecurityType(
                                row.getSecurityGroupClassCode(),
                                row.getEtpProductClassCode(),
                                row.getPreferredStockClassCode()),
                        parseYesNo(row.getTradingHaltYn()),
                        parseNullableDate(row.getStockListedDate()),
                        syncedAt);
                stockRepository.save(stock);
            } else {
                // 마스터파일에 들어 있는 Symbol이 이미 database에 있는 경우(update)
                stock.updateMasterInfo(
                        row.getSymbol(),
                        row.getStandardCode(),
                        row.getNameKo(),
                        null,
                        "KR",
                        "KRX",
                        "KRW",
                        convertDomesticSecurityType(
                                row.getSecurityGroupClassCode(),
                                row.getEtpProductClassCode(),
                                row.getPreferredStockClassCode()), // type 형태 변환
                        parseYesNo(row.getTradingHaltYn()),
                        parseNullableDate(row.getStockListedDate()),
                        syncedAt);
            }

            Stock syncedStock = stock;
            // 마스터파일에 존재하는 종목에 대한 메타데이터가 database에 존재하면, 해당 메타데이터를 꺼내고, 없으면 새로운 메타데이터 엔티티 생성
            DomesticStockMetadata metadata = domesticStockMetadataRepository.findByStock_Id(syncedStock.getId())
                    .orElseGet(() -> DomesticStockMetadata.create(syncedStock, syncedAt));
            // 마스터파일 dto정보를 기반으로 메타데이터 엔티티 update(copy) + 레포지터리에 저장
            copyDomesticMetadata(row, metadata, syncedAt);
            domesticStockMetadataRepository.save(metadata);
        }

        // 기존에 database에는 존재했지만, 새로 갱신된 마스터 파일에는 존재하지 않는 경우, 정합성과 언정성을 위해 해당 종목을 바로 삭제하는게 아니라, Inactive 상태로 설정
        existingStocks.values().stream()
                .filter(stock -> !syncedSymbols.contains(stock.getSymbol()))
                .forEach(stock -> stock.markInactive(syncedAt));
    }

    // 해외 주식종목 동기화 (NASDAQ)
    @Transactional
    public void applyOverseas(StockMarketType marketType,
                              List<OverseasStockMasterDto> rows,
                              LocalDateTime syncedAt) {
        // 현재 db에 저장되어 있는 해외 시장 내의(NASDAQ) 종목들을 기반으로 (symbol, stock) Map을 만든다.
        Map<String, Stock> existingStocks = stockRepository.findByMarketType(marketType).stream()
                .collect(Collectors.toMap(Stock::getSymbol, Function.identity()));
        // 마스터파일에 들어있는 Symbol 목록 만들기
        Set<String> syncedSymbols = rows.stream()
                .map(OverseasStockMasterDto::getSymbol)
                .collect(Collectors.toSet());

        for (OverseasStockMasterDto row : rows) {
            Stock stock = existingStocks.get(row.getSymbol());
            // 마스터파일에 들어 있는 Symbol이 database에 없는 경우(insert)
            if (stock == null) {
                stock = Stock.create(
                        row.getSymbol(),
                        row.getRealtimeSymbol(),
                        null,
                        row.getNameKo(),
                        row.getNameEn(),
                        row.getMarketType(),
                        row.getNationalCode(),
                        row.getExchangeCode(),
                        row.getCurrency(),
                        convertOverseasSecurityType(row.getSecurityTypeCode(), row.getEtpType()), // type 형태 변환
                        false,
                        null,
                        syncedAt);
                stockRepository.save(stock);
            } else {
                // 마스터파일에 들어 있는 Symbol이 이미 database에 있는 경우(update)
                stock.updateMasterInfo(
                        row.getRealtimeSymbol(),
                        null,
                        row.getNameKo(),
                        row.getNameEn(),
                        row.getNationalCode(),
                        row.getExchangeCode(),
                        row.getCurrency(),
                        convertOverseasSecurityType(row.getSecurityTypeCode(), row.getEtpType()),
                        false,
                        null,
                        syncedAt);
            }

            Stock syncedStock = stock;
            // 마스터파일에 존재하는 종목에 대한 메타데이터가 database에 존재하면, 해당 메타데이터를 꺼내고, 없으면 새로운 메타데이터 엔티티 생성
            OverseasStockMetadata metadata = overseasStockMetadataRepository.findByStock_Id(syncedStock.getId())
                    .orElseGet(() -> OverseasStockMetadata.create(syncedStock, syncedAt));
            // 마스터파일 dto정보를 기반으로 메타데이터 엔티티 update(copy) + 레포지터리에 저장
            copyOverseasMetadata(row, metadata, syncedAt);
            overseasStockMetadataRepository.save(metadata);
        }

        // 기존에 database에는 존재했지만, 새로 갱신된 마스터 파일에는 존재하지 않는 경우, 정합성과 언정성을 위해 해당 종목을 바로 삭제하는게 아니라, Inactive 상태로 설정
        existingStocks.values().stream()
                .filter(stock -> !syncedSymbols.contains(stock.getSymbol()))
                .forEach(stock -> stock.markInactive(syncedAt));
    }

    // 국내 주식 메타데이터 update (copy)
    private void copyDomesticMetadata(DomesticStockMasterDto row,
                                      DomesticStockMetadata metadata,
                                      LocalDateTime syncedAt) {
        metadata.setSecurityGroupClassCode(row.getSecurityGroupClassCode());
        metadata.setMarketCapScaleClassCode(row.getMarketCapScaleClassCode());
        metadata.setSectorLargeDivisionCode(row.getSectorLargeDivisionCode());
        metadata.setSectorMediumDivisionCode(row.getSectorMediumDivisionCode());
        metadata.setSectorSmallDivisionCode(row.getSectorSmallDivisionCode());
        metadata.setManufacturingClassYn(row.getManufacturingClassYn());
        metadata.setVentureIssueYn(row.getVentureIssueYn());
        metadata.setLowLiquidityYn(row.getLowLiquidityYn());
        metadata.setGovernanceIndexIssueYn(row.getGovernanceIndexIssueYn());
        metadata.setKospi200SectorCode(row.getKospi200SectorCode());
        metadata.setKospi100IssueYn(row.getKospi100IssueYn());
        metadata.setKospi50IssueYn(row.getKospi50IssueYn());
        metadata.setKrxIssueYn(row.getKrxIssueYn());
        metadata.setEtpProductClassCode(row.getEtpProductClassCode());
        metadata.setElwPublishedYn(row.getElwPublishedYn());
        metadata.setKrx100IssueYn(row.getKrx100IssueYn());
        metadata.setKrxCarYn(row.getKrxCarYn());
        metadata.setKrxSemiconductorYn(row.getKrxSemiconductorYn());
        metadata.setKrxBioYn(row.getKrxBioYn());
        metadata.setKrxBankYn(row.getKrxBankYn());
        metadata.setSpacYn(row.getSpacYn());
        metadata.setKrxEnergyChemicalYn(row.getKrxEnergyChemicalYn());
        metadata.setKrxSteelYn(row.getKrxSteelYn());
        metadata.setShortOverClassCode(row.getShortOverClassCode());
        metadata.setKrxMediaCommunicationYn(row.getKrxMediaCommunicationYn());
        metadata.setKrxConstructionYn(row.getKrxConstructionYn());
        metadata.setKrxFinancialServiceYn(row.getKrxFinancialServiceYn());
        metadata.setInvestmentAlertYn(row.getInvestmentAlertYn());
        metadata.setKrxSecuritiesYn(row.getKrxSecuritiesYn());
        metadata.setKrxShipYn(row.getKrxShipYn());
        metadata.setKrxInsuranceYn(row.getKrxInsuranceYn());
        metadata.setKrxTransportationYn(row.getKrxTransportationYn());
        metadata.setSriIndexYn(row.getSriIndexYn());
        metadata.setKosdaq150IndexYn(row.getKosdaq150IndexYn());
        metadata.setStockBasePrice(row.getStockBasePrice());
        metadata.setRegularMarketTradeQuantityUnit(row.getRegularMarketTradeQuantityUnit());
        metadata.setAfterHoursMarketTradeQuantityUnit(row.getAfterHoursMarketTradeQuantityUnit());
        metadata.setTradingHaltYn(row.getTradingHaltYn());
        metadata.setLiquidationTradeYn(row.getLiquidationTradeYn());
        metadata.setManagedIssueYn(row.getManagedIssueYn());
        metadata.setMarketAlertClassCode(row.getMarketAlertClassCode());
        metadata.setMarketAlertRiskNoticeYn(row.getMarketAlertRiskNoticeYn());
        metadata.setUnfaithfulDisclosureYn(row.getUnfaithfulDisclosureYn());
        metadata.setBackdoorListingYn(row.getBackdoorListingYn());
        metadata.setLockClassCode(row.getLockClassCode());
        metadata.setFaceValueChangeClassCode(row.getFaceValueChangeClassCode());
        metadata.setCapitalIncreaseClassCode(row.getCapitalIncreaseClassCode());
        metadata.setMarginRate(row.getMarginRate());
        metadata.setCreditAvailableYn(row.getCreditAvailableYn());
        metadata.setCreditDays(row.getCreditDays());
        metadata.setPreviousDayVolume(row.getPreviousDayVolume());
        metadata.setStockFaceValue(row.getStockFaceValue());
        metadata.setStockListedDate(row.getStockListedDate());
        metadata.setListedShares(row.getListedShares());
        metadata.setCapital(row.getCapital());
        metadata.setSettlementMonth(row.getSettlementMonth());
        metadata.setPublicOfferingPrice(row.getPublicOfferingPrice());
        metadata.setPreferredStockClassCode(row.getPreferredStockClassCode());
        metadata.setShortSaleOverheatedYn(row.getShortSaleOverheatedYn());
        metadata.setAbnormalRunupYn(row.getAbnormalRunupYn());
        metadata.setKrx300IssueYn(row.getKrx300IssueYn());
        metadata.setKospiIssueYn(row.getKospiIssueYn());
        metadata.setSales(row.getSales());
        metadata.setOperatingProfit(row.getOperatingProfit());
        metadata.setOrdinaryProfit(row.getOrdinaryProfit());
        metadata.setNetIncome(row.getNetIncome());
        metadata.setRoe(row.getRoe());
        metadata.setBaseDate(row.getBaseDate());
        metadata.setPreviousDayMarketCap(row.getPreviousDayMarketCap());
        metadata.setGroupCode(row.getGroupCode());
        metadata.setCompanyCreditLimitOverYn(row.getCompanyCreditLimitOverYn());
        metadata.setSecuritiesLendingAvailableYn(row.getSecuritiesLendingAvailableYn());
        metadata.setStockLoanAvailableYn(row.getStockLoanAvailableYn());
        metadata.setLastSyncedAt(syncedAt);
    }

    // 해외 주식 메타데이터 update (copy)
    private void copyOverseasMetadata(OverseasStockMasterDto row,
                                      OverseasStockMetadata metadata,
                                      LocalDateTime syncedAt) {
        metadata.setNationalCode(row.getNationalCode());
        metadata.setExchangeId(row.getExchangeId());
        metadata.setExchangeCode(row.getExchangeCode());
        metadata.setExchangeName(row.getExchangeName());
        metadata.setSecurityTypeCode(row.getSecurityTypeCode());
        metadata.setCurrency(row.getCurrency());
        metadata.setFloatPosition(row.getFloatPosition());
        metadata.setDataType(row.getDataType());
        metadata.setBasePrice(row.getBasePrice());
        metadata.setBidOrderSize(row.getBidOrderSize());
        metadata.setAskOrderSize(row.getAskOrderSize());
        metadata.setMarketStartTime(row.getMarketStartTime());
        metadata.setMarketEndTime(row.getMarketEndTime());
        metadata.setDrYn(row.getDrYn());
        metadata.setDrCountryCode(row.getDrCountryCode());
        metadata.setIndustryCode(row.getIndustryCode());
        metadata.setIndexConstituentYn(row.getIndexConstituentYn());
        metadata.setTickSizeType(row.getTickSizeType());
        metadata.setEtpType(row.getEtpType());
        metadata.setTickSizeTypeDetail(row.getTickSizeTypeDetail());
        metadata.setLastSyncedAt(syncedAt);
    }

    private StockSecurityType convertDomesticSecurityType(String securityGroupClassCode,
                                                          String etpProductClassCode,
                                                          String preferredStockClassCode) {
        if ("ST".equals(securityGroupClassCode)) {
            if ("1".equals(preferredStockClassCode) || "2".equals(preferredStockClassCode)) {
                return StockSecurityType.PREFERRED_STOCK;
            }
            return StockSecurityType.COMMON_STOCK;
        }
        if ("EN".equals(securityGroupClassCode)
                || "3".equals(etpProductClassCode)
                || "4".equals(etpProductClassCode)) {
            return StockSecurityType.ETN;
        }
        if ("EF".equals(securityGroupClassCode) || "FE".equals(securityGroupClassCode)) {
            return StockSecurityType.ETF;
        }
        if ("EW".equals(securityGroupClassCode)) {
            return StockSecurityType.ELW;
        }
        if ("DR".equals(securityGroupClassCode)) {
            return StockSecurityType.DR;
        }
        if ("RT".equals(securityGroupClassCode)) {
            return StockSecurityType.REIT;
        }
        if ("MF".equals(securityGroupClassCode)) {
            return StockSecurityType.INVESTMENT_COMPANY;
        }
        if ("SC".equals(securityGroupClassCode)) {
            return StockSecurityType.SHIP_INVESTMENT_COMPANY;
        }
        if ("IF".equals(securityGroupClassCode)) {
            return StockSecurityType.INFRASTRUCTURE_FUND;
        }
        if ("SW".equals(securityGroupClassCode) || "SR".equals(securityGroupClassCode)) {
            return StockSecurityType.WARRANT;
        }
        if ("BC".equals(securityGroupClassCode)
                || "PF".equals(securityGroupClassCode)
                || "5".equals(etpProductClassCode)) {
            return StockSecurityType.FUND;
        }
        if ("FS".equals(securityGroupClassCode)) {
            return StockSecurityType.COMMON_STOCK;
        }

        return StockSecurityType.UNKNOWN;
    }

    private StockSecurityType convertOverseasSecurityType(String securityTypeCode, String etpType) {
        if ("1".equals(securityTypeCode)) {
            return StockSecurityType.INDEX;
        }
        if ("2".equals(securityTypeCode)) {
            return StockSecurityType.COMMON_STOCK;
        }
        if ("3".equals(securityTypeCode)) {
            if ("002".equals(etpType)) {
                return StockSecurityType.ETN;
            }
            if ("003".equals(etpType)) {
                return StockSecurityType.ETC;
            }
            return StockSecurityType.ETF;
        }
        if ("4".equals(securityTypeCode)) {
            return StockSecurityType.WARRANT;
        }

        return StockSecurityType.UNKNOWN;
    }
}
