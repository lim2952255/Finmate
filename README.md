# FinMate

FinMate는 일반 은행 계좌와 모의 투자 계좌를 함께 관리하는 Spring Boot 기반 금융 포트폴리오 애플리케이션입니다.

브라우저 화면은 Thymeleaf로 서버 렌더링하고, 한국투자증권(KIS) Open API는 종목 마스터·시세·랭킹·실시간 WebSocket 데이터 수집에 사용합니다. 실제 증권사 주문 전송은 하지 않으며, 주식 주문·체결·정산은 FinMate DB 안에서 처리되는 모의 거래입니다.

## 현재 구현 범위

- 회원: 회원가입, 로그인, 로그아웃, HTTP Session 기반 사용자 상태 유지
- 일반 계좌: 계좌 개설, 대표 계좌 설정, 다중 통화 잔액, 계좌이체, 이체 한도, 거래 내역
- 투자 계좌: 증권계좌 개설, 대표 계좌 설정, 통화별 예수금, 일반 계좌와 투자 계좌 간 자금 이동
- 환전: 증권계좌 안 KRW/USD 환전, 최신 USD/KRW 시세 기반 계산, 환전 내역 조회
- 종목: 국내·NASDAQ 종목 마스터 동기화, 종목 검색, 관심 종목, 상세 차트, 마스터파일 기반 기업 정보 표시
- 시장 데이터: KOSPI, KOSDAQ, USD/KRW, NASDAQ 지표 차트와 실시간 화면값
- 모의 투자: 시장가·지정가·예약 주문, 주문 취소, 실시간 가격 기반 체결, 보유 종목·주문·체결 내역
- 실시간: 브라우저 WebSocket, KIS WebSocket 구독, 실시간 체결가·호가·국내 지수 전파
- 랭킹: 국내·해외 거래량/거래대금 TOP 10 조회와 Redis TTL 캐시

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5.15 |
| Web | Spring MVC, Thymeleaf, Bean Validation |
| Auth | Spring Security 의존성, BCrypt, 자체 MVC Interceptor, HTTP Session |
| Persistence | Spring Data JPA, Hibernate, MySQL Connector/J |
| Database | MySQL 8.4 |
| Cache | Redis 7.2, `StringRedisTemplate` |
| Realtime | Spring WebSocket, JDK `HttpClient` WebSocket |
| External API | 한국투자증권(KIS) Open API |
| Build/Test | Gradle Wrapper, JUnit 5, Spring Boot Test |
| Utility | Lombok, Docker Compose |

## 애플리케이션 구조

```text
src/main/java/com/finmate
├─ controller
│  ├─ home
│  ├─ login
│  ├─ normal.account
│  ├─ investment
│  ├─ stock
│  └─ order
├─ domain
│  ├─ user
│  ├─ normal.account
│  ├─ normal.transfer
│  ├─ investment
│  ├─ investment.cash
│  ├─ stock
│  ├─ stock.metadata
│  ├─ stock.price
│  ├─ stock.trading
│  └─ market
├─ repository
├─ service
├─ infra.kis
├─ global
└─ exception
```

```text
Browser
  ├─ HTTP -> Controller -> Service -> Repository -> MySQL
  ├─ /ws/stocks -> StockRealtimeWebSocketHandler
  └─ /ws/market-data -> MarketRealtimeWebSocketHandler

KIS REST API
  -> KisRestClient
  -> 일봉/지수/환율/랭킹/마스터파일 조회
  -> MySQL 또는 Redis TTL 캐시

KIS WebSocket
  -> KisRealtimeWebSocketClient
  -> KisRealtimeStore
  -> Spring Event
  ├─ 브라우저 실시간 전파
  └─ 활성 주문·예약 주문 체결 판단
```

## 핵심 도메인

### 일반 계좌

- `Account`: 사용자 소유 은행 계좌, 계좌번호, 통화, 잔액, 대표 계좌 여부를 관리합니다.
- `AccountTransaction`: 입금·출금·이체·투자 계좌 입출금 등 일반 계좌 기준 거래 내역입니다.
- `Transfer`: 일반 계좌 간 이체 원장입니다.
- `DailyTransferUsage`: 일일 이체 한도 사용량입니다.

