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
| 국내 종목 통합 일봉(KRX+NXT) | `/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice` | `FHKST03010100` |
| 해외 종목 일봉 | `/uapi/overseas-price/v1/quotations/dailyprice` | `HHDFS76240000` |
| 환율·해외 지수 일봉 | `/uapi/overseas-price/v1/quotations/inquire-daily-chartprice` | `FHKST03030100` |
| 환율·해외 지수 분봉 | `/uapi/overseas-price/v1/quotations/inquire-time-indexchartprice` | `FHKST03030200` |
| 국내 지수 일봉 | `/uapi/domestic-stock/v1/quotations/inquire-daily-indexchartprice` | `FHKUP03500100` |
| 국내 거래량 랭킹 | `/uapi/domestic-stock/v1/quotations/volume-rank` | `FHPST01710000` |
| 국내 주식현재가 시세 | `/uapi/domestic-stock/v1/quotations/inquire-price` | `FHKST01010100` |
| 국내 재무비율 | `/uapi/domestic-stock/v1/finance/financial-ratio` | `FHKST66430300` |
| 국내 손익계산서 | `/uapi/domestic-stock/v1/finance/income-statement` | `FHKST66430200` |
| 국내 대차대조표 | `/uapi/domestic-stock/v1/finance/balance-sheet` | `FHKST66430100` |
| 국내 종목별 투자자매매동향(일별) | `/uapi/domestic-stock/v1/quotations/investor-trade-by-stock-daily` | `FHPTJ04160001` |
| 국내 주식 공매도 일별추이 | `/uapi/domestic-stock/v1/quotations/daily-short-sale` | `FHPST04830000` |
| 국내 주식 일별 대차거래 추이 | `/uapi/domestic-stock/v1/quotations/daily-loan-trans` | `HHPST074500C0` |
| 해외 거래량 랭킹 | `/uapi/overseas-stock/v1/ranking/trade-vol` | `HHDFS76310010` |
| 해외 거래대금 랭킹 | `/uapi/overseas-stock/v1/ranking/trade-pbmn` | `HHDFS76320010` |
| 해외 업종별코드조회 | `/uapi/overseas-price/v1/quotations/industry-price` | `HHDFS76370100` |

국내 거래대금도 국내 랭킹 client의 같은 경로·TR ID에 구분 파라미터를 전달한다.
국내 종목 상세의 장중 현재가 REST 응답은 `stock:price:{symbol}` Redis 키에 기본 10초 TTL로 공유하고,
Redis 장애 시 같은 TTL의 JVM 캐시를 fallback으로 사용한다. 브라우저에서는 KIS WebSocket 체결값이
우선한다. 장중 현재가를 MySQL에 반복 저장하지 않고, 장이 닫힌 뒤 해당 종목의 첫 조회에서 그 거래일
스냅샷을 한 번 저장한다. `DomesticStockDetailRefreshState`는 DB 스냅샷과 재무정보 및 확정 일별
데이터의 마지막 성공 갱신시각을 보관한다. 재무정보의 기본 신선도는 24시간이다. 투자자 수급과
공매도·대차의 금일 변동값은 MySQL에 쓰지 않고 `stock:daily-flow:{symbol}` Redis 키에 10분 동안
공유한다. 한 API의 조회가 실패해도 다른 API의 캐시·저장 데이터와 기존 상세 화면은 반환한다.
재무 세 API는 분기 구분(`FID_DIV_CLS_CODE=1`)으로
저장하며 상세 화면에는 최신 4개 분기를 비교해 보여준다. 기존 연간 데이터는 기간 구분값이 달라 보존되지만
상세 화면의 기본 조회 대상에서는 제외한다. 재무 탭의 YoY, QoQ, TTM과 런레이트는 추가 KIS 호출 없이
저장된 분기 손익계산서에서 계산한다. KIS가 제공하는 연도 누적 손익에서 같은 해의 직전 분기 누적값을
차감해 개별 분기 실적으로 환산한다. 표에는 최신 4개 분기를 노출하고 최신 분기의 전년 동기까지 같은
방식으로 환산하기 위해 계산 시에만 최대 6개 누적 분기를 조회한다. 비교 기준 분기가 없거나 기준 실적이
0 이하이면 변화율은 계산하지 않는다.
투자자매매동향은 개인·기관·외국인의 매수·매도·순매수 흐름이며 보유 지분율이 아니다. 종목 상세의
투자자 탭은 저장된 일별 순매수 수량을 거래일 오름차순으로 정렬해 하나의 SVG 꺾은선그래프로 비교한다.
양수는 순매수, 음수는 순매도이며 0 기준선을 항상 표시한다. 그래프는 표를 대체하지 않고 기간별 방향을
빠르게 읽기 위한 요약이며, 전체 투자자 계열에서 순매수가 가장 큰 지점과 가장 작은 지점을 투자자·날짜와
함께 표시한다. 정확한 매수·매도·순매수 수량은 기존 표에서 함께 제공한다.
상세 화면에서는 최근 20거래일, 즉 약 4주의 투자자 수급을 기본 비교 구간으로 사용한다. 투자자매매동향
API가 한 번에 반환하는 최근 일별 응답 중 DB 최신일 다음 날 이후의 누락 행만 저장하며, 화면 조회만 최신 20건으로
제한한다. 따라서 기존 투자자 수급 행을 4주가 지났다는 이유로 삭제하는 보관 정책은 현재 적용하지 않는다.
상세 표는 순매수 그래프를 먼저 읽을 수 있도록 기본 접힘 상태로 제공한다.

