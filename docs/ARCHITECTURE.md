# FinMate 아키텍처

## 1. 패키지 구조

```text
com.finmate
├─ controller
│  ├─ home, login
│  ├─ normal.account
│  ├─ investment
│  ├─ stock
│  └─ order
├─ domain
│  ├─ user
│  ├─ normal.account, normal.transfer
│  ├─ investment, investment.cash.transaction, investment.cash.exchange
│  ├─ stock, stock.metadata, stock.price, stock.market
│  ├─ stock.trading, stock.trading.event
│  └─ market, market.price
├─ repository
│  ├─ user
│  ├─ normal.account, normal.transfer
│  ├─ investment, investment.cash.exchange
│  ├─ stock, stock.metadata, stock.price, stock.trading
│  └─ market.price
├─ service
│  ├─ user
│  ├─ normal.account, normal.transfer
│  ├─ investment
│  ├─ stock, stock.master, stock.price, stock.ranking
│  ├─ stock.realtime, stock.trading
│  └─ market
├─ infra.kis
│  ├─ core, rest, websocket, parser, exchange
│  └─ stock.master, stock.price, stock.ranking, stock.realtime
├─ global
│  ├─ interceptor, security, websocket
│  ├─ pagination, validation, format, constant
└─ exception
```

DTO가 별도의 최상위 계층이 아니라 각 도메인 하위에 배치되어 있고, MVC 화면 모델과 요청 객체를 함께 포함한다.

## 2. 계층별 책임

### Controller

Thymeleaf 화면과 폼 요청을 연결한다. `@Controller` 기반이며 JSON 전용 `@RestController` 계층은 확인되지 않는다.

- `AccountController`: 일반 계좌, 이체, 한도, 내역
- `InvestmentController`: 투자 계좌, 예수금 이체, 포트폴리오, 주문 내역, 환율·지수
- `StockController`: 시장별 종목/업종 검색, 관심 종목, 상세, 랭킹 데이터
- `OrderController`: 주문 화면, 일반·예약 주문 접수와 취소
- `LoginController`: 회원가입, 세션 로그인·로그아웃

### Service

유스케이스, 권한 검증, 트랜잭션 경계와 외부 연동 조율을 담당한다. 핵심 서비스는 다음과 같다.

- `AccountService`: 일반 계좌와 계좌이체
- `InvestmentService`: 투자 계좌와 일반↔투자 자금 이동
- `InvestmentCurrencyExchangeService`: 증권계좌 KRW/USD 예수금 환전과 환전 내역 조회
- `StockTradingCommandService`: 주문·예약 접수와 취소
- `StockTradingAssetService`: 예수금·보유수량 예약 및 해제
- `StockTradingExecutionService`: 실시간 가격 기반 체결과 정산
- `StockTradingQueryService`: 주문·체결·포트폴리오 조회, 포트폴리오 업종 비중 계산
- `StockPortfolioValuationPriceService`: 포트폴리오 초기 평가용 최신 일봉 종가 조회·보충
- `StockRealtimeSubscriptionManager`: 목적별 실시간 구독 참조 수 관리
- `StockMarketMoverService`: KIS 랭킹 조회와 Redis 캐시 갱신
- `StockMasterSyncService`: 종목 마스터와 국내 업종코드 스케줄 동기화

### Repository

Spring Data JPA 인터페이스다. 조회, 집계, fetch join 및 `PESSIMISTIC_WRITE` 조회를 제공한다. Repository 구현 클래스나 QueryDSL은 없다.

### Domain

JPA 엔티티, enum, 정책과 DTO를 포함한다. 잔액 변경, 자산 잠금, 주문 상태 전이는 엔티티 메서드에 들어 있다. 서비스는 여러 엔티티와 저장소를 묶어 유스케이스를 완성한다.

### Infra

현재 별도 인프라 패키지는 KIS 연동에 집중되어 있다.

- 공통 설정, REST 인증·호출 제한·재시도
- 종목 마스터 파일과 국내 업종코드 파일 다운로드
- 일봉·랭킹 REST API client
- KIS WebSocket approval key, 연결·재연결, 구독 메시지와 payload 파싱