계좌이체는 하나의 트랜잭션에서 출금 계좌와 입금 계좌를 함께 잠그고, 계좌 ID 오름차순으로 비관적 락을 획득해 데드락 가능성을 낮춥니다.

### 투자 계좌와 예수금

- `Investment`: 사용자 소유 증권계좌입니다.
- `InvestmentCashBalance`: 증권계좌별·통화별 예수금과 주문 잠금 예수금을 관리합니다.
- `SecuritiesCashTransaction`: 일반 계좌와 증권계좌 사이의 입출금 내역입니다.
- `InvestmentCurrencyExchangeTransaction`: 증권계좌 안 KRW/USD 환전 내역입니다.

환전은 `Investment`를 먼저 잠근 뒤, 환전 방향과 관계없이 항상 KRW 예수금 → USD 예수금 순서로 비관적 락을 획득합니다.

### 종목과 마스터파일

- `Stock`: 종목의 공통 기준 정보입니다.
- `DomesticStockMetadata`: 국내 종목 마스터파일의 업종, ETF/ETN/우선주/관리종목/거래정지, 액면가, 상장주식수, 기초 재무 등 상세 메타데이터입니다.
- `OverseasStockMetadata`: 해외 종목 마스터파일의 거래소, 산업 코드, 상품 유형, 기준가, 매매 수량 단위 등 상세 메타데이터입니다.
- `StockDailyPrice`: 종목별 일봉 차트 데이터입니다.
- `FavoriteStock`: 사용자 관심 종목입니다.

종목 상세 화면은 상단에 차트를 먼저 보여주고, 하단에 종목 기본 정보와 마스터파일 기반 기업 정보를 표시합니다. 국내 종목은 KRX 섹터 플래그가 있으면 자동차·반도체·바이오 같은 업종명을 함께 표시하고, 국내 기초 재무는 시가총액·손익 항목을 억 원 단위로, 자본금·가격 항목을 원 단위로, 상장주식수는 주 단위로 환산해 표시합니다.

### 모의 주식 거래

- `StockOrder`: 시장가·지정가 주문입니다.
- `StockOrderReservation`: 조건 충족 시 일반 주문으로 전환되는 예약 주문입니다.
- `StockTradeTransaction`: 체결 내역입니다.
- `StockHolding`: 보유 수량, 잠금 수량, 평균 매수가입니다.

주문 접수와 실시간 체결은 시장별 거래 가능 시간에만 허용됩니다.

| 시장 | 거래 가능 시간 |
|---|---|
| KOSPI/KOSDAQ | 대한민국 09:00~15:30, 15:40~18:00 |
| NASDAQ | America/New_York 09:30~16:00, 16:00~20:00 |

NASDAQ의 대한민국 시간은 미국 서머타임 여부에 따라 달라지며, 화면에는 현지 기준과 대한민국 기준 시간이 함께 표시됩니다. 현재 거래시간 정책은 주말만 제외하고, 공휴일·조기폐장은 반영하지 않습니다.

## 주요 화면 경로

| 기능 | 경로 |
|---|---|
| 홈 | `/`, `/home` |
| 회원가입/로그인 | `/signup`, `/login` |
| 일반 계좌 홈 | `/accounts` |
| 일반 계좌 개설/목록 | `/accounts/open`, `/accounts/list` |
| 계좌이체/한도/내역 | `/accounts/transfer`, `/accounts/transfer-limit`, `/accounts/transactions` |
| 투자 홈 | `/investments` |
| 투자 계좌 개설/목록 | `/investments/open`, `/investments/list` |
| 증권계좌 입출금/내역 | `/investments/transfer`, `/investments/securityCashTransaction` |
| 환전/환전 내역 | `/investments/currency-exchange`, `/investments/currency-exchange/transactions` |
| 포트폴리오/주문 내역 | `/investments/portfolio`, `/investments/orders` |
| 시장 지표 | `/investments/market-data`, `/investments/exchanges`, `/investments/indices` |
| 종목 검색/관심 종목 | `/investments/stocks/search`, `/investments/stocks/watchlist` |
| 종목 상세/랭킹 | `/investments/stocks/detail`, `/investments/stocks/market-movers` |
| 주문 화면 | `/investments/stocks/order/{stockId}` |

