package com.finmate.service.stock.master;

import com.finmate.domain.stock.StockMarketType;
import com.finmate.domain.stock.dto.master.DomesticStockMasterDto;
import com.finmate.domain.stock.dto.master.OverseasStockMasterDto;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.finmate.infra.kis.parser.KisValueParser.optionalCode;
import static com.finmate.infra.kis.parser.KisValueParser.optionalText;
import static com.finmate.infra.kis.parser.KisValueParser.requiredCode;
import static com.finmate.infra.kis.parser.KisValueParser.requiredText;

// 마스터 파일의 정보를 읽고, 파싱해서 dto에 데이터를 담는 서비스로작
@Component
public class StockMasterParser {
    private static final Charset STOCK_MASTER_CHARSET = Charset.forName("CP949");
    // KIS 파이썬 예시는 row[-228:], row[-222:]로 자르지만, 파이썬 row에는 줄바꿈이 포함된다.
    // Java BufferedReader.readLine()은 줄바꿈을 제거하므로 실제 metadata 본문 길이는 각각 1자 짧다.
    private static final int KOSPI_METADATA_LENGTH = 227;
    private static final int KOSDAQ_METADATA_LENGTH = 221;

    public List<DomesticStockMasterDto> parseDomestic(Path masterFile, StockMarketType marketType) {
        List<DomesticStockMasterDto> rows = new ArrayList<>();
        int metadataLength = getDomesticMetadataLength(marketType);

        // 지정한 길이만큼 읽으면서, 정해진 길이만큼 잘라서 파싱
        try (BufferedReader reader = Files.newBufferedReader(masterFile, STOCK_MASTER_CHARSET)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                rows.add(parseDomesticLine(line, marketType, metadataLength));
            }
        } catch (IOException e) {
            throw new RuntimeException("국내 종목 마스터 파일 파싱에 실패했습니다: " + masterFile, e);
        }