Redis 접근 코드는 `service.stock.ranking.StockRankingCacheService`에 있어 엄격한 계층 분리는 아니다.

## 3. 핵심 클래스 관계

```text
User
 ├─ Account ── AccountTransaction
 │     └─ Transfer ── 상대 Account 또는 Investment
 └─ Investment ── InvestmentCashBalance
        ├─ SecuritiesCashTransaction
        ├─ InvestmentCurrencyExchangeTransaction
        ├─ StockOrderReservation ──(조건 충족)── StockOrder
        ├─ StockOrder ── StockTradeTransaction
        └─ StockHolding

Stock는 Order / Reservation / Holding / TradeTransaction의 공통 종목 참조
```

`User`는 코드상 `Account`만 양방향 목록을 보유한다. `Investment`와의 관계는 `Investment.user` 단방향 참조로 표현된다.

## 4. 일반 계좌와 투자 계좌

| 항목 | 일반 계좌 `Account` | 투자 계좌 `Investment` |
|---|---|---|
| 소유자 | `User` 다대일 | `User` 다대일 |
| 계좌번호 | 전역 레지스트리로 발급, unique | 같은 레지스트리로 발급, unique |
| 잔액 | 계좌 통화 1개와 `balance` 1개 | 통화별 `InvestmentCashBalance` 목록 |
| 한도 | 1회·일일 이체 한도 | 별도 이체 한도 없음 |
| 대표 계좌 | `primary` | `primary` |
| 거래 기록 | `AccountTransaction` | `SecuritiesCashTransaction`, 주식 체결은 `StockTradeTransaction` |

## 5. 외부 API, Redis, WebSocket

### KIS REST

도메인별 client가 경로·TR ID·파라미터를 정하고 `KisRestClient`가 공통 인증 헤더, 호출 제한과 응답 검증을 처리한다. 액세스 토큰은 `KisTokenService`의 JVM 메모리에 저장된다.

### Redis

랭킹 캐시는 `StockRankingCacheService`가 관리한다. 키는 `stock:ranking:{market}:{type}`이고 값은 `StockRankingBoard` JSON, TTL은 장중 기본 30초·장외 기본 86,400초다.

환율·해외 지수 실시간 캐시는 `MarketRealtimeCacheService`가 관리한다. 키는 `market:realtime:{indicator}`이고 값은 `MarketRealtimeMessage` JSON, TTL은 기본 120초다. 세션, 토큰, WebSocket Pub/Sub 용도는 **현재 구현되지 않음**.

### WebSocket

두 연결이 분리되어 있다.

1. KIS ↔ 서버: `KisRealtimeWebSocketClient`가 JDK WebSocket으로 연결한다.
2. 브라우저 ↔ 서버: `/ws/stocks`에 `StockRealtimeWebSocketHandler`, `/ws/market-data`에 `MarketRealtimeWebSocketHandler`가 연결된다.

KIS payload는 `KisRealtimeStore`에 최신값으로 저장되고 Spring 동기 이벤트로 발행된다. `StockRealtimeClientSessionService`와 `MarketRealtimeClientSessionService`는 구독 브라우저에 JSON을 보내며, `StockTradingRealtimeExecutionListener`는 같은 이벤트로 체결을 시도한다.

## 6. 트랜잭션 경계와 락

이 절은 현재 소스에서 확인한 트랜잭션과 락 획득 순서를 설명한다. 변경 시 지켜야 할 정합성 계약과 동시성 증거 기준은 [금융 불변식](FINANCIAL_INVARIANTS.md)을 우선한다.

### 일반 계좌이체

`AccountService.transfer()` 전체가 하나의 트랜잭션이다.

1. 계좌번호로 두 ID 조회
2. 작은 ID, 큰 ID 순서로 `findByIdForUpdate()` 호출
3. 소유권·통화·한도 검증
4. 출금과 입금
5. `Transfer`와 양쪽 `AccountTransaction` 저장

두 잔액과 세 기록 중 하나라도 실패하면 같은 트랜잭션에서 롤백된다.

### 일반↔투자 자금 이동

