package com.finmate.domain.stock.metadata;

import com.finmate.domain.stock.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

// 해외 종목 마스터 파일의 상세 메타데이터를 저장한다.
// Stock에는 종목 검색과 거래에 공통으로 필요한 핵심 정보만 두고, 이 엔티티에는 해외 거래소/호가/시장시간/상품 관련 원본 부가정보를 보관한다.
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "overseas_stock_metadata",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_overseas_stock_metadata_stock", columnNames = "stock_id")
        }
)
@Entity
public class OverseasStockMetadata {
    // 해외주식 메타데이터 테이블의 PK다. 실제 종목과의 1:1 연결은 stock 필드로 관리한다.
    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 메타데이터가 속한 공통 종목 정보다. 종목명, 시장, 심볼 같은 핵심 식별 정보는 Stock에서 관리한다.
    @Setter(AccessLevel.NONE)
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, unique = true)
    private Stock stock;

    // 종목이 상장된 국가 코드 원본값이다. 해외 마스터 파일의 국가 구분을 그대로 저장한다.
    // Stock.countryCode에도 반영되지만, 원본 데이터 추적과 정합성 확인을 위해 별도로 보관한다.
    @Column(name = "national_code", length = 2)
    private String nationalCode;

    // 거래소 ID 원본값이다. KIS 해외 마스터 파일에서 거래소를 식별하기 위해 제공하는 내부 ID 성격의 코드다.
    // 같은 거래소 코드라도 공급자 시스템에서 사용하는 식별값을 추적할 때 사용할 수 있다.
    @Column(name = "exchange_id", length = 3)
    private String exchangeId;

    // 거래소 코드 원본값이다. NAS, NYS처럼 종목이 실제 거래되는 해외 거래소 구분을 저장한다.
    // Stock.exchangeCode에도 반영되지만, 원본 마스터 값 그대로 보관한다.
    @Column(name = "exchange_code", length = 3)
    private String exchangeCode;

    // 거래소명 원본값이다. NASDAQ, New York Stock Exchange 같은 거래소 표시명을 저장한다.
    // 화면 표시나 운영자가 원본 파일을 확인할 때 코드보다 읽기 쉬운 보조 정보다.
    @Column(name = "exchange_name", length = 16)
    private String exchangeName;

    // 증권 유형 코드 원본값이다. 주식, ETF, ETN, DR 등 해외 상품 유형을 구분하는 공급자 원본 코드다.
    // Stock.securityType으로 변환되기 전의 값을 보관해 변환 로직 검증과 신규 코드 대응에 사용한다.
    @Column(name = "security_type_code", length = 1)
    private String securityTypeCode;

    // 거래 통화 코드 원본값이다. USD처럼 주문, 평가금액, 가격 표시에 사용할 통화를 저장한다.
    // Stock.currency에도 반영되지만, 해외 마스터 파일의 원본 통화 값을 추적한다.
    @Column(name = "currency", length = 4)
    private String currency;

    // 가격 소수점 자리 구분 원본값이다. 해당 종목 가격을 몇 자리 소수까지 해석해야 하는지 나타낸다.
    // 해외 시장은 종목/거래소별 가격 단위가 다를 수 있어 시세 표시와 주문 금액 계산에 참고한다.
    @Column(name = "float_position", length = 1)
    private String floatPosition;

    // 데이터 유형 원본값이다. 해외 마스터 파일에서 제공하는 행의 데이터 성격을 구분하는 코드다.
    // 보통주/ETF 같은 상품 유형과 별개로 공급자 데이터 처리 기준을 추적하는 보조 필드다.
    @Column(name = "data_type", length = 1)
    private String dataType;

    // 기준가 원본값이다. 장 시작 전 가격 비교나 등락률 계산의 기준이 되는 가격 정보다.
    // 해외 원장은 통화와 소수점 자리 해석이 필요하므로 문자열 원본값으로 보관한다.
    @Column(name = "base_price", length = 12)
    private String basePrice;

    // 매수 주문 수량 단위 원본값이다. 매수 주문 시 허용되는 최소 주문 수량 또는 주문 단위다.
    // 거래소/상품별 주문 제약이 다를 수 있어 주문 검증 로직의 기준 데이터가 된다.
    @Column(name = "bid_order_size", length = 8)
    private String bidOrderSize;

    // 매도 주문 수량 단위 원본값이다. 매도 주문 시 허용되는 최소 주문 수량 또는 주문 단위다.
    // 매수 주문 단위와 다르게 제공될 가능성까지 고려해 별도 필드로 보관한다.
    @Column(name = "ask_order_size", length = 8)
    private String askOrderSize;

    // 시장 시작 시각 원본값이다. 해당 거래소의 정규장 시작 시간을 HHmm 형태의 문자열로 저장한다.
    // 해외 시장 시간 안내, 주문 가능 시간 판단, 장 운영 상태 표시의 기준이 된다.
    @Column(name = "market_start_time", length = 4)
    private String marketStartTime;

    // 시장 종료 시각 원본값이다. 해당 거래소의 정규장 종료 시간을 HHmm 형태의 문자열로 저장한다.
    // 시장 시작 시각과 함께 해외장 개장 여부를 판단할 때 사용할 수 있다.
    @Column(name = "market_end_time", length = 4)
    private String marketEndTime;

    // DR 여부 원본값이다. 해당 종목이 예탁증서 형태의 상품인지 나타내는 Y/N 값이다.
    // DR은 원주가 다른 국가에 상장되어 있어 일반 현지 보통주와 권리 구조나 위험 요인이 다를 수 있다.
    @Column(name = "dr_yn", length = 1)
    private String drYn;

    // DR 국가 코드 원본값이다. DR 상품인 경우 원주 또는 관련 국가를 식별하는 코드다.
    // DR이 아닌 종목에서는 값이 없을 수 있으며, DR 리스크 분석과 표시용 보조 정보로 사용한다.
    @Column(name = "dr_country_code", length = 2)
    private String drCountryCode;

    // 업종분류코드 원본값이다. 해외 종목이 속한 산업군을 공급자 기준 코드로 저장한다.
    // 해외 종목 검색 필터, 동종업계 비교, 섹터별 분류의 기초 데이터가 된다.
    @Column(name = "industry_code", length = 4)
    private String industryCode;

    // 지수 구성종목 존재 여부 원본값이다. 해당 종목이 주요 지수 구성 종목으로 분류되는지 나타낸다.
    // 대표 지수 편입 종목 필터링이나 지수 관련 투자 정보 표시에 활용할 수 있다.
    @Column(name = "index_constituent_yn", length = 1)
    private String indexConstituentYn;

    // 호가 단위 유형 원본값이다. 가격 구간별 tick size를 어떤 규칙으로 적용할지 구분하는 코드다.
    // 해외 주문 가격 검증과 호가 단위 반올림 처리에 필요한 원본 기준이다.
    @Column(name = "tick_size_type", length = 1)
    private String tickSizeType;

    // ETP 유형 원본값이다. ETF, ETN 등 상장지수상품의 세부 유형을 나타내는 원본 코드다.
    // Stock.securityType 변환과 ETP 전용 위험 안내, 필터링에 참고한다.
    @Column(name = "etp_type", length = 3)
    private String etpType;

    // 호가 단위 유형 상세 원본값이다. tickSizeType보다 세부적인 호가 규칙 구분을 저장한다.
    // 거래소별 세부 호가 테이블을 적용하거나 주문 가격 단위를 검증할 때 사용할 수 있다.
    @Column(name = "tick_size_type_detail", length = 3)
    private String tickSizeTypeDetail;

    // 해외주식 메타데이터가 마지막으로 종목 마스터 파일에서 반영된 시각이다.
    // 종목 마스터 파일이 갱신될 수 있으므로 데이터 신선도 판단에 사용한다.
    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    // 메타데이터 row가 처음 저장된 시각이다. 최초 해외 종목 마스터 적재 시점을 추적한다.
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 메타데이터 row가 마지막으로 수정된 시각이다. upsert 과정에서 값이 변경된 시점을 추적한다.
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static OverseasStockMetadata create(Stock stock, LocalDateTime lastSyncedAt) {
        validateRequired(stock, "종목 정보는 필수입니다.");
        validateRequired(lastSyncedAt, "동기화 시각은 필수입니다.");

        OverseasStockMetadata metadata = new OverseasStockMetadata();
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