공매도와 대차거래는 최초 조회 시 화면 분석에 필요한 최근 3개월을 적재하고, 이후에는 DB 마지막 확정
거래일 다음 날부터 새로 확정된 거래일까지의 누락 구간만 추가한다. 화면은 최근 1개월을 기본 표시하며
사용자가 3개월로 전환할 수 있다. 화면 조회 범위와 DB 보관 범위는 분리되어 있으며, 3개월이 지났다는
이유로 기존 확정 행을 삭제하지 않는다.
종목 랭킹 갱신은 `StockMarketSchedules`의 거래 가능 시간을 기준으로 판단한다. KOSPI/KOSDAQ은
기존 KRX 운영시간을 유지하고, NASDAQ은 현지 시각 기준 프리마켓 04:00부터 정규장과
애프터마켓을 거쳐 20:00까지 갱신한다. 정규장 또는 시간외 장 마감 직후 2분 이내에는 최종
갱신을 허용한다.

시장지표 일봉은 `MarketIndicatorSymbol`의 KIS API 타입에 따라 분기한다. `USD_KRW`, `NASDAQ_COMPOSITE`, `NASDAQ_100`은 해외 가격 기간별 차트 API를 사용하고, `KOSPI`, `KOSDAQ`은 국내 주식업종기간별시세 API를 사용한다. 포트폴리오 화면은 실시간 종목 시세가 없을 때 최신 종가 fallback을 표시하기 위해 종목 일봉을 DB 우선으로 조회하고, 부족하면 최근 구간을 KIS 종목 일봉 API로 보충한다.
NASDAQ 종목 일봉의 기대 최신 거래일은 프리·애프터마켓 종료 시각이 아니라 뉴욕 현지 정규장 종가인
16:00을 기준으로 계산한다.

종목 상세 차트의 서버 경계는 선택 기간에 저장된 일봉을 조회해 날짜·시가·고가·저가·종가·거래량·
거래대금을 전달하는 데까지다. 차트 좌표와 축, 이동평균선, 보이는 구간의 가격 범위는 브라우저에서
계산한다. 휠 확대·축소, 마우스 드래그와 트랙패드 두 손가락 좌우 이동은 최초 응답에 포함된 기간 안에서만 동작하므로 사용자 조작만으로
추가 KIS REST 호출이 발생하지 않는다. 최초 화면에는 최근 63거래일(약 3개월)을 표시하고, 축소하면 선택한
1~3년 조회 범위 전체까지 한 화면에 표시할 수 있다. 나머지 데이터는 차트 드래그나 하단 기간 바를 움직여
이동한다. 기간 바는 전체 조회 범위에서 현재 표시 중인 구간의 위치와 폭을 보여주며 확대·축소에 따라 손잡이
폭도 변한다. 보이는 구간의 최고·최저에는 가격, 현재 종가 대비 등락률과 거래일을 표시한다. 커서를 올리면
거래일·요일과 OHLC·거래량·거래대금의 전 거래일 대비 변화율을 함께 보여준다. KIS WebSocket 체결값은
당일 마지막 캔들에 반영한다. 환율·지수 종가 차트도 선택 기간의 최고·최저 종가와 해당 날짜를 차트 안에
표시한다.