`InvestmentService.depositToInvestment()`와 `withdrawFromInvestment()`가 각각 하나의 트랜잭션이다. 방향과 무관하게 일반 `Account`를 먼저, `Investment`를 다음에 잠근다. 이어 통화별 `InvestmentCashBalance`도 `PESSIMISTIC_WRITE`로 조회한다.

### 증권계좌 안 환전

`InvestmentCurrencyExchangeService.exchangeCurrency()` 전체가 하나의 트랜잭션이다.
해당 `Investment`를 먼저 잠근 뒤, 환전 방향과 관계없이 `InvestmentCashBalance`를 항상 `KRW` → `USD` 순서로 `PESSIMISTIC_WRITE` 조회한다.
이후 환전 전 통화의 사용 가능 예수금을 출금하고 환전 후 통화의 사용 가능 예수금에 입금한 다음 `InvestmentCurrencyExchangeTransaction`을 저장한다.
적용 환율은 기존 `USD_KRW` 최신 시세를 사용하며, KRW→USD와 USD→KRW 모두 환전 후 통화 최소 단위 이하 절사는 사용자에게 유리한 초과 입금을 만들지 않기 위해 `DOWN` 반올림 정책을 사용한다.

### 주식 주문

- 접수: `StockTradingCommandService.submitOrder()`/`submitReservation()`
- 실시간 처리: `StockTradingExecutionService.processRealtimeUpdate()`
- 활성 주문·예약 목록과 예수금·보유 수량은 비관적 쓰기 락 조회를 사용한다.
- 구독 활성·종료 이벤트는 DB 커밋 뒤 `@TransactionalEventListener(AFTER_COMMIT)`에서 처리된다.
- KIS payload 이벤트 리스너는 비동기 설정이 없으므로 발행 스레드에서 동기 실행된다.

### 락 순서의 확인된 범위

- 일반 계좌 두 개: 계좌 ID 오름차순
- 일반 계좌와 투자 계좌: 항상 일반 계좌 → 투자 계좌
- 증권계좌 안 환전: 증권 계좌 → KRW 예수금 → USD 예수금
- 즉시 동기 주문 접수: `Investment`를 먼저 잠근다. 매도 예약에서 `StockHolding`을 잠근 뒤 같은 트랜잭션의 즉시 체결이 `InvestmentCashBalance`를 요청하므로, 즉시 매도 체결의 실효 순서는 `Investment → Holding → Cash`다.
- 실시간 체결: 예약 행 목록 → 주문 행 목록 순으로 잠그고, 각 체결에서 `Cash → Holding` 순으로 락을 요청한다.

주문 취소 메서드는 주문/예약 자체를 `findById()`로 읽고 별도 비관적 락을 잡지 않는다. 실시간 체결과 취소가 동시에 실행될 때의 완전한 락 순서·경합 안전성은 테스트가 없어 **확인 필요**.

즉시 매도 체결의 `Holding → Cash`와 실시간 체결의 `Cash → Holding`은 현재 확인된 락 순서 역전이다. 두 경로가 같은 자산 행에서 교차하면 대기 사이클과 데드락이 가능하며, 이를 제거하거나 안전성을 입증한 구현·동시성 테스트는 현재 없다. 따라서 현 상태를 안전한 전역 락 순서로 간주하지 않는다.

## 7. 스케줄러와 이벤트

- `@EnableScheduling`: `FinmateApplication`
- 종목 마스터: 국내·NASDAQ 평일 오전 8시, 각 시장 시간대. 국내 업종코드는 국내 종목 마스터와 같은 스케줄에서 함께 갱신한다.
- 랭킹: 기본 100ms 후 시작, 이전 실행 완료 후 10초 간격
- 구독 해제: 별도 단일 스레드 executor로 기본 60초 유예
- KIS 재연결: 별도 단일 스레드 executor로 3초 후 재시도
- 애플리케이션 시작: 활성 주문·예약의 구독을 `ApplicationRunner`가 복구
- DB 상태 연동 이벤트: 주문/예약 구독은 커밋 후 처리
- 실시간 payload 이벤트: 브라우저 전파와 거래 체결 처리
