package com.finmate.domain.stock;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.finmate.global.validation.RequiredValidator.validateRequired;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "stock",
        // market + symbol을 묶어서 unique 제약조건을 추가
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stock_market_symbol",
                        columnNames = {"market_type", "symbol"}
                ),
                @UniqueConstraint(
                        name = "uk_stock_standard_code",
                        columnNames = "standard_code"
                )
        }
)
@Entity
public class Stock {
    // Stock 테이블의 PK다. 종목 식별에는 사용하지 않고 내부 연관관계 연결용 대리키로 사용한다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 종목을 식별하는 거래소 단축코드다. 국내는 005930, 해외는 AAPL 같은 심볼을 저장한다.
    @Column(nullable = false, length = 20, updatable = false)
    private String symbol;

    // 실시간 시세 구독에 사용하는 심볼이다. 해외주식은 일반 심볼과 다를 수 있다.
    @Column(name = "realtime_symbol", length = 30)
    private String realtimeSymbol;

    // 국내 표준 종목코드다. 국내는 KR7005930003 같은 표준코드를 저장하고, 해외는 없으면 null로 둔다.
    @Column(name = "standard_code", length = 30)
    private String standardCode;

    // 화면 표시와 검색에 사용할 한글 종목명이다.
    @Column(name = "name_ko", nullable = false, length = 120)
    private String nameKo;

    // 해외주식이나 영문 표기가 필요한 종목의 영문 종목명이다.
    @Column(name = "name_en", length = 120)
    private String nameEn;

    // 종목이 속한 시장 구분이다. KOSPI, KOSDAQ, NASDAQ 등을 저장한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", nullable = false, length = 20, updatable = false)
    private StockMarketType marketType;

    // 종목이 상장된 국가 코드다. KR, US 같은 ISO 국가 코드를 저장한다.
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    // 종목이 거래되는 거래소 코드다. 국내는 KRX, 해외는 NAS 같은 코드를 저장한다.
    @Column(name = "exchange_code", nullable = false, length = 10)
    private String exchangeCode;

    // 종목 거래 통화다. 국내는 KRW, 미국은 USD를 저장한다.(원화, 달러 등등 추후에 확장)
    @Column(nullable = false, length = 3)
    private String currency;

    // 주권, 우선주, ETF, ETN, REIT 등 종목의 상품 유형이다.
    @Enumerated(EnumType.STRING)
    @Column(name = "security_type", nullable = false, length = 30)
    private StockSecurityType securityType;

    // 최신 종목 마스터에 존재하는 활성 종목인지 여부다.(상장폐지된 종목인지 등등을 확인하기 위함)
    @Column(nullable = false)
    private boolean active;

    // 현재 주문 가능한 종목인지 여부다. 상장 상태, 거래정지, 증권사 주문 제한 등을 종합한 거래 가능 플래그다.
    // tradable은 모든 정보를 종합해서, 최종적으로 현재 우리 서비스에서 주식 거래가가능한지 여부를 결정한다.
    @Column(nullable = false)
    private boolean tradable;

    // 거래정지 여부다. 종목 마스터 또는 시세 상태를 기준으로 갱신한다.
    @Column(name = "trading_halted", nullable = false)
    private boolean tradingHalted;

    // 상장일자다. 마스터 파일에 값이 없거나 해외 데이터에서 제공하지 않으면 null일 수 있다.
    @Column(name = "listed_date")
    private LocalDate listedDate;

    // 종목 마스터 데이터가 마지막으로 동기화된 시각이다.
    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;

    // Stock 엔티티가 처음 저장된 시각이다.
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Stock 엔티티가 마지막으로 수정된 시각이다.
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Stock create(String symbol,
                               String realtimeSymbol,
                               String standardCode,
                               String nameKo,
                               String nameEn,
                               StockMarketType marketType,
                               String countryCode,
                               String exchangeCode,
                               String currency,
                               StockSecurityType securityType,
                               boolean tradingHalted,
                               LocalDate listedDate,
                               LocalDateTime lastSyncedAt) {
        validateRequired(symbol, "종목코드는 필수입니다.");
        validateRequired(nameKo, "한글 종목명은 필수입니다.");
        validateRequired(countryCode, "국가코드는 필수입니다.");
        validateRequired(exchangeCode, "거래소코드는 필수입니다.");
        validateRequired(currency, "통화코드는 필수입니다.");
        validateRequired(marketType, "시장 구분은 필수입니다.");
        validateRequired(securityType, "증권 유형은 필수입니다.");
        validateRequired(lastSyncedAt, "동기화 시각은 필수입니다.");

        Stock stock = new Stock();
        stock.symbol = symbol;
        stock.realtimeSymbol = realtimeSymbol;
        stock.standardCode = standardCode;
        stock.nameKo = nameKo;
        stock.nameEn = nameEn;
        stock.marketType = marketType;
        stock.countryCode = countryCode;
        stock.exchangeCode = exchangeCode;
        stock.currency = currency;
        stock.securityType = securityType;
        stock.active = true;
        stock.tradable = !tradingHalted;
        stock.tradingHalted = tradingHalted;
        stock.listedDate = listedDate;
        stock.lastSyncedAt = lastSyncedAt;
        return stock;
    }

    public void updateMasterInfo(String realtimeSymbol,
                                 String standardCode,
                                 String nameKo,
                                 String nameEn,
                                 String countryCode,
                                 String exchangeCode,
                                 String currency,
                                 StockSecurityType securityType,
                                 boolean tradingHalted,
                                 LocalDate listedDate,
                                 LocalDateTime lastSyncedAt) {
        validateRequired(nameKo, "한글 종목명은 필수입니다.");
        validateRequired(countryCode, "국가코드는 필수입니다.");
        validateRequired(exchangeCode, "거래소코드는 필수입니다.");
        validateRequired(currency, "통화코드는 필수입니다.");
        validateRequired(securityType, "증권 유형은 필수입니다.");
        validateRequired(lastSyncedAt, "동기화 시각은 필수입니다.");

        this.realtimeSymbol = realtimeSymbol;
        this.standardCode = standardCode;
        this.nameKo = nameKo;
        this.nameEn = nameEn;
        this.countryCode = countryCode;
        this.exchangeCode = exchangeCode;
        this.currency = currency;
        this.securityType = securityType;
        this.active = true;
        this.tradable = !tradingHalted;
        this.tradingHalted = tradingHalted;
        this.listedDate = listedDate;
        this.lastSyncedAt = lastSyncedAt;
    }

    public void markInactive(LocalDateTime lastSyncedAt) {
        validateRequired(lastSyncedAt, "동기화 시각은 필수입니다.");

        this.active = false;
        this.tradable = false;
        this.lastSyncedAt = lastSyncedAt;
    }

    public void updateTradingStatus(boolean tradable, boolean tradingHalted, LocalDateTime lastSyncedAt) {
        validateRequired(lastSyncedAt, "동기화 시각은 필수입니다.");

        this.tradable = tradable;
        this.tradingHalted = tradingHalted;
        this.lastSyncedAt = lastSyncedAt;
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
