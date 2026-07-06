package com.finmate.domain.stock.metadata;

import com.finmate.domain.stock.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// KOSPI/KOSDAQ 종목 마스터 파일의 국내주식 상세 메타데이터를 저장한다.
// Stock에는 종목 검색과 거래에 공통으로 필요한 핵심 정보만 두고, 이 엔티티에는 시장/지수/위험/재무 관련 원본 부가정보를 보관한다.
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        // 하나의 stock에 포함된 메타데이터이기 때문에 stock id가 unique해야 한다.
        name = "domestic_stock_metadata",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_domestic_stock_metadata_stock", columnNames = "stock_id")
        }
)
@Entity
public class DomesticStockMetadata {
    // 국내주식 메타데이터 테이블의 PK다. 실제 종목과의 1:1 연결은 stock 필드로 관리한다.
    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 메타데이터가 속한 공통 종목 정보다. 종목명, 시장, 단축코드 같은 핵심 식별 정보는 Stock에서 관리한다.
    @Setter(AccessLevel.NONE)
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, unique = true)
    private Stock stock;

    // 증권그룹구분코드다. 보통주, ETF, ELW, 리츠 등 종목의 상품 성격을 구분하는 원본 코드다.
    // 예: ST=주권, EF=ETF, EW=ELW, RT=부동산투자회사, MF=증권투자회사, DR=주식예탁증서.
    @Column(name = "security_group_class_code", length = 2)
    private String securityGroupClassCode;

    // 시가총액 규모 구분 코드다. 기업가치를 주가와 상장주식수 기준으로 대형/중형/소형으로 나눈다.
    // KOSPI는 0=제외, 1=대형, 2=중형, 3=소형이고 KOSDAQ은 KOSDAQ100/중형/소형 구분으로 쓰인다.
    @Column(name = "market_cap_scale_class_code", length = 1)
    private String marketCapScaleClassCode;

    // 지수 업종 대분류 코드다. 종목이 속한 산업을 가장 큰 단위로 분류한다.
    // 업종별 등락률, 같은 산업군 비교, 섹터 포트폴리오 구성 같은 기능의 기준 데이터가 된다.
    @Column(name = "sector_large_division_code", length = 4)
    private String sectorLargeDivisionCode;

    // 지수 업종 중분류 코드다. 대분류보다 세부적인 산업군을 나타내며 업종 비교에 사용한다.
    // 예를 들어 제조업 안에서도 전자부품, 화학, 자동차처럼 더 좁은 비교군을 만들 때 활용한다.
    @Column(name = "sector_medium_division_code", length = 4)
    private String sectorMediumDivisionCode;

    // 지수 업종 소분류 코드다. 같은 업종 안에서도 더 세밀한 사업 영역을 구분한다.
    // 종목 검색 필터나 동종업계 비교를 정교하게 만들 때 사용할 수 있다.
    @Column(name = "sector_small_division_code", length = 4)
    private String sectorSmallDivisionCode;

    // KOSPI 제조업 구분 여부다. 제조업으로 분류되는 기업인지 확인하는 원본 Y/N 값이다.
    // 제조업/비제조업을 구분해 경기민감주 성격을 분석할 때 참고할 수 있다.
    @Column(name = "manufacturing_class_yn", length = 1)
    private String manufacturingClassYn;

    // KOSDAQ 벤처기업 여부다. 코스닥 시장에서 벤처기업으로 분류되는지 나타낸다.
    // 성장성은 높지만 변동성과 재무 안정성 리스크가 클 수 있는 종목군을 구분하는 데 참고한다.
    @Column(name = "venture_issue_yn", length = 1)
    private String ventureIssueYn;

    // 저유동성종목 여부다. 거래량이나 호가가 부족해 매매 체결이 어려울 수 있는 종목인지 나타낸다.
    // 유동성이 낮으면 원하는 가격에 사고팔기 어렵고 호가 스프레드가 커질 수 있다.
    @Column(name = "low_liquidity_yn", length = 1)
    private String lowLiquidityYn;

    // KOSPI 지배구조 지수 종목 여부다. 기업지배구조 관련 지수 구성 종목인지 나타낸다.
    // ESG나 주주친화 정책 관점에서 종목을 살펴볼 때 보조 지표로 사용할 수 있다.
    @Column(name = "governance_index_issue_yn", length = 1)
    private String governanceIndexIssueYn;

    // KOSPI200 섹터 업종 코드다. KOSPI200 내에서 건설, 금융, 건강관리 등 섹터를 구분한다.
    // 예: 1=건설, 2=중공업, 3=철강소재, 4=에너지화학, 5=정보통신, 6=금융.
    @Column(name = "kospi200_sector_code", length = 1)
    private String kospi200SectorCode;

    // KOSPI100 종목 여부다. 코스피 대표 대형주 지수인 KOSPI100 구성 종목인지 나타낸다.
    // 대형 우량주 중심의 필터링이나 대표주 분석에 사용할 수 있다.
    @Column(name = "kospi100_issue_yn", length = 1)
    private String kospi100IssueYn;

    // KOSPI50 종목 여부다. 코스피 초대형 대표주 중심 지수인 KOSPI50 구성 종목인지 나타낸다.
    // 시장 영향력이 큰 초대형 종목을 구분하는 데 유용하다.
    @Column(name = "kospi50_issue_yn", length = 1)
    private String kospi50IssueYn;

    // KRX 종목 여부다. KRX 관련 대표 지수 또는 분류 체계에 포함되는 종목인지 나타낸다.
    // 한국거래소 지수/섹터 분류 편입 여부를 원본 그대로 보관한다.
    @Column(name = "krx_issue_yn", length = 1)
    private String krxIssueYn;

    // ETP 상품구분코드다. ETF, ETN, 손실제한 ETN 등 상장지수상품 유형을 구분한다.
    // 예: 0=해당없음, 1=투자회사형, 2=수익증권형, 3=ETN, 4=손실제한ETN, 5=상장형수익증권.
    @Column(name = "etp_product_class_code", length = 1)
    private String etpProductClassCode;

    // KOSPI ELW 발행 여부다. 해당 기초자산을 기반으로 ELW가 발행되어 있는지 나타낸다.
    // ELW는 기초자산 가격 변동에 연동되는 파생결합증권이므로 일반 주식과 위험 구조가 다르다.
    @Column(name = "elw_published_yn", length = 1)
    private String elwPublishedYn;

    // KRX100 종목 여부다. 유가증권/코스닥을 통합한 대표 지수 구성 종목인지 나타낸다.
    // 시장 전체 대표 종목을 추릴 때 사용할 수 있다.
    @Column(name = "krx100_issue_yn", length = 1)
    private String krx100IssueYn;

    // KRX 자동차 섹터 여부다. KRX 섹터지수 중 자동차 업종에 속하는지 나타낸다.
    // 완성차, 부품주 등 자동차 밸류체인 종목군을 볼 때 활용한다.
    @Column(name = "krx_car_yn", length = 1)
    private String krxCarYn;

    // KRX 반도체 섹터 여부다. KRX 섹터지수 중 반도체 업종에 속하는지 나타낸다.
    // 메모리, 장비, 소재 등 반도체 업황과 함께 움직일 가능성이 큰 종목을 구분한다.
    @Column(name = "krx_semiconductor_yn", length = 1)
    private String krxSemiconductorYn;

    // KRX 바이오 섹터 여부다. KRX 섹터지수 중 바이오 업종에 속하는지 나타낸다.
    // 임상, 신약, 의료기기 등 성장성과 이벤트 리스크가 큰 종목군을 분류하는 데 참고한다.
    @Column(name = "krx_bio_yn", length = 1)
    private String krxBioYn;

    // KRX 은행 섹터 여부다. KRX 섹터지수 중 은행 업종에 속하는지 나타낸다.
    // 금리, 대출 성장, 충당금 이슈에 민감한 금융주 그룹을 구분한다.
    @Column(name = "krx_bank_yn", length = 1)
    private String krxBankYn;

    // 기업인수목적회사 여부다. SPAC처럼 합병을 목적으로 상장된 회사인지 나타낸다.
    // 일반 영업회사와 달리 합병 대상 발굴이 핵심이므로 분석 방식이 다르다.
    @Column(name = "spac_yn", length = 1)
    private String spacYn;

    // KRX 에너지화학 섹터 여부다. KRX 섹터지수 중 에너지/화학 업종에 속하는지 나타낸다.
    // 유가, 원재료 가격, 정제마진, 화학 사이클에 민감한 종목군을 식별한다.
    @Column(name = "krx_energy_chemical_yn", length = 1)
    private String krxEnergyChemicalYn;

    // KRX 철강 섹터 여부다. KRX 섹터지수 중 철강 업종에 속하는지 나타낸다.
    // 원자재 가격, 중국 수요, 경기 사이클 영향을 크게 받는 종목군을 구분한다.
    @Column(name = "krx_steel_yn", length = 1)
    private String krxSteelYn;

    // 단기과열종목 구분 코드다. 단기간 이상 급등/과열로 지정예고, 지정, 연장 상태인지 구분한다.
    // 예: 0=해당없음, 1=지정예고, 2=지정, 3=지정연장. 투기적 과열 여부를 판단할 때 중요하다.
    @Column(name = "short_over_class_code", length = 1)
    private String shortOverClassCode;

    // KRX 미디어통신 섹터 여부다. KRX 섹터지수 중 미디어/통신 업종에 속하는지 나타낸다.
    // 통신사, 콘텐츠, 플랫폼 등 미디어/커뮤니케이션 종목군을 구분한다.
    @Column(name = "krx_media_communication_yn", length = 1)
    private String krxMediaCommunicationYn;

    // KRX 건설 섹터 여부다. KRX 섹터지수 중 건설 업종에 속하는지 나타낸다.
    // 부동산 경기, 수주, 금리 변화에 민감한 건설주 분류에 사용한다.
    @Column(name = "krx_construction_yn", length = 1)
    private String krxConstructionYn;

    // KOSPI 구 금융서비스 섹터 여부 원본값이다. 현재는 삭제된 과거 분류지만 원본 추적용으로 보관한다.
    // 과거 데이터와 현재 데이터의 스키마 차이를 추적할 때 필요할 수 있다.
    @Column(name = "krx_financial_service_yn", length = 1)
    private String krxFinancialServiceYn;

    // KOSDAQ 투자주의환기종목 여부다. 재무/공시/내부통제 위험이 있어 투자 유의가 필요한 종목인지 나타낸다.
    // 단순 가격 급등보다 기업 계속성, 내부통제, 공시 신뢰성 쪽 리스크를 보는 플래그다.
    @Column(name = "investment_alert_yn", length = 1)
    private String investmentAlertYn;

    // KRX 증권 섹터 여부다. KRX 섹터지수 중 증권 업종에 속하는지 나타낸다.
    // 거래대금, 금리, IB 실적에 민감한 증권사 종목군을 구분한다.
    @Column(name = "krx_securities_yn", length = 1)
    private String krxSecuritiesYn;

    // KRX 선박 섹터 여부다. KRX 섹터지수 중 선박 관련 업종에 속하는지 나타낸다.
    // 조선, 선박 관련 산업 분류를 추적할 때 사용한다.
    @Column(name = "krx_ship_yn", length = 1)
    private String krxShipYn;

    // KRX 보험 섹터 여부다. KRX 섹터지수 중 보험 업종에 속하는지 나타낸다.
    // 금리, 손해율, 투자수익률에 영향을 받는 보험사 종목군을 구분한다.
    @Column(name = "krx_insurance_yn", length = 1)
    private String krxInsuranceYn;

    // KRX 운송 섹터 여부다. KRX 섹터지수 중 운송 업종에 속하는지 나타낸다.
    // 항공, 해운, 물류처럼 운임과 유가에 민감한 종목군을 분류한다.
    @Column(name = "krx_transportation_yn", length = 1)
    private String krxTransportationYn;

    // KOSPI SRI 지수 여부다. 사회책임투자 관련 지수 구성 종목인지 나타낸다.
    // 재무지표 외에 사회책임투자 관점의 지수 편입 여부를 확인할 수 있다.
    @Column(name = "sri_index_yn", length = 1)
    private String sriIndexYn;

    // KOSDAQ150 지수 여부다. 코스닥 대표 150개 종목 지수에 포함되는지 나타낸다.
    // 코스닥 대형/대표 성장주를 필터링하거나 지수 편입 효과를 분석할 때 참고한다.
    @Column(name = "kosdaq150_index_yn", length = 1)
    private String kosdaq150IndexYn;

    // 주식 기준가 원본값이다. 장 시작 전 가격제한폭 계산 등에 기준이 되는 가격이다.
    // 상한가/하한가 계산, 기준가 대비 등락률 계산의 기준으로 활용된다.
    @Column(name = "stock_base_price", length = 9)
    private String stockBasePrice;

    // 정규시장 매매 수량 단위다. 정규장 주문 시 허용되는 최소 거래 수량 단위다.
    // 국내 주식은 보통 1주 단위지만, 상품 유형이나 시장 제도에 따라 원본값을 보관한다.
    @Column(name = "regular_market_trade_quantity_unit", length = 5)
    private String regularMarketTradeQuantityUnit;

    // 시간외시장 매매 수량 단위다. 시간외 거래에서 적용되는 최소 거래 수량 단위다.
    // 정규장과 다른 거래 제약이 있을 수 있어 별도 필드로 둔다.
    @Column(name = "after_hours_market_trade_quantity_unit", length = 5)
    private String afterHoursMarketTradeQuantityUnit;

    // 거래정지 여부 원본값이다. 거래소 조치로 현재 매매가 정지된 종목인지 나타낸다.
    // 주문 가능 여부와 직결되므로 Stock.tradingHalted를 갱신할 때도 핵심 기준이 된다.
    @Column(name = "trading_halt_yn", length = 1)
    private String tradingHaltYn;

    // 정리매매 여부다. 상장폐지 전 마지막 매매 기간에 들어간 종목인지 나타낸다.
    // 일반 투자 관점에서는 매우 높은 위험 상태로 보고 별도 경고나 필터링 대상이 된다.
    @Column(name = "liquidation_trade_yn", length = 1)
    private String liquidationTradeYn;

    // 관리종목 여부다. 상장유지 요건 미달 등으로 거래소가 관리 대상으로 지정한 종목인지 나타낸다.
    // 재무 악화, 감사의견, 공시 문제 등으로 상장폐지 리스크가 커진 상태일 수 있다.
    @Column(name = "managed_issue_yn", length = 1)
    private String managedIssueYn;

    // 시장경고 구분 코드다. 투자주의, 투자경고, 투자위험 등 과열/위험 단계의 원본 코드다.
    // 예: 00=해당없음, 01=투자주의, 02=투자경고, 03=투자위험. 단기 급등주 리스크 판단에 중요하다.
    @Column(name = "market_alert_class_code", length = 2)
    private String marketAlertClassCode;

    // 시장경고위험 예고 여부다. 투자위험 종목 지정 가능성이 예고된 상태인지 나타낸다.
    // 아직 투자위험 종목은 아니더라도 추가 지정 가능성이 있어 경고성 정보로 활용할 수 있다.
    @Column(name = "market_alert_risk_notice_yn", length = 1)
    private String marketAlertRiskNoticeYn;

    // 불성실 공시 여부다. 공시 의무 위반 등으로 불성실공시법인에 해당하는지 나타낸다.
    // 정보 신뢰성과 기업 거버넌스 리스크를 판단할 때 참고한다.
    @Column(name = "unfaithful_disclosure_yn", length = 1)
    private String unfaithfulDisclosureYn;

    // 우회상장 여부다. 합병 등을 통해 기존 상장 법인을 활용해 상장한 종목인지 나타낸다.
    // 일반 IPO와 다른 상장 이력을 가지므로 과거 재무/사업 연속성 분석 시 주의가 필요하다.
    @Column(name = "backdoor_listing_yn", length = 1)
    private String backdoorListingYn;

    // 락구분 코드다. 권리락, 배당락, 분배락 등 기준가격 조정 이벤트를 구분한다.
    // 예: 00=해당없음, 01=권리락, 02=배당락, 03=분배락, 04=권배락, 05=중간배당락.
    @Column(name = "lock_class_code", length = 2)
    private String lockClassCode;

    // 액면가 변경 구분 코드다. 액면분할, 액면병합 등 주식 액면가 변경 이벤트를 구분한다.
    // 예: 00=해당없음, 01=액면분할, 02=액면병합, 99=기타. 주가/주식수 비교 시 보정이 필요할 수 있다.
    @Column(name = "face_value_change_class_code", length = 2)
    private String faceValueChangeClassCode;

    // 증자 구분 코드다. 유상증자, 무상증자, 유무상증자 등 자본금 증가 이벤트를 구분한다.
    // 예: 00=해당없음, 01=유상증자, 02=무상증자, 03=유무상증자, 99=기타. 주식수 희석 분석에 중요하다.
    @Column(name = "capital_increase_class_code", length = 2)
    private String capitalIncreaseClassCode;

    // 증거금 비율 원본값이다. 주문 시 필요한 현금/담보 비율을 나타내며 미수·신용 가능성과 연관된다.
    // 위험도가 높은 종목일수록 증거금률이 높아질 수 있어 주문 가능 금액 계산에 영향을 준다.
    @Column(name = "margin_rate", length = 3)
    private String marginRate;

    // 신용주문 가능 여부다. 증권사 신용거래로 매수할 수 있는 종목인지 나타낸다.
    // 신용거래 불가 종목은 레버리지 매수나 일부 주문 전략에서 제외해야 한다.
    @Column(name = "credit_available_yn", length = 1)
    private String creditAvailableYn;

    // 신용기간 원본값이다. 신용거래가 가능한 경우 적용되는 대출/상환 가능 기간이다.
    // 신용매수 후 언제까지 상환해야 하는지와 관련된 원본 기간 정보다.
    @Column(name = "credit_days", length = 3)
    private String creditDays;

    // 전일 거래량 원본값이다. 직전 거래일에 체결된 주식 수량으로 유동성 판단에 참고한다.
    // 거래대금 순위, 유동성 필터, 급등락 종목 선별의 보조 데이터로 활용할 수 있다.
    @Column(name = "previous_day_volume", length = 12)
    private String previousDayVolume;

    // 주식 액면가 원본값이다. 회사 정관상 1주에 부여된 명목 금액이다.
    // 시가와 직접 같지는 않지만 자본금, 액면분할/병합 이벤트를 이해하는 데 필요하다.
    @Column(name = "stock_face_value", length = 12)
    private String stockFaceValue;

    // 주식 상장일자 원본값이다. 해당 종목이 거래소에 처음 상장된 날짜다.
    // 신규상장주 여부, 상장 경과 기간, 과거 데이터 시작점 판단에 사용할 수 있다.
    @Column(name = "stock_listed_date", length = 8)
    private String stockListedDate;

    // 상장 주수 원본값이다. 거래소에 상장되어 유통 가능한 주식 수량 정보다.
    // 시가총액 계산과 주식수 희석 여부 분석의 기본 데이터다.
    @Column(name = "listed_shares", length = 15)
    private String listedShares;

    // 자본금 원본값이다. 회사가 발행한 주식의 액면 총액 성격의 재무 정보다.
    // 자본잠식 여부나 증자/감자 이력을 공부할 때 함께 보는 기초 항목이다.
    @Column(name = "capital", length = 21)
    private String capital;

    // 결산월 원본값이다. 기업의 회계연도가 마감되는 월이다.
    // 실적 발표 시점과 재무제표 기준 기간을 이해할 때 필요하다.
    @Column(name = "settlement_month", length = 2)
    private String settlementMonth;

    // 공모가격 원본값이다. 신규 상장 또는 공모 시 투자자에게 배정된 기준 가격이다.
    // 상장 이후 주가가 공모가 대비 얼마나 상승/하락했는지 분석하는 기준이 된다.
    @Column(name = "public_offering_price", length = 7)
    private String publicOfferingPrice;

    // 우선주 구분 코드다. 보통주, 구형우선주, 신형우선주 등 주식 권리 구조를 구분한다.
    // 예: 0=보통주, 1=구형우선주, 2=신형우선주. 의결권/배당 조건 차이를 이해해야 한다.
    @Column(name = "preferred_stock_class_code", length = 1)
    private String preferredStockClassCode;

    // 공매도과열종목 여부다. 공매도 거래가 급증해 과열종목으로 지정되었는지 나타낸다.
    // 투자심리 악화나 가격 변동성 확대 가능성을 볼 때 참고할 수 있다.
    @Column(name = "short_sale_overheated_yn", length = 1)
    private String shortSaleOverheatedYn;

    // 이상급등종목 여부다. 비정상적인 가격 급등으로 별도 관리되는 종목인지 나타낸다.
    // 단기 급등 이후 변동성이 커질 수 있어 주문 전 경고 조건으로 쓰기 좋다.
    @Column(name = "abnormal_runup_yn", length = 1)
    private String abnormalRunupYn;

    // KRX300 종목 여부다. KRX300 지수 구성 종목인지 나타낸다.
    // 코스피/코스닥 우량 대표 종목군 편입 여부를 확인할 수 있다.
    @Column(name = "krx300_issue_yn", length = 1)
    private String krx300IssueYn;

    // KOSPI 종목 여부다. 유가증권시장 종목인지 나타내는 원본 플래그다.
    // marketType과 중복될 수 있지만 원본 마스터 검증과 데이터 정합성 확인에 사용할 수 있다.
    @Column(name = "kospi_issue_yn", length = 1)
    private String kospiIssueYn;

    // 매출액 원본값이다. 기업이 영업활동으로 벌어들인 총 수익 규모다.
    // 기업의 외형 성장과 시장 규모를 볼 때 가장 기본이 되는 손익계산서 항목이다.
    @Column(name = "sales", length = 9)
    private String sales;

    // 영업이익 원본값이다. 본업에서 발생한 이익으로 기업의 영업 경쟁력을 볼 때 사용한다.
    // 매출에서 원가와 판관비를 뺀 이익으로, 본업 수익성 판단에 중요하다.
    @Column(name = "operating_profit", length = 9)
    private String operatingProfit;

    // 경상이익 원본값이다. 영업외 손익까지 반영한 반복적 이익 수준을 나타낸다.
    // 현재 회계 기준에서는 자주 쓰이지 않지만 과거 재무 데이터와 비교할 때 참고할 수 있다.
    @Column(name = "ordinary_profit", length = 9)
    private String ordinaryProfit;

    // 당기순이익 원본값이다. 세금과 비용을 모두 반영한 최종 이익이다.
    // EPS, PER, ROE 같은 핵심 투자지표의 기반이 되는 순이익 항목이다.
    @Column(name = "net_income", length = 5)
    private String netIncome;

    // ROE 원본값이다. 자기자본 대비 순이익 비율로 자본 효율성을 볼 때 사용한다.
    // 같은 업종 안에서 자본을 얼마나 효율적으로 굴리는지 비교할 때 자주 사용한다.
    @Column(name = "roe", length = 9)
    private String roe;

    // 재무 기준년월 원본값이다. 매출액, 이익, ROE 등 재무 필드가 어느 시점 기준인지 나타낸다.
    // 오래된 기준월이면 최신 재무 상태와 차이가 있을 수 있어 화면 표시 시 함께 보여주는 것이 좋다.
    @Column(name = "base_date", length = 8)
    private String baseDate;

    // 전일 기준 시가총액 원본값이다. 전일 종가 기준 기업가치 규모를 나타낸다.
    // 대형주/중소형주 분류, 시장 내 비중, 거래대금 대비 규모 비교에 활용된다.
    @Column(name = "previous_day_market_cap", length = 9)
    private String previousDayMarketCap;

    // 그룹사 코드다. 같은 기업집단 또는 계열사 분류를 식별하는 원본 코드다.
    // 동일 그룹 내 종목 묶음이나 계열사 리스크 분석에 사용할 수 있다.
    @Column(name = "group_code", length = 3)
    private String groupCode;

    // 회사신용한도초과 여부다. 증권사 내부 신용한도 기준을 초과한 회사인지 나타낸다.
    // 신용거래, 담보대출 등 여신성 거래에서 제한이 걸릴 수 있는 위험 정보를 나타낸다.
    @Column(name = "company_credit_limit_over_yn", length = 1)
    private String companyCreditLimitOverYn;

    // 담보대출가능 여부다. 해당 종목을 담보로 대출이 가능한지 나타낸다.
    // 종목의 담보가치와 증권사 리스크 관리 정책에 따라 가능 여부가 달라질 수 있다.
    @Column(name = "securities_lending_available_yn", length = 1)
    private String securitiesLendingAvailableYn;

    // 대주가능 여부다. 주식을 빌려 매도하는 대주 거래가 가능한 종목인지 나타낸다.
    // 공매도 성격의 거래 가능 여부와 연결되며, 개인 대주 서비스 지원 종목 판단에 쓰일 수 있다.
    @Column(name = "stock_loan_available_yn", length = 1)
    private String stockLoanAvailableYn;

    // 국내주식 메타데이터가 마지막으로 종목 마스터 파일에서 반영된 시각이다.
    // 종목 마스터 파일이 매일 갱신될 수 있으므로 데이터 신선도 판단에 사용한다.
    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    // 메타데이터 row가 처음 저장된 시각이다. 최초 종목 마스터 적재 시점을 추적한다.
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 메타데이터 row가 마지막으로 수정된 시각이다. upsert 과정에서 값이 변경된 시점을 추적한다.
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static DomesticStockMetadata create(Stock stock, LocalDateTime lastSyncedAt) {
        validateRequired(stock, "종목 정보는 필수입니다.");
        validateRequired(lastSyncedAt, "동기화 시각은 필수입니다.");

        DomesticStockMetadata metadata = new DomesticStockMetadata();
        metadata.stock = stock;
        metadata.lastSyncedAt = lastSyncedAt;
        return metadata;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
