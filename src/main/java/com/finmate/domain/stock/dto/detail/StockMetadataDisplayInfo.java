package com.finmate.domain.stock.dto.detail;

import com.finmate.domain.stock.Stock;
import com.finmate.domain.stock.metadata.DomesticStockMetadata;
import com.finmate.domain.stock.metadata.OverseasStockMetadata;
import lombok.Getter;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static com.finmate.domain.stock.dto.industry.StockIndustryDisplayUtils.displayNameOrCode;
import static com.finmate.domain.stock.dto.industry.StockIndustryDisplayUtils.isNoneCode;
import static com.finmate.domain.stock.dto.industry.StockIndustryDisplayUtils.mostSpecificDomesticDisplayName;

@Getter
public class StockMetadataDisplayInfo {
    private static final String EMPTY_DISPLAY_VALUE = "-";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(\\.\\d+)?");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{8}");
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000);
    private static final BigDecimal TEN_THOUSAND = BigDecimal.valueOf(10_000);

    private static final Map<String, String> DOMESTIC_SECURITY_GROUP_NAMES = Map.ofEntries(
            Map.entry("ST", "주권"),
            Map.entry("EF", "ETF"),
            Map.entry("FE", "ETF"),
            Map.entry("EN", "ETN"),
            Map.entry("EW", "ELW"),
            Map.entry("DR", "주식예탁증서"),
            Map.entry("RT", "리츠"),
            Map.entry("MF", "증권투자회사"),
            Map.entry("SC", "선박투자회사"),
            Map.entry("IF", "인프라펀드"),
            Map.entry("SW", "신주인수권증권"),
            Map.entry("SR", "신주인수권증서"),
            Map.entry("BC", "수익증권"),
            Map.entry("PF", "펀드"),
            Map.entry("FS", "외국주권")
    );

    private static final Map<String, String> MARKET_CAP_SCALE_NAMES = Map.of(
            "0", "제외",
            "1", "대형",
            "2", "중형",
            "3", "소형"
    );

    private static final Map<String, String> KOSPI200_SECTOR_NAMES = Map.ofEntries(
            Map.entry("0", "해당없음"),
            Map.entry("1", "건설"),
            Map.entry("2", "중공업"),
            Map.entry("3", "철강소재"),
            Map.entry("4", "에너지화학"),
            Map.entry("5", "정보통신"),
            Map.entry("6", "금융"),
            Map.entry("7", "필수소비재"),
            Map.entry("8", "자유소비재"),
            Map.entry("9", "산업재"),
            Map.entry("A", "건강관리"),
            Map.entry("B", "커뮤니케이션서비스")
    );

    private static final Map<String, String> ETP_PRODUCT_NAMES = Map.of(
            "0", "해당없음",
            "1", "투자회사형",
            "2", "수익증권형",
            "3", "ETN",
            "4", "손실제한 ETN",
            "5", "상장형 수익증권"
    );

    private static final Map<String, String> SHORT_OVER_NAMES = Map.of(
            "0", "해당없음",
            "1", "지정예고",
            "2", "지정",
            "3", "지정연장"
    );

    private static final Map<String, String> MARKET_ALERT_NAMES = Map.of(
            "00", "해당없음",
            "01", "투자주의",
            "02", "투자경고",
            "03", "투자위험"
    );

    private static final Map<String, String> LOCK_CLASS_NAMES = Map.of(
            "00", "해당없음",
            "01", "권리락",
            "02", "배당락",
            "03", "분배락",
            "04", "권배락",
            "05", "중간배당락"
    );

    private static final Map<String, String> FACE_VALUE_CHANGE_NAMES = Map.of(
            "00", "해당없음",
            "01", "액면분할",
            "02", "액면병합",
            "99", "기타"
    );

    private static final Map<String, String> CAPITAL_INCREASE_NAMES = Map.of(
            "00", "해당없음",
            "01", "유상증자",
            "02", "무상증자",
            "03", "유무상증자",
            "99", "기타"
    );

    private static final Map<String, String> PREFERRED_STOCK_NAMES = Map.of(
            "0", "보통주",
            "1", "구형우선주",
            "2", "신형우선주"
    );

    private static final Map<String, String> OVERSEAS_SECURITY_TYPE_NAMES = Map.of(
            "1", "지수",
            "2", "보통주",
            "3", "ETF/ETN/ETC",
            "4", "워런트"
    );

    private final String title;
    private final String description;
    private final String sourceLabel;
    private final List<StockMetadataBadge> badges;
    private final List<StockMetadataSection> sections;

    private StockMetadataDisplayInfo(String title,
                                     String description,
                                     String sourceLabel,
                                     List<StockMetadataBadge> badges,
                                     List<StockMetadataSection> sections) {
        this.title = title;
        this.description = description;
        this.sourceLabel = sourceLabel;
        this.badges = List.copyOf(badges);
        this.sections = List.copyOf(sections);
    }

    public static StockMetadataDisplayInfo from(Stock stock,
                                                DomesticStockMetadata domesticMetadata,
                                                OverseasStockMetadata overseasMetadata) {
        return from(stock, domesticMetadata, overseasMetadata, StockIndustryDisplayNames.empty());
    }

    public static StockMetadataDisplayInfo from(Stock stock,
                                                DomesticStockMetadata domesticMetadata,
                                                OverseasStockMetadata overseasMetadata,
                                                StockIndustryDisplayNames industryDisplayNames) {
        StockIndustryDisplayNames displayNames = industryDisplayNames == null
                ? StockIndustryDisplayNames.empty()
                : industryDisplayNames;
        if (domesticMetadata != null) {
            return domestic(domesticMetadata, displayNames);
        }

        if (overseasMetadata != null) {
            return overseas(overseasMetadata, displayNames);
        }

        String sourceLabel = stock == null ? "종목 마스터" : stock.getMarketType() + " 마스터";
        return new StockMetadataDisplayInfo(
                "마스터파일 기반 기업 정보",
                "아직 이 종목에 저장된 마스터파일 부가정보가 없습니다.",
                sourceLabel,
                List.of(),
                List.of());
    }

    public boolean hasContent() {
        return !badges.isEmpty() || !sections.isEmpty();
    }

    public boolean hasBadges() {
        return !badges.isEmpty();
    }

    private static StockMetadataDisplayInfo domestic(DomesticStockMetadata metadata,
                                                     StockIndustryDisplayNames industryDisplayNames) {
        List<StockMetadataBadge> badges = new ArrayList<>();
        List<StockMetadataSection> sections = new ArrayList<>();

        addDomesticIndexBadges(badges, metadata);
        addDomesticRiskBadges(badges, metadata);

        List<StockMetadataItem> overviewItems = new ArrayList<>();
        addMappedItem(overviewItems, "상품 성격", metadata.getSecurityGroupClassCode(), DOMESTIC_SECURITY_GROUP_NAMES);
        addMappedItem(overviewItems, "시가총액 규모", metadata.getMarketCapScaleClassCode(), MARKET_CAP_SCALE_NAMES);
        addRawItem(overviewItems, "업종", mostSpecificDomesticDisplayName(
                metadata.getSectorLargeDivisionCode(),
                metadata.getSectorMediumDivisionCode(),
                metadata.getSectorSmallDivisionCode(),
                industryDisplayNames::domesticSectorName));
        addDomesticIndustryItems(overviewItems, metadata);
        addMappedItem(overviewItems, "KOSPI200 섹터", metadata.getKospi200SectorCode(), KOSPI200_SECTOR_NAMES);
        addMappedItem(overviewItems, "우선주 구분", metadata.getPreferredStockClassCode(), PREFERRED_STOCK_NAMES);
        addMappedItem(overviewItems, "ETP 상품구분", metadata.getEtpProductClassCode(), ETP_PRODUCT_NAMES);
        addCodeItem(overviewItems, "그룹 코드", metadata.getGroupCode());
        addSection(sections, "기업 분류", "마스터파일에 저장된 종목·업종 분류입니다. 국내 종목은 KRX 섹터 여부가 있으면 업종명으로 함께 표시합니다.", overviewItems);

        List<StockMetadataItem> financeItems = new ArrayList<>();
        addHundredMillionWonItem(financeItems, "전일 시가총액", metadata.getPreviousDayMarketCap());
        addSharesFromThousandSharesItem(financeItems, "상장주식수", metadata.getListedShares());
        addQuantityItem(financeItems, "전일 거래량", metadata.getPreviousDayVolume());
        addWonItem(financeItems, "기준가", metadata.getStockBasePrice());
        addWonItem(financeItems, "액면가", metadata.getStockFaceValue());
        addDateItem(financeItems, "상장일", metadata.getStockListedDate());
        addWonItem(financeItems, "자본금", metadata.getCapital());
        addMonthItem(financeItems, "결산월", metadata.getSettlementMonth());
        addWonItem(financeItems, "공모가", metadata.getPublicOfferingPrice());
        addHundredMillionWonItem(financeItems, "매출액", metadata.getSales());
        addHundredMillionWonItem(financeItems, "영업이익", metadata.getOperatingProfit());
        addHundredMillionWonItem(financeItems, "경상이익", metadata.getOrdinaryProfit());
        addHundredMillionWonItem(financeItems, "당기순이익", metadata.getNetIncome());
        addPercentItem(financeItems, "ROE", metadata.getRoe());
        addDateItem(financeItems, "재무 기준일", metadata.getBaseDate());
        addSection(sections, "기초 재무", "시가총액·손익 항목은 억 원 단위, 자본금·가격 항목은 원 단위로 표시합니다. 상장주식수는 마스터파일의 천주 단위를 주 단위로 환산합니다.", financeItems);

        List<StockMetadataItem> tradingItems = new ArrayList<>();
        addQuantityItem(tradingItems, "정규장 주문단위", metadata.getRegularMarketTradeQuantityUnit());
        addQuantityItem(tradingItems, "시간외 주문단위", metadata.getAfterHoursMarketTradeQuantityUnit());
        addPercentItem(tradingItems, "증거금률", metadata.getMarginRate());
        addYesNoItem(tradingItems, "신용주문 가능", metadata.getCreditAvailableYn());
        addRawItem(tradingItems, "신용기간", appendSuffix(formatNumber(metadata.getCreditDays()), "일"));
        addYesNoItem(tradingItems, "담보대출 가능", metadata.getSecuritiesLendingAvailableYn());
        addYesNoItem(tradingItems, "대주 가능", metadata.getStockLoanAvailableYn());
        addYesNoItem(tradingItems, "회사 신용한도 초과", metadata.getCompanyCreditLimitOverYn());
        addSection(sections, "거래 조건", "주문 가능성과 증거금 판단에 쓰는 원본 조건입니다.", tradingItems);

        List<StockMetadataItem> riskItems = new ArrayList<>();
        addYesNoItem(riskItems, "거래정지", metadata.getTradingHaltYn());
        addYesNoItem(riskItems, "관리종목", metadata.getManagedIssueYn());
        addYesNoItem(riskItems, "정리매매", metadata.getLiquidationTradeYn());
        addMappedItem(riskItems, "시장경고", metadata.getMarketAlertClassCode(), MARKET_ALERT_NAMES);
        addYesNoItem(riskItems, "시장경고위험 예고", metadata.getMarketAlertRiskNoticeYn());
        addYesNoItem(riskItems, "투자주의환기", metadata.getInvestmentAlertYn());
        addYesNoItem(riskItems, "저유동성", metadata.getLowLiquidityYn());
        addMappedItem(riskItems, "단기과열", metadata.getShortOverClassCode(), SHORT_OVER_NAMES);
        addYesNoItem(riskItems, "공매도과열", metadata.getShortSaleOverheatedYn());
        addYesNoItem(riskItems, "이상급등", metadata.getAbnormalRunupYn());
        addYesNoItem(riskItems, "불성실공시", metadata.getUnfaithfulDisclosureYn());
        addYesNoItem(riskItems, "우회상장", metadata.getBackdoorListingYn());
        addMappedItem(riskItems, "락 구분", metadata.getLockClassCode(), LOCK_CLASS_NAMES);
        addMappedItem(riskItems, "액면가 변경", metadata.getFaceValueChangeClassCode(), FACE_VALUE_CHANGE_NAMES);
        addMappedItem(riskItems, "증자 구분", metadata.getCapitalIncreaseClassCode(), CAPITAL_INCREASE_NAMES);
        addSection(sections, "위험·이벤트", "거래 전 확인하면 좋은 시장 경고와 권리 이벤트입니다.", riskItems);

        List<StockMetadataItem> syncItems = new ArrayList<>();
        addRawItem(syncItems, "마스터 동기화", formatDateTime(metadata.getLastSyncedAt()));
        addSection(sections, "데이터 기준", "현재 화면은 저장된 마스터파일 정보만 사용합니다.", syncItems);

        return new StockMetadataDisplayInfo(
                "국내 종목 마스터 정보",
                "KIS 국내 종목 마스터파일에서 저장한 기업·거래·위험 정보입니다.",
                "국내 마스터파일",
                badges,
                sections);
    }

    private static StockMetadataDisplayInfo overseas(OverseasStockMetadata metadata,
                                                     StockIndustryDisplayNames industryDisplayNames) {
        List<StockMetadataBadge> badges = new ArrayList<>();
        List<StockMetadataSection> sections = new ArrayList<>();

        addBadgeIfHasText(badges, metadata.getExchangeName(), "info");
        addBadgeIfHasText(badges, metadata.getCurrency(), "neutral");
        addBadgeIfYes(badges, metadata.getDrYn(), "DR", "warning");
        addBadgeIfYes(badges, metadata.getIndexConstituentYn(), "지수 구성", "info");
        addBadgeIfHasText(
                badges,
                textWithPrefix("업종", displayNameOrCode(metadata.getIndustryCode(), industryDisplayNames.overseasIndustryName())),
                "info");

        List<StockMetadataItem> overviewItems = new ArrayList<>();
        addCodeItem(overviewItems, "국가 코드", metadata.getNationalCode());
        addRawItem(overviewItems, "거래소", joinCodeName(metadata.getExchangeCode(), metadata.getExchangeName()));
        addCodeItem(overviewItems, "거래소 ID", metadata.getExchangeId());
        addMappedItem(overviewItems, "증권 유형", metadata.getSecurityTypeCode(), OVERSEAS_SECURITY_TYPE_NAMES);
        addCodeItem(overviewItems, "데이터 유형", metadata.getDataType());
        addResolvedCodeItem(overviewItems, "업종", metadata.getIndustryCode(), industryDisplayNames.overseasIndustryName());
        addRawItem(overviewItems, "통화", metadata.getCurrency());
        addCodeItem(overviewItems, "ETP 유형", metadata.getEtpType());
        addYesNoItem(overviewItems, "DR 여부", metadata.getDrYn());
        addCodeItem(overviewItems, "DR 국가", metadata.getDrCountryCode());
        addYesNoItem(overviewItems, "지수 구성종목", metadata.getIndexConstituentYn());
        addSection(sections, "기업 분류", "해외 마스터파일에 저장된 거래소·상품 분류입니다.", overviewItems);

        List<StockMetadataItem> tradingItems = new ArrayList<>();
        addCurrencyItem(tradingItems, "기준가", metadata.getBasePrice(), metadata.getCurrency());
        addRawItem(tradingItems, "가격 소수점", appendPrefixSuffix(metadata.getFloatPosition(), "소수 ", "자리"));
        addQuantityItem(tradingItems, "매수 주문단위", metadata.getBidOrderSize());
        addQuantityItem(tradingItems, "매도 주문단위", metadata.getAskOrderSize());
        addRawItem(tradingItems, "시장 시간", marketTime(metadata.getMarketStartTime(), metadata.getMarketEndTime()));
        addCodeItem(tradingItems, "호가 단위 유형", metadata.getTickSizeType());
        addCodeItem(tradingItems, "호가 단위 상세", metadata.getTickSizeTypeDetail());
        addSection(sections, "거래 조건", "가격 표시와 주문 검증에 필요한 해외 원본 조건입니다.", tradingItems);

        List<StockMetadataItem> syncItems = new ArrayList<>();
        addRawItem(syncItems, "마스터 동기화", formatDateTime(metadata.getLastSyncedAt()));
        addSection(sections, "데이터 기준", "현재 화면은 저장된 마스터파일 정보만 사용합니다.", syncItems);

        return new StockMetadataDisplayInfo(
                "해외 종목 마스터 정보",
                "KIS 해외 종목 마스터파일에서 저장한 거래소·상품·주문 조건입니다.",
                "해외 마스터파일",
                badges,
                sections);
    }

    private static void addDomesticIndexBadges(List<StockMetadataBadge> badges, DomesticStockMetadata metadata) {
        addBadgeIfYes(badges, metadata.getKospiIssueYn(), "KOSPI", "info");
        addBadgeIfYes(badges, metadata.getKospi100IssueYn(), "KOSPI100", "info");
        addBadgeIfYes(badges, metadata.getKospi50IssueYn(), "KOSPI50", "info");
        addBadgeIfYes(badges, metadata.getKrxIssueYn(), "KRX", "info");
        addBadgeIfYes(badges, metadata.getKrx100IssueYn(), "KRX100", "info");
        addBadgeIfYes(badges, metadata.getKrx300IssueYn(), "KRX300", "info");
        addBadgeIfYes(badges, metadata.getKosdaq150IndexYn(), "KOSDAQ150", "info");
        addBadgeIfYes(badges, metadata.getGovernanceIndexIssueYn(), "지배구조", "info");
        addBadgeIfYes(badges, metadata.getSriIndexYn(), "SRI", "info");
        addBadgeIfYes(badges, metadata.getManufacturingClassYn(), "제조업", "neutral");
        addBadgeIfYes(badges, metadata.getVentureIssueYn(), "벤처", "neutral");
        addBadgeIfYes(badges, metadata.getKrxSemiconductorYn(), "반도체", "info");
        addBadgeIfYes(badges, metadata.getKrxBioYn(), "바이오", "info");
        addBadgeIfYes(badges, metadata.getKrxCarYn(), "자동차", "info");
        addBadgeIfYes(badges, metadata.getKrxBankYn(), "은행", "info");
        addBadgeIfYes(badges, metadata.getKrxEnergyChemicalYn(), "에너지화학", "info");
        addBadgeIfYes(badges, metadata.getKrxSteelYn(), "철강", "info");
        addBadgeIfYes(badges, metadata.getKrxMediaCommunicationYn(), "미디어통신", "info");
        addBadgeIfYes(badges, metadata.getKrxConstructionYn(), "건설", "info");
        addBadgeIfYes(badges, metadata.getKrxSecuritiesYn(), "증권", "info");
        addBadgeIfYes(badges, metadata.getKrxShipYn(), "선박", "info");
        addBadgeIfYes(badges, metadata.getKrxInsuranceYn(), "보험", "info");
        addBadgeIfYes(badges, metadata.getKrxTransportationYn(), "운송", "info");
    }

    private static void addDomesticIndustryItems(List<StockMetadataItem> items, DomesticStockMetadata metadata) {
        addJoinedItem(items,
                "산업 분류",
                yesLabel(metadata.getManufacturingClassYn(), "제조업"),
                yesLabel(metadata.getVentureIssueYn(), "벤처기업"));
        addJoinedItem(items,
                "KRX 섹터",
                yesLabel(metadata.getKrxCarYn(), "자동차"),
                yesLabel(metadata.getKrxSemiconductorYn(), "반도체"),
                yesLabel(metadata.getKrxBioYn(), "바이오"),
                yesLabel(metadata.getKrxBankYn(), "은행"),
                yesLabel(metadata.getKrxEnergyChemicalYn(), "에너지화학"),
                yesLabel(metadata.getKrxSteelYn(), "철강"),
                yesLabel(metadata.getKrxMediaCommunicationYn(), "미디어통신"),
                yesLabel(metadata.getKrxConstructionYn(), "건설"),
                yesLabel(metadata.getKrxSecuritiesYn(), "증권"),
                yesLabel(metadata.getKrxShipYn(), "선박"),
                yesLabel(metadata.getKrxInsuranceYn(), "보험"),
                yesLabel(metadata.getKrxTransportationYn(), "운송"));
    }

    private static void addDomesticRiskBadges(List<StockMetadataBadge> badges, DomesticStockMetadata metadata) {
        addBadgeIfYes(badges, metadata.getLowLiquidityYn(), "저유동성", "warning");
        addBadgeIfYes(badges, metadata.getSpacYn(), "SPAC", "warning");
        addBadgeIfYes(badges, metadata.getInvestmentAlertYn(), "투자주의환기", "warning");
        addBadgeIfYes(badges, metadata.getTradingHaltYn(), "거래정지", "danger");
        addBadgeIfYes(badges, metadata.getLiquidationTradeYn(), "정리매매", "danger");
        addBadgeIfYes(badges, metadata.getManagedIssueYn(), "관리종목", "danger");
        addBadgeIfYes(badges, metadata.getUnfaithfulDisclosureYn(), "불성실공시", "warning");
        addBadgeIfYes(badges, metadata.getShortSaleOverheatedYn(), "공매도과열", "warning");
        addBadgeIfYes(badges, metadata.getAbnormalRunupYn(), "이상급등", "warning");
        addBadgeIfCodeIsActive(badges, metadata.getMarketAlertClassCode(), MARKET_ALERT_NAMES, "시장경고", "danger");
        addBadgeIfCodeIsActive(badges, metadata.getShortOverClassCode(), SHORT_OVER_NAMES, "단기과열", "warning");
    }

    private static void addSection(List<StockMetadataSection> sections,
                                   String title,
                                   String description,
                                   List<StockMetadataItem> items) {
        if (!items.isEmpty()) {
            sections.add(new StockMetadataSection(title, description, items));
        }
    }

    private static void addRawItem(List<StockMetadataItem> items, String label, String value) {
        String normalizedValue = normalize(value);
        if (hasText(normalizedValue)) {
            items.add(new StockMetadataItem(label, normalizedValue));
        }
    }

    private static void addCodeItem(List<StockMetadataItem> items, String label, String code) {
        String normalizedCode = normalize(code);
        if (hasText(normalizedCode)) {
            items.add(new StockMetadataItem(label, "코드 " + normalizedCode));
        }
    }

    private static void addResolvedCodeItem(List<StockMetadataItem> items,
                                            String label,
                                            String code,
                                            String resolvedName) {
        String displayValue = displayNameOrCode(code, resolvedName);
        if (displayValue != null) {
            items.add(new StockMetadataItem(label, displayValue));
        }
    }

    private static void addMappedItem(List<StockMetadataItem> items,
                                      String label,
                                      String code,
                                      Map<String, String> codeNames) {
        String displayValue = mappedCode(code, codeNames);
        if (displayValue != null) {
            items.add(new StockMetadataItem(label, displayValue));
        }
    }

    private static void addJoinedItem(List<StockMetadataItem> items, String label, String... values) {
        List<String> displayValues = new ArrayList<>();
        for (String value : values) {
            String normalizedValue = normalize(value);
            if (hasText(normalizedValue)) {
                displayValues.add(normalizedValue);
            }
        }

        if (!displayValues.isEmpty()) {
            items.add(new StockMetadataItem(label, String.join(" · ", displayValues)));
        }
    }

    private static void addNumberItem(List<StockMetadataItem> items, String label, String value) {
        addRawItem(items, label, formatNumber(value));
    }

    private static void addWonItem(List<StockMetadataItem> items, String label, String value) {
        addRawItem(items, label, appendSuffix(formatNumber(value), "원"));
    }

    private static void addHundredMillionWonItem(List<StockMetadataItem> items, String label, String value) {
        addRawItem(items, label, formatHundredMillionWon(value));
    }

    private static void addCurrencyItem(List<StockMetadataItem> items, String label, String value, String currency) {
        addRawItem(items, label, appendCurrencyCode(formatNumber(value), currency));
    }

    private static void addQuantityItem(List<StockMetadataItem> items, String label, String value) {
        addRawItem(items, label, appendSuffix(formatNumber(value), "주"));
    }

    private static void addSharesFromThousandSharesItem(List<StockMetadataItem> items, String label, String value) {
        addRawItem(items, label, appendSuffix(formatScaledNumber(value, THOUSAND), "주"));
    }

    private static void addPercentItem(List<StockMetadataItem> items, String label, String value) {
        addRawItem(items, label, appendSuffix(formatNumber(value), "%"));
    }

    private static void addMonthItem(List<StockMetadataItem> items, String label, String value) {
        addRawItem(items, label, appendSuffix(formatNumber(value), "월"));
    }

    private static void addDateItem(List<StockMetadataItem> items, String label, String value) {
        addRawItem(items, label, formatDate(value));
    }

    private static void addYesNoItem(List<StockMetadataItem> items, String label, String value) {
        addRawItem(items, label, yesNo(value));
    }

    private static void addBadgeIfYes(List<StockMetadataBadge> badges,
                                      String value,
                                      String label,
                                      String type) {
        if (isYes(value)) {
            badges.add(new StockMetadataBadge(label, type));
        }
    }

    private static void addBadgeIfHasText(List<StockMetadataBadge> badges, String label, String type) {
        String normalizedLabel = normalize(label);
        if (hasText(normalizedLabel)) {
            badges.add(new StockMetadataBadge(normalizedLabel, type));
        }
    }

    private static void addBadgeIfCodeIsActive(List<StockMetadataBadge> badges,
                                               String code,
                                               Map<String, String> codeNames,
                                               String prefix,
                                               String type) {
        String normalizedCode = normalize(code);
        if (!hasText(normalizedCode) || isNoneCode(normalizedCode)) {
            return;
        }

        String codeName = codeNames.getOrDefault(normalizedCode, "코드 " + normalizedCode);
        badges.add(new StockMetadataBadge(prefix + " " + codeName, type));
    }

    private static String mappedCode(String code, Map<String, String> codeNames) {
        String normalizedCode = normalize(code);
        if (!hasText(normalizedCode)) {
            return null;
        }

        String codeName = codeNames.get(normalizedCode);
        if (!hasText(codeName)) {
            return "코드 " + normalizedCode;
        }

        return codeName + " (" + normalizedCode + ")";
    }

    private static String yesNo(String value) {
        String normalizedValue = normalize(value);
        if (!hasText(normalizedValue)) {
            return null;
        }

        if ("Y".equalsIgnoreCase(normalizedValue)) {
            return "예";
        }

        if ("N".equalsIgnoreCase(normalizedValue)) {
            return "아니오";
        }

        return normalizedValue;
    }

    private static boolean isYes(String value) {
        return "Y".equalsIgnoreCase(normalize(value));
    }

    private static String yesLabel(String value, String label) {
        if (!isYes(value)) {
            return null;
        }

        return label;
    }

    private static String formatNumber(String value) {
        String normalizedValue = normalize(value);
        if (!hasText(normalizedValue)) {
            return null;
        }

        String numericText = normalizedValue.replace(",", "");
        if (!NUMBER_PATTERN.matcher(numericText).matches()) {
            return normalizedValue;
        }

        BigDecimal number = new BigDecimal(numericText);
        return formatDecimal(number);
    }

    private static String formatScaledNumber(String value, BigDecimal multiplier) {
        String normalizedValue = normalize(value);
        if (!hasText(normalizedValue)) {
            return null;
        }

        String numericText = normalizedValue.replace(",", "");
        if (!NUMBER_PATTERN.matcher(numericText).matches()) {
            return normalizedValue;
        }

        return formatDecimal(new BigDecimal(numericText).multiply(multiplier));
    }

    private static String formatHundredMillionWon(String value) {
        String normalizedValue = normalize(value);
        if (!hasText(normalizedValue)) {
            return null;
        }

        String numericText = normalizedValue.replace(",", "");
        if (!NUMBER_PATTERN.matcher(numericText).matches()) {
            return appendSuffix(normalizedValue, "억 원");
        }

        BigDecimal amount = new BigDecimal(numericText);
        BigDecimal strippedAmount = amount.stripTrailingZeros();
        if (strippedAmount.scale() > 0 || amount.abs().compareTo(TEN_THOUSAND) < 0) {
            return appendSuffix(formatDecimal(amount), "억 원");
        }

        BigDecimal absAmount = amount.abs();
        BigDecimal trillionPart = absAmount.divideToIntegralValue(TEN_THOUSAND);
        BigDecimal hundredMillionPart = absAmount.remainder(TEN_THOUSAND);
        String signPrefix = amount.signum() < 0 ? "-" : "";

        if (hundredMillionPart.signum() == 0) {
            return signPrefix + formatDecimal(trillionPart) + "조 원";
        }

        return signPrefix + formatDecimal(trillionPart)
                + "조 "
                + formatDecimal(hundredMillionPart)
                + "억 원";
    }

    private static String formatDecimal(BigDecimal number) {
        BigDecimal strippedNumber = number.stripTrailingZeros();
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.KOREA);
        formatter.setMaximumFractionDigits(Math.max(0, Math.min(6, strippedNumber.scale())));
        return formatter.format(number);
    }

    private static String formatDate(String value) {
        String normalizedValue = normalize(value);
        if (!hasText(normalizedValue)) {
            return null;
        }

        if (!DATE_PATTERN.matcher(normalizedValue).matches()) {
            return normalizedValue;
        }

        return normalizedValue.substring(0, 4)
                + "-"
                + normalizedValue.substring(4, 6)
                + "-"
                + normalizedValue.substring(6, 8);
    }

    private static String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }

        return value.format(DATE_TIME_FORMATTER);
    }

    private static String marketTime(String startTime, String endTime) {
        String formattedStartTime = formatTime(startTime);
        String formattedEndTime = formatTime(endTime);

        if (hasText(formattedStartTime) && hasText(formattedEndTime)) {
            return formattedStartTime + " ~ " + formattedEndTime;
        }

        if (hasText(formattedStartTime)) {
            return formattedStartTime + " 시작";
        }

        if (hasText(formattedEndTime)) {
            return formattedEndTime + " 종료";
        }

        return null;
    }

    private static String formatTime(String value) {
        String normalizedValue = normalize(value);
        if (!hasText(normalizedValue)) {
            return null;
        }

        if (normalizedValue.matches("\\d{4}")) {
            return normalizedValue.substring(0, 2) + ":" + normalizedValue.substring(2, 4);
        }

        return normalizedValue;
    }

    private static String joinCodeName(String code, String name) {
        String normalizedCode = normalize(code);
        String normalizedName = normalize(name);
        if (hasText(normalizedCode) && hasText(normalizedName)) {
            return normalizedName + " (" + normalizedCode + ")";
        }

        if (hasText(normalizedName)) {
            return normalizedName;
        }

        if (hasText(normalizedCode)) {
            return "코드 " + normalizedCode;
        }

        return null;
    }

    private static String textWithPrefix(String prefix, String text) {
        String normalizedText = normalize(text);
        if (!hasText(normalizedText)) {
            return null;
        }

        return prefix + " " + normalizedText;
    }

    private static String appendPrefixSuffix(String value, String prefix, String suffix) {
        String normalizedValue = normalize(value);
        if (!hasText(normalizedValue)) {
            return null;
        }

        return prefix + normalizedValue + suffix;
    }

    private static String appendCurrencyCode(String value, String currency) {
        if (!hasText(value)) {
            return null;
        }

        String normalizedCurrency = normalize(currency);
        if (!hasText(normalizedCurrency)) {
            return value;
        }

        return value + " " + normalizedCurrency;
    }

    private static String appendSuffix(String value, String suffix) {
        if (!hasText(value)) {
            return null;
        }

        if (!hasText(suffix)) {
            return value;
        }

        return value + suffix;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty() || EMPTY_DISPLAY_VALUE.equals(trimmedValue)) {
            return null;
        }

        return trimmedValue;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Getter
    public static class StockMetadataBadge {
        private final String label;
        private final String type;

        public StockMetadataBadge(String label, String type) {
            this.label = label;
            this.type = type;
        }
    }

    @Getter
    public static class StockMetadataSection {
        private final String title;
        private final String description;
        private final List<StockMetadataItem> items;

        public StockMetadataSection(String title, String description, List<StockMetadataItem> items) {
            this.title = title;
            this.description = description;
            this.items = List.copyOf(items);
        }
    }

    @Getter
    public static class StockMetadataItem {
        private final String label;
        private final String value;

        public StockMetadataItem(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }
}
