# 한국투자증권(KIS) 연동

이 문서는 시세·기준정보 수집 경계를 설명한다. KIS 시세는 내부 모의 거래의 판단 입력이며 외부 주문 접수나 체결 확인이 아니다. 이 경계와 장애·중복·stale 데이터에 대한 금융 정합성 계약은 [금융 불변식](FINANCIAL_INVARIANTS.md)을 따른다.

## 1. 설정과 공통 구조

`KisProperties`가 다음 `finmate.kis.*` 설정을 바인딩한다.

- `base-url`, `app-key`, `app-secret`
- `request-interval-millis` (기본 700ms)
- `web-socket-url`, `web-socket-path`
- 구독 해제 유예는 별도 `@Value`로 `realtime-unsubscribe-grace-millis`를 읽는다.

REST와 WebSocket은 반드시 같은 KIS 환경의 자격 증명과 endpoint를 사용해야 한다. 특히 해외주식
`HDFSCNT0` 실시간지연체결가와 `HDFSASP0` 미국 실시간호가는 모의투자 WebSocket에서 지원되지 않으므로
이 기능을 사용할 때는 실전 REST/실전 WebSocket 설정을 한 쌍으로 사용한다. VTS REST 랭킹과 실전
WebSocket 체결값을 함께 사용하면 서로 다른 환경의 현재가·거래량·거래대금을 비교하게 된다.

공통 REST 흐름은 `도메인 client -> KisRestClient -> JDK HttpClient -> KIS`다.

## 2. KIS REST API

코드에서 확인된 API는 다음과 같다.

| 용도 | 경로 | TR ID |
|---|---|---|
| 국내 종목 일봉 | `/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice` | `FHKST03010100` |
| 해외 종목 일봉 | `/uapi/overseas-price/v1/quotations/dailyprice` | `HHDFS76240000` |
| 환율·해외 지수 일봉 | `/uapi/overseas-price/v1/quotations/inquire-daily-chartprice` | `FHKST03030100` |
| 환율·해외 지수 분봉 | `/uapi/overseas-price/v1/quotations/inquire-time-indexchartprice` | `FHKST03030200` |
| 국내 지수 일봉 | `/uapi/domestic-stock/v1/quotations/inquire-daily-indexchartprice` | `FHKUP03500100` |
| 국내 거래량 랭킹 | `/uapi/domestic-stock/v1/quotations/volume-rank` | `FHPST01710000` |
| 해외 거래량 랭킹 | `/uapi/overseas-stock/v1/ranking/trade-vol` | `HHDFS76310010` |
| 해외 거래대금 랭킹 | `/uapi/overseas-stock/v1/ranking/trade-pbmn` | `HHDFS76320010` |
| 해외 업종별코드조회 | `/uapi/overseas-price/v1/quotations/industry-price` | `HHDFS76370100` |

국내 거래대금도 국내 랭킹 client의 같은 경로·TR ID에 구분 파라미터를 전달한다.
종목 랭킹 갱신은 `StockMarketSchedules`의 정규장과 시간외 거래 가능 시간을 함께 기준으로 판단하고, 정규장 또는 시간외 장 마감 직후 2분 이내에는 최종 갱신을 허용한다.

시장지표 일봉은 `MarketIndicatorSymbol`의 KIS API 타입에 따라 분기한다. `USD_KRW`, `NASDAQ_COMPOSITE`, `NASDAQ_100`은 해외 가격 기간별 차트 API를 사용하고, `KOSPI`, `KOSDAQ`은 국내 주식업종기간별시세 API를 사용한다. 포트폴리오 화면은 실시간 종목 시세가 없을 때 최신 종가 fallback을 표시하기 위해 종목 일봉을 DB 우선으로 조회하고, 부족하면 최근 구간을 KIS 종목 일봉 API로 보충한다.

환율·해외 지수 실시간 화면값은 `KisOverseasMarketMinuteChartPriceClient`가 해외지수분봉조회 API를 1분 단위로 호출하고, `MarketRealtimeCacheService`가 Redis에 `market:realtime:{indicator}` 키로 TTL 캐시한다.

종목 마스터는 `KisStockMasterFileClient`가 별도 마스터 파일을 다운로드·압축 해제하고 파서와 적용 서비스가 DB에 반영한다. 국내 업종코드는 같은 파일 다운로드 클라이언트가 `idxcode.mst.zip`을 내려받아 `DomesticStockSectorCode`에 저장한다.

해외 업종코드는 종목 상세·목록·포트폴리오 화면에서 `OverseasStockMetadata.exchangeCode`와 `industryCode`를 기준으로 `OverseasStockIndustryCode`를 조회한다. DB에 없으면 해외 업종별코드조회 API를 거래소 단위로 호출하고 응답 목록을 upsert한 뒤 다시 조회한다. API 호출 실패 시 화면은 기존 코드 fallback을 표시한다.

## 3. 토큰 발급 및 갱신

### REST access token

1. `KisAuthClient`가 `/oauth2/tokenP`에 `client_credentials`, app key·secret을 POST한다.
2. `KisTokenService`가 토큰과 만료 시각을 JVM 메모리에 저장한다.
3. 만료 5분 전부터 unusable로 판단해 다음 요청에서 새 토큰을 발급한다.
4. 응답 만료 시각이 없으면 `expires_in`, 그것도 없으면 23시간을 사용한다.

`KisTokenService.clear()`는 있으나 REST 호출이 인증 실패했을 때 자동 clear 후 재발급하는 경로는 **현재 구현되지 않음**.