국내 종목 일봉은 `FID_COND_MRKT_DIV_CODE=UN`으로 조회해 KRX와 NXT를 합친 통합 차트 기준으로 저장한다.
통합 일봉의 당일 OHLC·거래량·거래대금은 NXT 애프터마켓이 끝나는 20:00 이후에 확정 대상으로 보므로,
그 전에는 예상 최신 일봉 거래일을 직전 거래일로 계산하고 WebSocket 당일 캔들만 화면에 덧붙인다.
종목 상세 화면에서 저장된 일봉이 없으면 최대 3년을 최초 적재하고, 이후에는 마지막 저장 거래일의 다음
날짜부터 예상 최신 거래일까지의 누락 구간만 증분 저장한다. 따라서 20:00 이후 해당 종목의 상세 화면에
처음 접근한 요청이 그날의 통합 일봉을 저장하며, 전 종목 일괄 장마감 스케줄러는 사용하지 않는다.
포트폴리오 평가처럼 최근 종가만 필요한 기능이 먼저 일부 일봉을 저장했을 수도 있으므로, 상세 화면에서는
가장 오래된 저장 일봉도 확인한다. 최대 3년 전 또는 상장일 중 더 최근인 날짜까지 과거 구간이 부족하면
가장 오래된 저장 일봉의 전날까지 먼저 역방향으로 보충한 뒤 최신 누락 구간을 이어서 저장한다. 시작일이
주말·휴장일일 수 있으므로 목표 시작일로부터 7일 이내에 최초 일봉이 있으면 과거 구간이 채워진 것으로 본다.

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

국내 KOSPI/KOSDAQ 및 NASDAQ 종목에 대해 체결과 호가 두 구독을 만든다. 국내 종목은 KRX와 NXT를 합친
`H0UNCNT0`/`H0UNASP0` 통합 채널을 사용한다. 해외 체결의 `MTYP`은 정규장·프리마켓·애프터마켓
세션 표시와 실시간 주문 처리 가능 여부에 사용한다. `StockRealtimeSubscriptionManager`는 상세 화면,
활성 주문, 활성 예약 등 목적별 참조 수와 전체 참조 수를 `ConcurrentHashMap`/`AtomicInteger`로 관리한다.

국내 KOSPI/KOSDAQ 지수 상세 화면은 `MarketRealtimeSubscriptionManager`가 `H0UPCNT0` 국내지수 실시간체결을 구독한다. 구독 키는 `MarketIndicatorSymbol`의 KIS symbol을 사용하며, `KOSPI=0001`, `KOSDAQ=1001`이다.

참조 수가 0이 되면 즉시 끊지 않고 기본 60초 유예 후 다시 0인지 확인하여 해제한다.

### NXT 거래대상 종목

KIS KOSPI/KOSDAQ 종목 마스터에는 NXT 편입 여부가 없으므로 그 파일만으로는 종목별 NXT 거래 가능
시간을 판단할 수 없다. `NxtStockTradingPermissionSyncService`가 NXT 공식 시장정보의 전체 거래대상
종목과 `cptrTrdPmsnCd`를 평일 NXT 프리마켓 시작 전 07:50에 하루 한 번 동기화한다. 이 코드는 프리·메인·애프터마켓 허용 비트로
`Stock.nxtTradingPermissionCode`에 저장하며, `null`은 NXT 비대상, `0`은 NXT 대상이지만 현재 거래
제한 상태를 뜻한다. 외부 NXT 목록 조회는 DB 트랜잭션 밖에서 수행하고, 국내 종목 조회·비교·변경은
`NxtStockTradingPermissionApplyService`의 짧은 트랜잭션에서 dirty checking으로 반영한다. NXT 조회가 실패하거나
빈 목록을 반환하면 기존 값을 유지한다.

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

- 국내 종목 일봉은 상세 화면에서 수정주가(`DEFAULT_ADJUSTED_PRICE=true`)와 통합시장(`UN`) 기준으로 조회한다.
  이미 저장된 종목·거래일·수정주가 행은 다시 조회하지 않으며 상세 화면에서는 목표 과거 시작일 이전의
  누락 구간과 마지막 저장 거래일 이후의 최신 누락 구간을 추가한다.
- KIS WebSocket 최신 실시간 payload는 DB·Redis가 아니라 `ConcurrentHashMap`에 저장된다.
- 환율·해외 지수 1분 조회 결과는 Redis에 TTL 캐시된다.
- 브라우저 세션, 구독 참조 수, KIS 연결 상태도 JVM 로컬이다.
- 프로세스 재시작 시 payload와 클라이언트 세션은 사라지며, DB의 활성 주문·예약 구독만 `ApplicationRunner`가 복구한다.
- 서버 여러 대 사이의 실시간 fan-out, leader election, 중복 체결 방지 설계는 **현재 구현되지 않음**.
- WebSocket 메시지 재전송, 순서 보장, 유실 복구용 offset은 **현재 구현되지 않음**.

## 9. 공매도·대차 데이터

종목 상세의 일별 공매도 흐름은 KIS의 `국내주식 공매도 일별추이[국내주식-134]`를 사용한다.
요청 경로는 `/uapi/domestic-stock/v1/quotations/daily-short-sale`, TR ID는 `FHPST04830000`이다.
`FID_COND_MRKT_DIV_CODE=J`, `FID_INPUT_ISCD={종목코드}`와 선택 조회기간을 전달하며, 응답의
영업일·공매도 체결수량·거래량 대비 비율·공매도 거래대금·누적 비율을 `DomesticStockShortSaleDaily`에
종목과 거래일 단위로 저장한다.

