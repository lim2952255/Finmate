# 주식/시세 Summary

마지막 정리일: 2026-07-06

이 문서는 FinMate의 주식 종목, 시세, 일봉 차트, 랭킹 기능 구현과 설계 의도를 정리한다.

## 구현 범위

현재 주식/시세 파트에서 구현한 기능은 다음과 같다.

- KIS 종목 마스터 파일 다운로드
- KOSPI, KOSDAQ, NASDAQ 종목 마스터 파싱
- `Stock`, 국내/해외 stock metadata 저장
- 종목 검색
- 종목 검색 결과 관심종목 우선 정렬
- 종목 검색/관심종목 10개 단위 페이징
- 관심 종목 등록/해제
- 관심 종목 목록 조회
- 종목 상세 화면
- 일봉 데이터 저장
- 종목 상세 진입 시 부족한 일봉 데이터 on-demand 동기화
- 국내/해외 일봉 API 호출 구조 분리
- SVG 기반 캔들 차트
- 거래량 bar
- MA5, MA20, MA60 이동평균선
- 현재가 기준선
- 하단 날짜 축
- 기간 내 최고가/최저가 마커
- KOSPI/KOSDAQ/NASDAQ 거래량 TOP10
- KOSPI/KOSDAQ/NASDAQ 거래대금 TOP10
- Redis 기반 랭킹 캐시
- 장중에만 랭킹 API 주기 호출
- KIS access token 메모리 캐시
- KIS 호출 간격 제어와 호출 제한 재시도
- 랭킹 갱신 실패 시 로그 기록 후 기존 캐시 조회 흐름 유지
- KIS WebSocket approval key 발급/메모리 캐시
- KIS WebSocket 연결, 재연결, PINGPONG 처리
- 국내 통합 실시간체결가(`H0UNCNT0`) 구독/해제
- NASDAQ 실시간지연체결가(`HDFSCNT0`) 구독/해제
- KOSPI/KOSDAQ 국내지수 실시간체결(`H0UPCNT0`) 구독/해제
- 실시간 구독자 수 기반 구독 관리와 지연 해제
- 실시간 수신 payload 메모리 최신값 저장
- 브라우저-서버 WebSocket(`/ws/stocks`) 연결
- 종목 상세 페이지 진입 시 실시간 종목 구독
- 종목 상세 페이지 이탈/WebSocket 종료 시 구독자 수 차감
- 실시간 체결가 기반 현재가/현재가 기준선 갱신
- 실시간 체결가 기반 SVG 캔들 양봉/음봉 오버레이
- 일봉 차트 캔들 hover tooltip
- 통화 기준 가격 포맷 적용(KRW 소수점 제거)

아직 구현하지 않은 기능:

- 주문
- 체결
- 보유 종목
- 실시간 시세 Redis Pub/Sub 연동
- 관심종목 목록 실시간 시세 반영
- KOSPI/KOSDAQ 지수 실시간 화면 반영
- 포트폴리오 평가
- 시장 휴장일 캘린더
- 랭킹 히스토리 DB 스냅샷

## 주요 엔티티

### Stock

`Stock`은 국내/해외 주식 종목의 공통 정보를 표현한다.

주요 필드:

- `symbol`
- `realtimeSymbol`
- `standardCode`
- `nameKo`
- `nameEn`
- `marketType`
- `countryCode`
- `exchangeCode`
- `currency`
- `securityType`
- `active`
- `tradable`
- `tradingHalted`
- `listedDate`
- `lastSyncedAt`

설계 포인트:

- 국내와 해외 종목을 하나의 `Stock` 엔티티로 통합한다.
- 공통 검색/화면/거래에 필요한 필드는 `Stock`에 둔다.
- 국내/해외 마스터 파일의 원본 부가 필드는 metadata 엔티티로 분리한다.
- `marketType + symbol` unique constraint로 같은 시장 내 중복 종목을 막는다.
- 국내 표준코드도 unique constraint를 둔다.

### DomesticStockMetadata / OverseasStockMetadata

마스터 파일의 상세 원본 필드를 보관한다.

설계 포인트:

- `Stock`에는 핵심 식별 정보만 둔다.
- 시장별로 다른 원본 필드는 metadata 엔티티에 분리한다.
- 이후 신규 KIS 필드나 시장별 부가 정보를 추가해도 `Stock`이 과도하게 커지지 않는다.

### FavoriteStock

사용자의 관심 종목을 표현한다.

주요 필드:

- `user`
- `stock`
- `createdAt`

설계 포인트:

- `user_id + stock_id` unique constraint로 중복 관심 종목을 막는다.
- 관심 여부는 종목 검색/관심 종목 목록에서 공통으로 사용한다.

### StockDailyPrice

종목별 일봉 데이터를 저장한다.

주요 필드:

- `stock`
- `tradeDate`
- `openPrice`
- `highPrice`
- `lowPrice`
- `closePrice`
- `accumulatedVolume`
- `accumulatedTradeAmount`
- `adjustedPrice`
- `modified`
- `lastFetchedAt`

설계 포인트:

- `stock_id + trade_date + adjusted_price` unique constraint로 같은 기준의 일봉 중복 저장을 막는다.
- 국내/해외 API 응답을 하나의 일봉 엔티티로 정규화한다.
- 거래대금은 일부 시장/API에서 없을 수 있으므로 nullable로 둔다.
- 수정주가 여부를 저장해 원주가/수정주가 기준 데이터를 구분할 수 있게 했다.

## KIS 값 파싱과 검증

### KisValueParser

KIS 응답과 마스터 파일에서 온 문자열 값을 Java 타입으로 바꾸는 공통 유틸이다.

담당하는 규칙:

- `yyyyMMdd` 날짜 파싱
- `00000000` 날짜를 null로 처리
- 콤마가 포함된 숫자 정규화
- `BigDecimal`, `Long` 변환
- `Y/N`, `Y/1` boolean 변환
- 코드값 trim + uppercase
- 필수 텍스트 검증

설계 포인트:

- 파싱 규칙이 서비스마다 흩어지지 않게 했다.
- 단, 종목 마스터 파일의 고정폭/탭 구조 파싱은 `StockMasterParser`에 남겼다.
- `KisValueParser`는 “잘라낸 문자열 값을 타입으로 변환하는 책임”만 가진다.

### RequiredValidator

`null` 또는 blank 문자열 필수값 검증을 공통 유틸로 분리했다.

사용처:

- KIS API 클라이언트 입력값 검증
- KIS 값 파서 내부 검증
- 주식 도메인 엔티티 필수값 검증
- 일봉 동기화 서비스 입력값 검증

설계 포인트:

- 파싱 책임이 아닌 공통 검증 책임이므로 parser가 아니라 `global.validation`에 둔다.
- 가격이 0 이상인지 같은 도메인 고유 검증은 각 도메인 엔티티 내부에 남긴다.

## KIS 공통 클라이언트

관련 클래스:

- `KisProperties`
- `KisAuthClient`
- `KisTokenService`
- `KisRestClient`
- `KisRateLimiter`
- `KisApiResponse`

설정:

```properties
finmate.kis.base-url
finmate.kis.app-key
finmate.kis.app-secret
finmate.kis.request-interval-millis
```

토큰 발급:

```text
KisTokenService.getAccessToken()
-> 사용 가능한 메모리 토큰이 있으면 재사용
-> 없거나 만료 5분 전이면 KisAuthClient.issueAccessToken()
-> POST {baseUrl}/oauth2/tokenP
-> access_token_token_expired 또는 expires_in 기준으로 만료 시각 계산
```

설계 포인트:

- access token은 `KisTokenService`가 메모리에 보관한다.
- `getAccessToken()`과 `clear()`는 `synchronized`로 보호한다.
- 토큰 만료 5분 전부터 새 토큰 발급 대상으로 본다.
- KIS 토큰 응답에 만료 시각이 없으면 `expires_in`, 그것도 없으면 23시간을 fallback으로 사용한다.
- 토큰 발급과 REST API 호출은 `HttpClient` connect timeout 10초, request timeout 30초를 사용한다.

REST 호출:

```text
KisRestClient.get(path, trId, params, responseType)
-> KIS 설정값 검증
-> access token 조회
-> Bearer authorization, appkey, appsecret, tr_id 헤더 구성
-> KisRateLimiter.waitTurn()
-> HTTP GET
-> HTTP status 검증
-> JSON을 KIS 응답 record로 역직렬화
-> rt_cd == "0" 검증
```