### WebSocket approval key

1. `KisWebSocketApprovalClient`가 `/oauth2/Approval`에 app key·secret을 POST한다.
2. `KisWebSocketApprovalService`가 approval key를 JVM 메모리에 저장한다.
3. 응답에 만료 정보가 없으므로 코드 고정값 23시간을 사용하고 5분 전에 갱신 대상으로 본다.

서버 재시작·다중 인스턴스 간 토큰 공유는 **현재 구현되지 않음**. Redis에는 토큰을 저장하지 않는다.

## 4. 호출 제한

`KisRateLimiter.waitTurn()`은 프로세스 전체의 마지막 KIS 요청 시각을 기준으로 요청 사이를 기본 700ms 이상 벌린다. 메서드가 `synchronized`이므로 한 JVM 내 REST 토큰·조회·approval 요청이 같은 제한기를 공유한다.

이는 설정 가능한 고정 간격 조절이다. KIS 상품별·TR별 공식 제한 수치를 코드가 모델링하지는 않는다. 실제 허용량은 **확인 필요**.

## 5. 재시도

`KisRetryConnection`이 KIS HTTP 요청과 KIS WebSocket 최초 연결의 공통 재시도를 처리한다.

- HTTP 적용 대상: `KisAuthClient`, `KisRestClient`, `KisWebSocketApprovalClient`
- WebSocket 적용 대상: `KisRealtimeWebSocketClient` 최초 연결
- 최초 호출을 포함해 총 5회 시도
- 실패 후 다음 재시도 전 2초 대기
- HTTP 요청은 각 시도 전 `KisRateLimiter.waitTurn()`으로 요청 간격 조절
- HTTP 재시도 대상: 네트워크 예외, HTTP 429, HTTP 5xx, 응답 body의 `EGW00201`
- HTTP 비재시도 대상: JSON 파싱 실패, 재시도 대상이 아닌 4xx, KIS 업무 실패 코드, 빈 access token·approval key 응답

REST 호출이 인증 실패했을 때 access token을 자동 clear 후 재발급하는 경로는 **현재 구현되지 않음**.

## 6. WebSocket 구독

### 연결

- 기본 endpoint: `ws://ops.koreainvestment.com:21000/tryitout`
- 첫 구독 시 approval key를 먼저 확보한 뒤 지연 연결
- 최초 연결 실패 시 총 5회까지 연결 시도
- 연결 시도 중 들어온 다른 구독 요청은 연결 완료 또는 실패까지 대기
- 구독 메시지에 approval key, `tr_type`, `tr_id`, `tr_key` 포함
- `tr_type=1` 구독, `tr_type=2` 구독 해제
- ping/pong 처리
- 연결 종료 시 활성 구독이 있으면 3초 뒤 재연결, 실패하면 다시 예약
- 재연결 후 메모리의 활성 구독 전체 재전송

### 구독 종류

국내 KOSPI/KOSDAQ 및 NASDAQ 종목에 대해 체결과 호가 두 구독을 만든다. `StockRealtimeSubscriptionManager`는 상세 화면, 활성 주문, 활성 예약 등 목적별 참조 수와 전체 참조 수를 `ConcurrentHashMap`/`AtomicInteger`로 관리한다.

국내 KOSPI/KOSDAQ 지수 상세 화면은 `MarketRealtimeSubscriptionManager`가 `H0UPCNT0` 국내지수 실시간체결을 구독한다. 구독 키는 `MarketIndicatorSymbol`의 KIS symbol을 사용하며, `KOSPI=0001`, `KOSDAQ=1001`이다.

참조 수가 0이 되면 즉시 끊지 않고 기본 60초 유예 후 다시 0인지 확인하여 해제한다.

## 7. 실시간 데이터 저장과 전파

```text
KIS WebSocket text frame
 -> KisRealtimeMessageParser
 -> KisRealtimePayload
 -> KisRealtimeStore.put()                 [JVM 메모리 최신값]
 -> KisRealtimePayloadReceivedEvent        [Spring 동기 이벤트]
      ├─ StockRealtimeClientSessionService [브라우저 구독자에 JSON 전송]
      └─ StockTradingRealtimeExecutionListener
           └─ 활성 예약·주문 체결 판단
```

브라우저는 서버의 `/ws/stocks`, `/ws/market-data` raw WebSocket endpoint에 연결한다. 종목 상세,
주문, 포트폴리오 화면은 각 화면 목적의 참조 수를 등록하며, 주문 화면은 체결가와 최우선 매수·매도
호가를 받아 현재가·매수 기준가·매도 기준가를 갱신한다. STOMP나 메시지 브로커는 사용하지 않는다.

## 8. 저장·운영 한계

- KIS WebSocket 최신 실시간 payload는 DB·Redis가 아니라 `ConcurrentHashMap`에 저장된다.
- 환율·해외 지수 1분 조회 결과는 Redis에 TTL 캐시된다.
- 브라우저 세션, 구독 참조 수, KIS 연결 상태도 JVM 로컬이다.
- 프로세스 재시작 시 payload와 클라이언트 세션은 사라지며, DB의 활성 주문·예약 구독만 `ApplicationRunner`가 복구한다.
- 서버 여러 대 사이의 실시간 fan-out, leader election, 중복 체결 방지 설계는 **현재 구현되지 않음**.
- WebSocket 메시지 재전송, 순서 보장, 유실 복구용 offset은 **현재 구현되지 않음**.