        return rows;
    }

    // 지정한 길이만큼 읽으면서, 정해진 길이만큼 잘라서 파싱
    public List<OverseasStockMasterDto> parseOverseas(Path masterFile, StockMarketType marketType) {
        List<OverseasStockMasterDto> rows = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(masterFile, STOCK_MASTER_CHARSET)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                rows.add(parseOverseasLine(line, marketType));
            }
        } catch (IOException e) {
            throw new RuntimeException("해외 종목 마스터 파일 파싱에 실패했습니다: " + masterFile, e);
        }

        return rows;
    }

    private DomesticStockMasterDto parseDomesticLine(String line,
                                                     StockMarketType marketType,
                                                     int metadataLength) {
        if (line.length() <= metadataLength + 21) {
            throw new RuntimeException("국내 종목 마스터 파일 라인 길이가 올바르지 않습니다. length=" + line.length());
        }

        int metadataStartIndex = line.length() - metadataLength;
        String prefix = line.substring(0, metadataStartIndex);
        String metadata = line.substring(metadataStartIndex);

        String symbol = requiredCode(prefix.substring(0, 9), "단축코드는 필수입니다.");
        String standardCode = optionalCode(prefix.substring(9, 21));
        String nameKo = requiredText(prefix.substring(21), "한글 종목명은 필수입니다.");

        DomesticStockMasterDto row = new DomesticStockMasterDto(marketType, symbol, standardCode, nameKo);
        DomesticMetadataFieldReader fieldReader = new DomesticMetadataFieldReader(metadata);

        if (marketType == StockMarketType.KOSPI) {
            // KOSPI Parsing
            fillKospiMetadata(row, fieldReader);
        } else if (marketType == StockMarketType.KOSDAQ) {
            // KOSDAQ Parsing
            fillKosdaqMetadata(row, fieldReader);
        } else {
            throw new RuntimeException("국내 종목 마스터 파싱 대상 시장이 아닙니다: " + marketType);
        }
        fieldReader.validateFullyRead();

        return row;
    }

    // KOSPI 메타데이터 형식에 맞춰서 파싱 및 dto 업데이트
    private void fillKospiMetadata(DomesticStockMasterDto row, DomesticMetadataFieldReader reader) {
        row.setSecurityGroupClassCode(reader.next(2));
        row.setMarketCapScaleClassCode(reader.next(1));
        row.setSectorLargeDivisionCode(reader.next(4));
        row.setSectorMediumDivisionCode(reader.next(4));
        row.setSectorSmallDivisionCode(reader.next(4));
        row.setManufacturingClassYn(reader.next(1));
        row.setLowLiquidityYn(reader.next(1));
        row.setGovernanceIndexIssueYn(reader.next(1));
        row.setKospi200SectorCode(reader.next(1));
        row.setKospi100IssueYn(reader.next(1));
        row.setKospi50IssueYn(reader.next(1));
        row.setKrxIssueYn(reader.next(1));
        row.setEtpProductClassCode(reader.next(1));
        row.setElwPublishedYn(reader.next(1));
        row.setKrx100IssueYn(reader.next(1));
        row.setKrxCarYn(reader.next(1));
        row.setKrxSemiconductorYn(reader.next(1));
        row.setKrxBioYn(reader.next(1));
        row.setKrxBankYn(reader.next(1));
        row.setSpacYn(reader.next(1));
        row.setKrxEnergyChemicalYn(reader.next(1));
        row.setKrxSteelYn(reader.next(1));
        row.setShortOverClassCode(reader.next(1));
        row.setKrxMediaCommunicationYn(reader.next(1));
        row.setKrxConstructionYn(reader.next(1));
        row.setKrxFinancialServiceYn(reader.next(1));
        row.setKrxSecuritiesYn(reader.next(1));
        row.setKrxShipYn(reader.next(1));
        row.setKrxInsuranceYn(reader.next(1));
        row.setKrxTransportationYn(reader.next(1));
        row.setSriIndexYn(reader.next(1));
        fillCommonDomesticMetadata(row, reader, true);
    }

    // KOSDAQ 메타데이터 형식에 맞춰서 파싱 및 dto 업데이트
    private void fillKosdaqMetadata(DomesticStockMasterDto row, DomesticMetadataFieldReader reader) {
        row.setSecurityGroupClassCode(reader.next(2));
        row.setMarketCapScaleClassCode(reader.next(1));
        row.setSectorLargeDivisionCode(reader.next(4));
        row.setSectorMediumDivisionCode(reader.next(4));
        row.setSectorSmallDivisionCode(reader.next(4));
        row.setVentureIssueYn(reader.next(1));
        row.setLowLiquidityYn(reader.next(1));
        row.setKrxIssueYn(reader.next(1));
        row.setEtpProductClassCode(reader.next(1));
        row.setKrx100IssueYn(reader.next(1));
        row.setKrxCarYn(reader.next(1));
        row.setKrxSemiconductorYn(reader.next(1));
        row.setKrxBioYn(reader.next(1));
        row.setKrxBankYn(reader.next(1));
        row.setSpacYn(reader.next(1));
        row.setKrxEnergyChemicalYn(reader.next(1));
        row.setKrxSteelYn(reader.next(1));
        row.setShortOverClassCode(reader.next(1));
        row.setKrxMediaCommunicationYn(reader.next(1));
        row.setKrxConstructionYn(reader.next(1));
        row.setInvestmentAlertYn(reader.next(1));
        row.setKrxSecuritiesYn(reader.next(1));
        row.setKrxShipYn(reader.next(1));
        row.setKrxInsuranceYn(reader.next(1));
        row.setKrxTransportationYn(reader.next(1));
        row.setKosdaq150IndexYn(reader.next(1));
        fillCommonDomesticMetadata(row, reader, false);
    }

    // 국내 주식의 공통 메타데이터 update
    private void fillCommonDomesticMetadata(DomesticStockMasterDto row,
                                            DomesticMetadataFieldReader reader,
                                            boolean hasKospiIssueYn) {
        row.setStockBasePrice(reader.next(9));
        row.setRegularMarketTradeQuantityUnit(reader.next(5));
        row.setAfterHoursMarketTradeQuantityUnit(reader.next(5));
        row.setTradingHaltYn(reader.next(1));
        row.setLiquidationTradeYn(reader.next(1));
        row.setManagedIssueYn(reader.next(1));
        row.setMarketAlertClassCode(reader.next(2));
        row.setMarketAlertRiskNoticeYn(reader.next(1));
        row.setUnfaithfulDisclosureYn(reader.next(1));
        row.setBackdoorListingYn(reader.next(1));
        row.setLockClassCode(reader.next(2));
        row.setFaceValueChangeClassCode(reader.next(2));
        row.setCapitalIncreaseClassCode(reader.next(2));
        row.setMarginRate(reader.next(3));
        row.setCreditAvailableYn(reader.next(1));
        row.setCreditDays(reader.next(3));
        row.setPreviousDayVolume(reader.next(12));
        row.setStockFaceValue(reader.next(12));
        row.setStockListedDate(reader.next(8));
        row.setListedShares(reader.next(15));
        row.setCapital(reader.next(21));
        row.setSettlementMonth(reader.next(2));
        row.setPublicOfferingPrice(reader.next(7));
        row.setPreferredStockClassCode(reader.next(1));
        row.setShortSaleOverheatedYn(reader.next(1));
        row.setAbnormalRunupYn(reader.next(1));
        row.setKrx300IssueYn(reader.next(1));
        if (hasKospiIssueYn) {
            row.setKospiIssueYn(reader.next(1));
        }
        row.setSales(reader.next(9));
        row.setOperatingProfit(reader.next(9));
        row.setOrdinaryProfit(reader.next(9));
        row.setNetIncome(reader.next(5));
        row.setRoe(reader.next(9));
        row.setBaseDate(reader.next(8));
        row.setPreviousDayMarketCap(reader.next(9));
        row.setGroupCode(reader.next(3));
        row.setCompanyCreditLimitOverYn(reader.next(1));
        row.setSecuritiesLendingAvailableYn(reader.next(1));
        row.setStockLoanAvailableYn(reader.next(1));
    }

    private OverseasStockMasterDto parseOverseasLine(String line, StockMarketType marketType) {
        String[] fields = line.split("\t", -1);
        if (fields.length < 24) {
            throw new RuntimeException("해외 종목 마스터 파일 라인 형식이 올바르지 않습니다.");
        }

        String nameEn = optionalText(fields[7]);
        String nameKo = optionalText(fields[6]);
        if (nameKo == null) {
            nameKo = nameEn;
        }

        OverseasStockMasterDto row = new OverseasStockMasterDto(
                marketType,
                requiredCode(fields[0], "국가 코드는 필수입니다."),
                requiredCode(fields[1], "거래소 ID는 필수입니다."),
                requiredCode(fields[2], "거래소 코드는 필수입니다."),
                requiredText(fields[3], "거래소명은 필수입니다."),
                requiredCode(fields[4], "해외 종목 심볼은 필수입니다."),
                optionalCode(fields[5]),
                requiredText(nameKo, "해외 종목명은 필수입니다."),
                nameEn,
                requiredCode(fields[8], "해외 증권 유형 코드는 필수입니다."),
                requiredCode(fields[9], "통화 코드는 필수입니다."));

        row.setFloatPosition(optionalCode(fields[10]));
        row.setDataType(optionalCode(fields[11]));
        row.setBasePrice(optionalCode(fields[12]));
        row.setBidOrderSize(optionalCode(fields[13]));
        row.setAskOrderSize(optionalCode(fields[14]));
        row.setMarketStartTime(optionalCode(fields[15]));
        row.setMarketEndTime(optionalCode(fields[16]));
        row.setDrYn(optionalCode(fields[17]));
        row.setDrCountryCode(optionalCode(fields[18]));
        row.setIndustryCode(optionalCode(fields[19]));
        row.setIndexConstituentYn(optionalCode(fields[20]));
        row.setTickSizeType(optionalCode(fields[21]));
        row.setEtpType(optionalCode(fields[22]));
        row.setTickSizeTypeDetail(optionalCode(fields[23]));
        return row;
    }

    private int getDomesticMetadataLength(StockMarketType marketType) {
        if (marketType == StockMarketType.KOSPI) {
            return KOSPI_METADATA_LENGTH;
        }

        if (marketType == StockMarketType.KOSDAQ) {
            return KOSDAQ_METADATA_LENGTH;
        }

        throw new RuntimeException("국내 종목 마스터 파싱 대상 시장이 아닙니다: " + marketType);
    }

    private static class DomesticMetadataFieldReader {
        private final String text;
        private int offset;

        private DomesticMetadataFieldReader(String text) {
            this.text = text;
        }

        private String next(int length) {
            if (offset + length > text.length()) {
                throw new RuntimeException("국내 종목 마스터 메타데이터 길이가 올바르지 않습니다.");
            }

            String value = text.substring(offset, offset + length);
            offset += length;
            return optionalCode(value);
        }

        private void validateFullyRead() {
            if (offset != text.length()) {
                throw new RuntimeException(
                        "국내 종목 마스터 메타데이터 길이가 필드 정의와 일치하지 않습니다. metadataLength="
                                + text.length() + ", consumedLength=" + offset);
            }
        }
    }
}