호출 제한 처리:

- 기본 호출 간격은 `finmate.kis.request-interval-millis`, 기본값 700ms다.
- `KisRateLimiter`가 마지막 KIS 요청 이후 경과 시간을 보고 필요한 만큼 대기한다.
- KIS 응답 body에 `EGW00201` 호출 제한 코드가 있으면 잠시 대기 후 최대 2회 재시도한다.

현재 한계:

- KIS 연결 타임아웃, 점검, 네트워크 차단은 현재 `RuntimeException`으로 감싸져 전파된다.
- 종목 상세의 on-demand 일봉 동기화 중 KIS 연결이 실패하면 별도 fallback 없이 요청 처리가 실패할 수 있다.
- 랭킹 스케줄러는 갱신 실패를 catch해서 로그를 남기고, 화면은 Redis 캐시 또는 빈 board를 읽는다.
- 아직 `KisApiUnavailableException` 같은 전용 예외와 `@ControllerAdvice` 기반 503 응답/전용 에러 화면은 없다.

## 종목 검색과 관심 종목

관련 클래스:

- `StockController`
- `StockService`
- `StockRepository`
- `FavoriteStock`
- `FavoriteStockRepository`
- `StockSearchPageInfo`
- `FavoriteStockPageInfo`

경로:

- 검색 화면: `/investments/stocks/search`
- 관심 종목 toggle: `POST /investments/stocks/favorite`
- 관심 종목 목록: `/investments/stocks/watchlist`

검색 정책:

- 검색어가 비어 있으면 빈 페이지를 반환한다.
- 검색 대상은 활성 종목(`active = true`)이다.
- `symbol`, `standardCode`, `nameKo`, `nameEn`을 대상으로 부분 검색한다.
- 검색 결과는 현재 사용자의 관심 종목을 먼저 보여주고, 그 다음 `symbol` 오름차순으로 정렬한다.
- 검색과 관심 종목 목록은 10개 단위로 페이징한다.
- 음수 페이지 요청은 `PaginationInfo.safePage()`로 0페이지로 보정한다.

관심 종목 정책:

- `user_id + stock_id` unique constraint로 중복 등록을 막는다.
- 이미 관심 종목이면 삭제하고, 없으면 새로 등록하는 toggle 방식이다.
- toggle 후에는 요청에서 받은 `redirectUrl`로 돌아간다.

## 종목 마스터 동기화

관련 클래스:

- `KisStockMasterFileClient`
- `StockMasterParser`
- `StockMasterApplyService`
- `StockMasterSyncService`

흐름:

```text
1. KIS 종목 마스터 zip 다운로드
2. 임시 디렉터리에 압축 해제
3. 국내는 고정폭, 해외는 탭 구분 형식으로 파싱
4. DTO 목록 생성
5. Stock과 metadata에 upsert
6. 최신 마스터에 없는 기존 종목은 inactive 처리
7. 임시 파일 삭제
```

스케줄:

- 국내 마스터: KOSPI/KOSDAQ 장 시작 전 동기화
- NASDAQ 마스터: 미국장 기준 장 시작 전 동기화

설계 포인트:

- 마스터 파일 형식 자체를 읽는 책임은 `StockMasterParser`가 가진다.
- DB 반영 책임은 `StockMasterApplyService`가 가진다.
- 다운로드/압축 해제 책임은 `KisStockMasterFileClient`가 가진다.

## 일봉 on-demand 동기화

관련 클래스:

- `StockDetailService`
- `StockDailyPriceSyncService`
- `KisStockDailyPriceClient`
- `StockDailyPriceRepository`

처리 흐름:

```text
1. 사용자가 종목 상세 페이지에 진입
2. 선택 기간과 예상 최신 거래일 계산
3. DB에 저장된 최신 일봉 확인
4. 일봉이 없으면 최근 3년치 조회
5. 일봉이 일부 있으면 저장된 최신 거래일 다음 날부터 부족한 구간만 조회
6. DB에서 화면 표시 기간만 다시 조회
7. StockDetailPageInfo로 차트 데이터를 계산해 화면에 전달
```