## KIS 연동

### REST

현재 코드에서 사용하는 KIS REST 범위입니다.

| 용도 | 경로 | TR ID |
|---|---|---|
| 국내 종목 일봉 | `/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice` | `FHKST03010100` |
| 해외 종목 일봉 | `/uapi/overseas-price/v1/quotations/dailyprice` | `HHDFS76240000` |
| 환율·해외 지수 일봉 | `/uapi/overseas-price/v1/quotations/inquire-daily-chartprice` | `FHKST03030100` |
| 환율·해외 지수 분봉 | `/uapi/overseas-price/v1/quotations/inquire-time-indexchartprice` | `FHKST03030200` |
| 국내 지수 일봉 | `/uapi/domestic-stock/v1/quotations/inquire-daily-indexchartprice` | `FHKUP03500100` |
| 국내 거래량/거래대금 랭킹 | `/uapi/domestic-stock/v1/quotations/volume-rank` | `FHPST01710000` |
| 해외 거래량 랭킹 | `/uapi/overseas-stock/v1/ranking/trade-vol` | `HHDFS76310010` |
| 해외 거래대금 랭킹 | `/uapi/overseas-stock/v1/ranking/trade-pbmn` | `HHDFS76320010` |

종목 랭킹은 각 시장의 정규장과 시간외 거래 가능 시간 동안 갱신하며, 거래 가능 시간이 아니면 Redis에 남아 있는 마지막 랭킹을 표시합니다.

REST access token은 JVM 메모리에 캐시합니다. Redis 토큰 공유, 다중 인스턴스 토큰 동기화, 인증 실패 시 자동 토큰 clear 후 재발급은 현재 구현하지 않았습니다.

### WebSocket

- 서버는 KIS WebSocket에 지연 연결하고, 필요한 종목·지수만 구독합니다.
- 브라우저는 `/ws/stocks`, `/ws/market-data` raw WebSocket endpoint를 사용합니다.
- 종목 실시간 데이터는 상세 화면 전파와 모의 주문 체결 판단에 함께 사용됩니다.
- 구독 참조 수가 0이 되면 즉시 해제하지 않고 기본 60초 유예 후 해제합니다.

## Redis 사용

| 용도 | 키 | TTL |
|---|---|---|
| 종목 랭킹 | `stock:ranking:{market}:{type}` | 장중 기본 30초, 장외 기본 86,400초 |
| 환율·해외 지수 실시간 화면값 | `market:realtime:{indicator}` | 기본 120초 |

KIS WebSocket 최신 payload, 브라우저 세션, 구독 참조 수는 Redis가 아니라 단일 JVM 메모리에 있습니다.

## 현재 한계

- 실제 은행·증권사 거래는 수행하지 않습니다.
- 주식 주문은 모의 거래이며, KIS에는 주문을 전송하지 않습니다.
- DB 스키마는 `spring.jpa.hibernate.ddl-auto=update`로 반영하며 Flyway/Liquibase는 없습니다.
- 부분 체결 상태 enum은 있으나 실제 체결 로직은 남은 수량 전체 체결 기준입니다.
- 공휴일, 조기폐장, 종목별 특수 거래 제한 캘린더는 반영하지 않았습니다.
- KIS token, WebSocket 연결 상태, 실시간 최신 payload는 단일 JVM 메모리 기준입니다.
- 다중 서버 fan-out, leader election, 중복 체결 방지 구조는 없습니다.
- JWT, Refresh Token, FDS, 뉴스 수집, AI 리포트, React 프론트엔드, Spring Batch, QueryDSL, CI/CD, AWS 배포는 현재 소스 기준 구현 범위가 아닙니다.
