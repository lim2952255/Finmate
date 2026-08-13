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
│  ├─ stock, stock.concept, stock.metadata, stock.price, stock.market
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
│  ├─ security, websocket
│  ├─ pagination, validation, format, constant
└─ exception
```

DTO가 별도의 최상위 계층이 아니라 각 도메인 하위에 배치되어 있고, MVC 화면 모델과 요청 객체를 함께 포함한다.

## 2. 계층별 책임

### Controller

React 화면과 JSON 요청을 연결한다. 사용자 화면 URL은 React 진입 문서로 전달하고,
React 헤더의 인증 상태는 `/api/session`이 세션 사용자와 CSRF 정보를 JSON으로 반환한다. `/api/accounts`와
`/api/investments`는 홈과 목록의 조회 데이터를 반환하며 각 `/primary` POST API가 기존 서비스의 대표계좌 변경을 호출한다. 투자 학습 카탈로그와
개념 상세, 주제별 시장 리포트와 모든 업무 데이터도 JSON API로 조회한다. 상태 변경은 CSRF 토큰을 포함한
JSON 요청으로 받고 기존 Service의 검증과 트랜잭션을 재사용한다.

- `AccountController`: 일반 계좌, 이체, 한도, 내역
- `InvestmentController`: 투자 화면 URL을 React 진입 문서로 연결
- `InvestmentReadApiController`: 포트폴리오의 계좌·최근 평가가·업종 비중·환율과 종목 상세의 OHLC 일봉 데이터를 JSON으로 반환
- `StockController`: 시장별 종목/업종 검색, 관심 종목, 상세, 랭킹 데이터
- `StockConceptController`: 종목 ID와 enum 개념 코드를 받아 종목 상세의 개념정보 JSON 반환
- `OrderController`: 주문 화면, 일반·예약 주문 접수와 취소
- `LoginController`: 회원가입과 로그인 화면

Spring Security의 `SecurityFilterChain`이 폼 로그인·Google/Kakao OIDC·Naver OAuth2 로그인·로그아웃과 URL 인가를 처리한다. 로컬 로그인은 `FinMateUserDetailsService`와 `DaoAuthenticationProvider`를 사용한다. `FinMateOidcUserService`는 Google·Kakao OIDC 사용자를, `FinMateOAuth2UserService`는 Naver OAuth2 사용자를 로컬 `User`에 매핑한다. 보호 컨트롤러는 로그인 방식과 무관하게 `@AuthenticationPrincipal FinMateAuthenticatedPrincipal`에서 로컬 사용자 ID를 받아 서비스 계층의 소유권 검증에 전달한다.

브라우저가 보호 화면 URL을 Spring에 직접 요청하면 Security가 로그인 페이지로 리다이렉트하고 원래 요청을 저장한다. React Router 내부 이동은 새 HTML 요청이 없으므로 `ProtectedRoute`가 먼저 `/api/session`으로 인증 여부를 확인하고, 비로그인 사용자를 원래 주소가 담긴 `redirect` 파라미터와 함께 `/login`으로 보낸다. 로그인 성공 후에는 검증된 FinMate 내부 경로로 복귀한다. 세션이 만료된 상태에서 `/api/**`를 호출하면 Spring은 로그인 HTML 대신 `401 Unauthorized`를 반환하고, 프론트엔드 공통 HTTP 모듈이 이를 로그인 이동으로 처리한다.

소셜 로그인 흐름은 다음과 같다.

```text
/oauth2/authorization/{google|kakao|naver}
  -> 공급자 인증·동의
  -> /login/oauth2/code/{registrationId}
  -> Spring Security가 authorization code와 token 처리
  -> Google/Kakao: FinMateOidcUserService(providerSubject=sub)
  -> Naver: FinMateOAuth2UserService(providerSubject=response.id)
  -> OAuthAccountService
  -> 기존 OAuthAccount 조회 또는 비밀번호 없는 User + OAuthAccount 생성
  -> FinMateOidcPrincipal 또는 FinMateOAuth2Principal
  -> SecurityContext -> HTTP Session
```

Google·Kakao의 `sub`와 Naver의 프로필 `id`는 각 공급자 내에서 사용자를 식별하는 키다. `OAuthAccount`가 `provider + providerSubject`를 로컬 `User.id`에 연결하며, 공급자 비밀번호와 OAuth 토큰은 영속화하지 않는다. 동일 이메일을 근거로 서로 다른 공급자나 로컬 계정을 자동 병합하지 않는다.

### View

`frontend/`의 React 애플리케이션이 전체 사용자 화면과 공통 헤더를 렌더링한다. 각 화면 진입 Controller는
Gradle 빌드가 `static/react`에 포함한 Vite 진입 문서로 요청을 전달하고, React는 `/api/session`과 업무별
JSON API를 조회한다. 공통 화면 토큰은 `static/css/common.css`를 사용한다.
React 최상단 오류 경계는 특정 컴포넌트의 렌더링 오류가 전체 흰 화면으로 번지는 것을 막고, 데이터 조회가 오래 걸리는 화면은 공통 로딩 상태를 표시한다.
종목 상세는 일봉 원본으로 캔들·거래량·이동평균선을 렌더링하고, 포트폴리오는 서버가 제공한 평균매입가와 최근 종가를 초기 상태로 사용한다. 장중 `STOCK_TRADE` WebSocket 메시지가 도착하면 React 상태의 현재가를 바꾸고 평가금액·손익·수익률 표시를 다시 계산한다. 통화 환산은 화면 표시 전용이며 서버의 실제 잔고나 거래금액을 변경하지 않는다.

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
- `StockConceptCardSyncService`: YAML의 enum별 공식 개념 카드 seed를 멱등하게 생성·갱신
- `StockConceptCardStartupSyncRunner`: 명시적으로 활성화한 서버 시작 시 공식 개념 카드를 한 번 즉시 동기화
- `StockConceptQueryService`: DB에 동기화된 활성 공식 개념 카드를 조회해 개념 패널 응답으로 제공
- `StockConceptVisualCatalog`: 개념 코드별 공용 SVG 학습 그림의 정적 경로, 대체문구와 캡션을 API 응답에 결합
- `StockConceptAnalysisService`: 요청 종목의 상세 갱신·조회 결과를 개념 코드별 실제 값, 계산식,
  기준시점과 중립적인 설명으로 변환하며 산정 기준이 다른 값은 임의로 재계산하지 않음
- `InvestmentLearningCatalog`: 투자 학습 화면에 노출할 17개 개념의 카테고리와 표시 순서를 관리
- `StockDetailService`: 선택 기간의 일봉을 조회해 화면에 OHLCV 원본 DTO로 전달한다. 캔들 좌표,
  가격·날짜축, 이동평균선과 확대·이동 같은 표현 계산은 브라우저 차트 렌더러가 담당한다.
- `StockNewsService`: `{종목명} 시장정보`로 조회한 NAVER 뉴스 후보를 제목의 투자 핵심 키워드 수와
  발행일시로 정렬하고, 상위 10건을 종목별 MySQL 캐시에 저장해 `updatedAt`이 기본 6시간을 넘었을 때만 갱신한다.
- `MarketReportService`: KOSPI·KOSDAQ·NASDAQ·S&P 500·금리·환율 주제별 NAVER 뉴스 후보를 각 주제의 제목
  키워드 수와 발행일시로 정렬하고, 상위 10건을 주제별 MySQL 공유 캐시에 저장한다.
- `NewsRankingService`: 종목 뉴스와 시장 리포트가 함께 사용하는 제목 키워드 동일 가중치 점수화와
  점수·발행일시·NAVER 원본 순서 정렬을 담당한다.

### Repository

Spring Data JPA 인터페이스다. 조회, 집계, fetch join 및 `PESSIMISTIC_WRITE` 조회를 제공한다. Repository 구현 클래스나 QueryDSL은 없다.

### Domain

JPA 엔티티, enum, 정책과 DTO를 포함한다. 잔액 변경, 자산 잠금, 주문 상태 전이는 엔티티 메서드에 들어 있다. 서비스는 여러 엔티티와 저장소를 묶어 유스케이스를 완성한다.

### Infra

현재 별도 인프라 패키지는 KIS 연동에 집중되어 있다.

- 공통 설정, REST 인증·호출 제한·재시도
- 종목 마스터 파일과 국내 업종코드 파일 다운로드
- 일봉·랭킹 REST API client
- 국내 종목 상세의 현재가·재무비율·손익계산서·대차대조표·투자자 일별 수급 REST client
- KIS WebSocket approval key, 연결·재연결, 구독 메시지와 payload 파싱

Redis 접근 코드는 `service.stock.ranking.StockRankingCacheService`에 있어 엄격한 계층 분리는 아니다.

## 3. 핵심 클래스 관계

```text
User
 ├─ OAuthAccount(GOOGLE/KAKAO sub, NAVER profile id)
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

국내 종목 상세 조회의 현재가·재무 갱신은 `DomesticStockDetailRefreshService`가 담당한다. 재무정보처럼
같은 기준 데이터를 갱신하는 데이터는 마지막 성공 시각을 검사해 MySQL에 upsert한다. 거래일마다 누적되는
투자자 수급·공매도·대차의 수명주기는 `DomesticStockDailyFlowRefreshService`에 위임한다.

일별 흐름 서비스는 시장 상태만 판단한다. 마감된 거래일의 누락 구간은
`DomesticStockFinalizedDailyFlowSyncService`가 DB 마지막 거래일 다음 날부터 확정 거래일까지 KIS에서 받아
MySQL에 저장한다. 투자자매매동향처럼 KIS가 기준일 하나만 받는 API는 확정일 기준 응답에서 DB 최신일
다음 날 이후의 누락 행만 필터링해 저장한다. 아직 값이 변하는 금일 데이터는
`DomesticStockIntradayDailyFlowService`가 조회하고
`DomesticStockDailyFlowCacheService`가 `stock:daily-flow:{symbol}` Redis 스냅샷으로만 보관한다. 통합 일봉
확정 뒤 DB 동기화가 끝나면 금일 스냅샷을 제거한다. `DomesticStockDetailQueryService`는 확정된 DB 이력과
Redis 금일 스냅샷을 합쳐 화면 DTO를 만든다.

장중 현재가 REST 응답은 `DomesticStockCurrentQuoteCacheService`가 Redis에 10초간 공유하고 WebSocket
값이 화면에서 우선한다. MySQL 현재가 스냅샷은 장 마감 뒤 종목별 첫 조회에서 하루 한 번 갱신한다.
외부 호출 실패는 API별로 격리하여 마지막 정상 DB 이력은 계속 반환한다.

### NAVER 뉴스 검색

`NaverNewsClient`는 JDK `HttpClient`로 NAVER API HUB의 `/search/v1/news`를 호출한다. 검색어는
`{Stock.nameKo} 시장정보`이며 `display=40`, `start=1`, `sort=sim`, `format=json`으로 고정한다.
인증에는 NAVER OAuth와 별개인 `X-NCP-APIGW-API-KEY-ID`, `X-NCP-APIGW-API-KEY` 값을 사용한다.

`StockNewsService`는 관련도순 후보 40건의 제목에서 `실적`, `매출`, `영업이익`, `순이익`, `주가`,
`투자`, `애널리스트`, `컨센서스`, 수급·사업·주주환원·등락 관련 핵심 키워드의 포함 여부를 각각 같은 1점으로 계산한다.
같은 키워드가 제목에 반복되어도 한 번만 계산하며, 점수 내림차순, 발행일시 내림차순, NAVER 원본 순서로
정렬한 상위 10건만 종목별 `stock_news_cache` 한 행에 검색어와 뉴스 목록 JSON으로 저장한다. 저장 행의
`updatedAt + 6시간`이 현재 시각보다 뒤이고 검색어도 동일하면 DB 값을 반환하고, 만료됐거나 검색어가
바뀌었으면 NAVER API를 다시 호출해 같은 행을 갱신한다. 종목 상세 HTML 렌더링과 외부 뉴스 호출은 분리하며,
뉴스 탭을 처음 선택할 때 브라우저가 `/api/stocks/{stockId}/news`를 비동기 호출한다.

시장 리포트는 `MarketReportTopic`에 주제 코드, 화면명, 검색어와 제목 키워드를 정의한다. 검색어는 각각
`코스피 시장정보`, `코스닥 시장정보`, `나스닥 시장정보`, `S&P 500 시장정보`, `금리 시장정보`,
`환율 시장정보`다. 지수 주제는
지수·증시·수급·등락·마감·전망 관련 단어를, 금리는 중앙은행·기준금리·채권·물가·통화정책 관련 단어를,
환율은 주요 통화·강약세·외환시장·무역 관련 단어를 사용한다. `/api/market-reports/{topic}` 응답의 최종
10건은 `market_report_cache`의 주제별 한 행에 저장하고 모든 사용자가 기본 6시간 동안 공유한다. 같은 JVM에서
동일 주제 갱신이 겹치면 하나의 외부 호출만 수행한다. React `/investments/reports` 화면은 선택한 주제만
`/api/market-reports/{topic}`으로 지연 조회하고, 이미 받은 주제 응답은 브라우저 메모리에 재사용한다.

### Redis

랭킹 캐시는 `StockRankingCacheService`가 관리한다. 키는 `stock:ranking:{market}:{type}`이고 값은 `StockRankingBoard` JSON, TTL은 장중 기본 30초·장외 기본 86,400초다.

환율·해외 지수 실시간 캐시는 `MarketRealtimeCacheService`가 관리한다. 키는 `market:realtime:{indicator}`이고 값은 `MarketRealtimeMessage` JSON, TTL은 기본 120초다. 세션, 토큰, WebSocket Pub/Sub 용도는 **현재 구현되지 않음**.

국내 종목 상세 현재가 REST 캐시는 `DomesticStockCurrentQuoteCacheService`가 관리한다. 키는
`stock:price:{symbol}`이고 값은 조회 시각을 포함한 현재가 스냅샷 JSON, TTL은 기본 10초다.

국내 종목 상세의 금일 투자자 수급·공매도·대차 캐시는 `DomesticStockDailyFlowCacheService`가 관리한다.
키는 `stock:daily-flow:{symbol}`이고 값은 동일 거래일의 세 API 결과를 합친 스냅샷 JSON이다. TTL은
기본 10분이며 주기 스케줄러가 아니라 만료 뒤 상세 조회가 들어올 때만 KIS를 다시 호출한다. 이 캐시는
확정 이력 저장소가 아니며 KRX+NXT 통합 일봉 확정 뒤 DB 동기화가 끝나면 제거한다.

### WebSocket

외부 실시간 연결과 브라우저용 WebSocket 경로가 분리되어 있다.

1. KIS ↔ 서버: `KisRealtimeWebSocketClient`가 JDK WebSocket으로 연결한다.
2. 브라우저 시세 ↔ 서버: `/ws/stocks`에 `StockRealtimeWebSocketHandler`, `/ws/market-data`에 `MarketRealtimeWebSocketHandler`가 연결된다.
3. 브라우저 채팅 ↔ 서버: `/ws/chat`에 `StockChatWebSocketHandler`가 연결되고 `HttpSessionHandshakeInterceptor`가 로그인 HTTP 세션 정보를 전달한다.

KIS payload는 `KisRealtimeStore`에 최신값으로 저장되고 Spring 동기 이벤트로 발행된다. `StockRealtimeClientSessionService`와 `MarketRealtimeClientSessionService`는 구독 브라우저에 JSON을 보내며, `StockTradingRealtimeExecutionListener`는 같은 이벤트로 체결을 시도한다.

종목 상세 일봉 차트는 서버가 `StockChartCandleData`로 날짜·OHLCV·거래대금만 전달하고, 브라우저가
Canvas에 캔들·거래량·MA5·MA20·MA60과 동적 축을 그린다. 현재 전달받은 조회 범위 안에서 휠 확대·축소,
마우스 드래그, 트랙패드 두 손가락 좌우 이동과 십자선 툴팁을 처리한다. 최초 화면에는 최근 63거래일
(약 3개월)을 표시하며, 축소하면 선택한 조회 기간 전체를 한 화면에서 볼 수 있다. 나머지 일봉은 차트 드래그
또는 하단 기간 바를 움직여 탐색한다. 기간 바의 손잡이 길이와 위치는 전체 조회 기간 중 현재 화면에 보이는
범위를 나타내며 확대·축소에 따라 함께 변한다. 과거 구간을 추가로 가져오는 별도 페이지 조회 API는 사용하지 않는다.

React 투자 학습 화면은 `/investment-learning`에서 계좌·거래, 위험관리·포트폴리오, 펀드·ETF,
시장 안전장치, 파생상품·공매도 카테고리를 제공한다. 최초 진입 시 `/api/investment-learning/catalog`에서
카테고리와 17개 개념 요약을 조회하고, 검색·필터는 브라우저에서 처리한다. 카드를 열 때
`/api/investment-learning/concepts/{conceptCode}`로 DB에 동기화된 정적 설명과 공용 SVG를 조회한다.
상세 API는 투자 학습 카탈로그에 등록된 코드만 허용하며 종목 상세 데이터나 KIS API는 호출하지 않는다.
투자 학습 카드의 `marketImpact`는 별도의 초록색 `시장과 연결해서 보기` 영역으로 렌더링하며,
대형기관 리밸런싱·ETF 자금 흐름·파생상품 헤지처럼 개념이 실제 수급과 가격에 연결되는 과정과
방향성 신호로 단정할 수 없는 이유를 함께 보여준다.

투자자 수급 SVG 차트와 시장지표 차트의 상세 값 확인도 브라우저 상호작용으로 처리한다. 투자자 수급 차트는
가장 가까운 거래일에 세로 기준선을 맞추고 외국인·개인·기관 순매수를 한 툴팁에서 비교한다. 환율·지수 차트는
가장 가까운 거래일의 종가 지점을 강조하고 해당 일자의 OHLC와 거래량을 툴팁으로 표시한다.

종목 채팅의 `JOIN_ROOM`, `LEAVE_ROOM`, `SEND_MESSAGE`, `EDIT_MESSAGE`, `DELETE_MESSAGE` 명령은 `StockChatClientSessionService`가 처리한다. 메시지 작성·수정·소프트 삭제와 답글 관계는 MySQL의 `stock_chat_message`에 저장한다. 수정·삭제 권한은 WebSocket payload의 사용자 값이 아니라 handshake에서 전달된 Spring Security `SecurityContext`의 `FinMateAuthenticatedPrincipal`과 메시지 작성자를 비교해 판단한다. 삭제된 메시지 행은 답글 연결을 보존하기 위해 남기고 API 응답에서는 원문을 숨긴다. 종목별 연결 세션과 접속 인원, 실시간 fan-out은 현재 단일 JVM 메모리에 있으므로 다중 인스턴스에서는 Redis Pub/Sub 같은 별도 전파 계층이 필요하다.

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
- 만료 처리: `StockOrderExpirationScheduler` → `StockOrderExpirationService.expireOverdueOrdersAndReservations()`
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
- 주문·예약 만료: 서버 시작 즉시 한 번 실행한 뒤 기본 10초 간격. 중단 중 만료된 활성 건도 시작 시 복구
- 종목 마스터: 국내·NASDAQ 평일 오전 8시, 각 시장 시간대. 국내 업종코드는 국내 종목 마스터와 같은 스케줄에서 함께 갱신한다.
- 공식 주식 개념 카드: 기본 비활성화. 활성화하면 매주 월요일 오전 4시에 `stock-concepts.yml`과 DB를 멱등 동기화한다.
- 즉시 반영이 필요하면 `STOCK_CONCEPT_SYNC_ON_STARTUP=true`로 시작 동기화를 한 번 실행하고 다시 비활성화한다.
- 랭킹: 기본 100ms 후 시작, 이전 실행 완료 후 10초 간격
- 구독 해제: 별도 단일 스레드 executor로 기본 60초 유예
- KIS 재연결: 별도 단일 스레드 executor로 3초 후 재시도
- 애플리케이션 시작: 활성 주문·예약의 구독을 `ApplicationRunner`가 복구
- DB 상태 연동 이벤트: 주문/예약 구독은 커밋 후 처리
- 실시간 payload 이벤트: 브라우저 전파와 거래 체결 처리