운영체제 개념과의 연결:

- 운영체제의 demand paging은 모든 페이지를 미리 메모리에 올리지 않고, 실제 접근이 발생했을 때 필요한 페이지를 적재한다.
- FinMate의 일봉 데이터도 모든 종목의 3년치 일봉을 미리 가져오지 않는다.
- 사용자가 특정 종목 상세에 접근하는 시점에만 해당 종목의 부족한 일봉 구간을 가져온다.
- 이미 DB에 있는 구간은 다시 호출하지 않고, 최신 일봉 이후 부족한 구간만 가져온다.
- 즉 “종목 상세 조회”를 page reference처럼 보고, “부족한 일봉 구간”을 page fault처럼 처리하는 on-demand 동기화 구조다.

장점:

- 초기 마스터 동기화 시 전체 종목 일봉을 가져오지 않아 API 호출량이 줄어든다.
- 사용자가 실제 조회한 종목부터 데이터가 채워진다.
- DB 저장량과 KIS API 사용량을 현실적인 수준으로 통제할 수 있다.

## 국내/해외 일봉 API 차이

### 국내 일봉

국내 API는 시작일과 종료일을 직접 받는다.

처리 방식:

```text
startDate ~ endDate
-> 120일 단위 chunk로 분할
-> 각 chunk를 KIS 국내 일봉 API로 조회
-> 없는 일봉만 저장
```

설계 이유:

- 너무 긴 기간을 한 번에 요청하면 응답이 커지고 API 실패 가능성이 높다.
- 120일 단위로 쪼개 호출하면 실패 범위와 응답 크기를 줄일 수 있다.

### 해외 일봉

해외 API는 시작일/종료일이 아니라 `BYMD` 기준일 하나를 받는다.

처리 방식:

```text
baseDate = endDate
-> BYMD=baseDate로 호출
-> 응답 중 요청 범위 안의 일봉만 저장
-> 응답에서 가장 오래된 거래일을 찾음
-> oldestTradeDate - 1일을 다음 baseDate로 설정
-> startDate 이전까지 반복
```

설계 포인트:

- 해외는 120일 chunk가 아니라 API가 반환하는 묶음을 기준으로 뒤로 이동한다.
- `baseDate`는 조회 시작일이 아니라 “이 날짜를 기준으로 과거 데이터를 달라”는 기준일에 가깝다.
- 저장 시에는 다시 `startDate <= tradeDate <= endDate` 조건으로 필터링한다.

## 종목 상세 차트

관련 클래스:

- `StockDetailPageInfo`
- `StockDailyPriceCandle`
- `StockMovingAverageLine`
- `StockPriceAxisLabel`
- `StockDateAxisLabel`
- `StockPriceMarker`
- `investments/stocks/detail.html`

표시 요소:

- 최신 종가
- 최신 거래일
- 캔들
- 거래량 bar
- MA5
- MA20
- MA60
- 현재가 기준선
- 가격축 라벨
- 날짜축 라벨
- 기간 내 최고가
- 기간 내 최저가
- 기간/시장/표시 일수/최고/최저 사이드 정보

설계 포인트:

- Chart.js 같은 클라이언트 라이브러리 대신 서버에서 SVG 좌표를 계산한다.
- Thymeleaf는 계산된 좌표 DTO를 기반으로 SVG를 렌더링한다.
- 차트 좌표 계산 책임은 `StockDetailPageInfo`와 관련 DTO에 둔다.
- 템플릿은 계산 결과를 그리는 역할에 집중한다.

## 거래량/거래대금 TOP10

관련 클래스:

- `KisStockRankingClient`
- `StockMarketMoverService`
- `StockRankingCacheService`
- `StockMarketSessionService`
- `StockRankingBoard`
- `StockRankingItem`
- `StockRankingType`
- `investments/stocks/market-movers.html`

경로:

- 화면: `/investments/stocks/market-movers`
- JSON: `/investments/stocks/market-movers/data`

지원 범위:

- KOSPI 거래대금 TOP10
- KOSPI 거래량 TOP10
- KOSDAQ 거래대금 TOP10
- KOSDAQ 거래량 TOP10
- NASDAQ 거래대금 TOP10
- NASDAQ 거래량 TOP10