일별 대차거래 흐름은 `국내주식 일별 대차거래 추이`를 사용한다. 요청 경로는
`/uapi/domestic-stock/v1/quotations/daily-loan-trans`, TR ID는 `HHPST074500C0`이며,
`MRKT_DIV_CLS_CODE=3`, `MKSC_SHRN_ISCD={종목코드}`, `START_DATE`, `END_DATE`, `CTS`를 전달한다.
응답의 `new_stcn`(신규 대차), `rdmp_stcn`(상환), `rmnd_stcn`(잔고), `rmnd_amt`(잔고 금액)를
`DomesticStockLoanTransactionDaily`에 저장한다.
공식 예제 기준 한 요청의 최대 조회 건수는 100건이므로 현재 3개월 조회 범위는 별도 장기 페이징 없이
상세 화면 용도로 충분하다.

시장 전체의 공매도 상위 종목 화면이 필요할 때만 `국내주식 공매도 상위종목[국내주식-133]`을 별도로
사용한다. 요청 경로는 `/uapi/domestic-stock/v1/ranking/short-sale`, TR ID는 `FHPST04820000`이며,
개별 종목 상세의 시계열을 대체하지 않는다.

공매도 체결 데이터와 대차거래 데이터는 서로 다른 단계다. 대차는 주식을 빌리고 갚는 흐름이며,
빌린 주식이 반드시 공매도로 매도되는 것은 아니다. 공매도 일별추이는 실제 공매도 체결 흐름이지만
아직 상환하지 않은 공매도 잔고와도 같지 않다. 화면은 이 차이를 명시하고 공매도 거래비중과 대차잔고를
별도 차트로 표시한다.

투자자 수급·공매도·대차는 같은 일별 흐름 정책을 사용한다. KRX 정규장 개장 이후부터 KRX+NXT 통합
일봉 확정 시각 전까지 금일 응답은 `stock:daily-flow:{symbol}`에 10분 TTL로만 저장한다. 이는 10분마다
실행되는 스케줄러가 아니라 캐시가 만료된 뒤 상세 조회가 들어왔을 때만 갱신하는 온디맨드 캐시다. 화면은
MySQL에 저장된 확정 과거 이력 뒤에 Redis의 금일 스냅샷을 합쳐 보여주며, 같은 거래일이 있으면 Redis 값을
우선한다.

구현 책임은 시장 상태를 분기하는 `DomesticStockDailyFlowRefreshService`, 금일 세 API를 조회하는
`DomesticStockIntradayDailyFlowService`, 확정 누락 구간을 DB에 저장하는
`DomesticStockFinalizedDailyFlowSyncService`, Redis 입출력을 담당하는 `DomesticStockDailyFlowCacheService`로
나뉜다. 현재가·재무 갱신 서비스는 이 흐름을 직접 구현하지 않고 일별 흐름 서비스에 위임한다.

통합 일봉 확정 뒤에는 DB 마지막 거래일의 다음 날부터 확정 거래일까지의 누락 구간만 조회하여 종목·거래일
기준으로 저장한다. 예를 들어 DB 마지막 저장일이 8월 7일이고 기대 최신 확정 거래일이 8월 10일이면
공매도·대차는 8월 8일부터 8월 10일까지만 요청한다. 투자자매매동향 API는 기준일 하나만 받으므로
8월 10일 기준 응답을 받은 뒤 8월 8일부터 8월 10일까지의 누락 행만 골라 저장한다. 장 마감 뒤 접속이
없었더라도 다음 거래일 첫 접속에서 동일한 누락
검사를 수행하므로 전 거래일 최종값이 DB에 보충된다. 확정 동기화가 끝나면 금일 Redis 스냅샷은 제거한다.
최초 공매도·대차 조회는 최근 3개월을 적재하고 이후 확정 행은 별도 보관 만료 없이 유지한다. 과거의 잘못된
응답 키로 대차 수량이 모두 비어 있는 행은 최근 3개월을 다시 조회해 복구할 수 있다. API 실패 시에는 마지막
정상 DB 이력을 유지한다.

투자자매매동향 API는 한 호출에서 제공하는 최근 일별 건수가 제한되어 있으므로 장기간 접속이 없었던 종목은
KIS가 반환한 최근 구간까지만 한 번에 복구될 수 있다. 더 긴 기간의 완전한 투자자 이력이 필요하면 연속조회나
별도 장기 이력 API 지원 여부를 확인해 보강해야 한다.