구조:

```text
@Scheduled
-> 장중 여부 확인
-> KIS 랭킹 API 호출
-> StockRankingBoard로 변환
-> Redis 저장

Controller
-> Redis에서 랭킹 board 조회
-> 화면 렌더링 또는 JSON 응답

화면
-> 최초 서버 렌더링
-> 10초마다 우리 서버 JSON API polling
```

중요한 설계 판단:

- 화면 요청이 KIS API를 직접 호출하지 않는다.
- KIS API 호출은 서버 스케줄러에서만 수행한다.
- 화면은 Redis 캐시만 읽는다.
- DB에는 실시간 TOP10 값을 저장하지 않는다.

DB에 저장하지 않는 이유:

- 거래량/거래대금 TOP10은 장중에 계속 변하는 휘발성 시세 데이터다.
- 5~10초마다 바뀌는 값을 모두 DB에 저장하면 저장량이 빠르게 늘고 활용 가치가 낮다.
- 현재 화면 표시 목적에는 Redis 캐시가 적합하다.

나중에 DB 저장을 고려할 경우:

- 장 마감 후 최종 TOP10 스냅샷만 저장하는 방식이 적합하다.
- 예: `stock_trade_amount_ranking_snapshot`

## 장중/장마감 처리

관련 클래스:

- `StockMarketSessionService`

정책:

```text
국내 KOSPI/KOSDAQ
-> Asia/Seoul
-> 09:00 ~ 15:30 장중

NASDAQ
-> America/New_York
-> 09:30 ~ 16:00 장중
```

랭킹 갱신 정책:

- 장중에는 설정된 주기마다 KIS API 호출
- 장 마감 직후 2분 동안은 최종값 확보를 위해 갱신 허용
- 그 이후에는 KIS API를 호출하지 않음
- Redis TTL은 장중 짧게, 장마감 이후 길게 유지

설정:

```properties
finmate.stock-ranking.refresh-interval-millis
finmate.stock-ranking.initial-delay-millis
finmate.stock-ranking.open-cache-ttl-seconds
finmate.stock-ranking.closed-cache-ttl-seconds
```

## Redis 캐시

관련 클래스:

- `StockRankingCacheService`

Redis key:

```text
stock:ranking:{marketType}:{rankingType}
```

예시:

```text
stock:ranking:KOSPI:TRADE_AMOUNT
stock:ranking:KOSDAQ:VOLUME
stock:ranking:NASDAQ:TRADE_AMOUNT
```

설계 포인트:

- `StringRedisTemplate`으로 JSON 문자열을 저장한다.
- Redis 저장/조회 실패 시 화면 전체가 깨지지 않도록 빈 board를 반환한다.
- Redis repository는 사용하지 않으므로 `spring.data.redis.repositories.enabled=false`로 비활성화했다.

## 화면

주식 관련 화면:

- `/investments/stocks/search`: 종목 검색
- `/investments/stocks/watchlist`: 관심 종목
- `/investments/stocks/detail`: 종목 상세/차트
- `/investments/stocks/market-movers`: 거래량/거래대금 TOP10

종목 상세 화면:

- 종목 정보 요약
- 기간 선택
- 일봉 차트
- 최근 일봉 테이블

랭킹 화면:

- 시장별/랭킹타입별 board
- 현재가
- 등락률
- 거래량
- 거래대금
- 종목 상세 링크
- 10초마다 JSON polling

## 현재 보완점

- KIS 외부 API 장애/점검 시 전용 예외와 503 응답/전용 에러 화면 도입
- 종목 상세 on-demand 동기화 실패 시 기존 DB 일봉 fallback 표시
- KIS 랭킹 API 응답 필드명 실계좌/모의계좌 환경별 검증
- 국내 KOSPI/KOSDAQ 랭킹 API의 시장 코드 실환경 검증
- 한국/미국 휴장일 캘린더 반영
- Redis 장애 시 마지막 메모리 캐시 fallback 검토
- 장 마감 최종 TOP10 DB 스냅샷 필요 여부 검토
- KIS API 호출 실패 알림/운영 로그 보강
- 차트 tooltip/crosshair 같은 인터랙션 추가 검토
- 실시간 체결 WebSocket 도입 여부 검토
