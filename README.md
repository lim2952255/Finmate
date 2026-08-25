# FinMate

> 차트를 읽고, 투자 지표를 이해하고, 실제 시장 데이터 위에서 안전하게 연습하는 초보 투자자용 금융 학습·모의투자 플랫폼

FinMate는 일반 은행 계좌와 모의 증권 계좌를 연결해 **투자 학습 → 시장 관찰 → 종목 분석 → 모의 주문 → 포트폴리오 복기**를 하나의 경험으로 제공합니다.

한국투자증권(KIS) Open API의 REST·WebSocket 시세, NAVER 뉴스 검색, Google·Kakao·Naver 소셜 로그인을 연동했으며, 금융 데이터는 `BigDecimal`, 트랜잭션, 비관적 락과 동시성 테스트를 중심으로 다룹니다.

> KIS는 시세·종목·시장 데이터의 출처로 사용합니다. 주문과 체결은 실제 증권사로 전송하지 않고 FinMate DB 안에서 처리되는 **모의 거래**입니다.

![FinMate 종목 상세 캔들 차트](docs/images/readme/stock-candlestick-chart.png)

## 프로젝트 개요

### 시작하게 된 이유

이 프로젝트는 제가 처음 MTS와 HTS를 사용하면서 느꼈던 어려움에서 출발했습니다. 화면에는 캔들, 이동평균선, 거래량과 수많은 숫자가 한꺼번에 나타났지만 **차트의 봉 하나가 무엇을 의미하는지**, **PER·PBR·ROE 같은 지표를 어떻게 해석해야 하는지**, 그리고 **그 정보를 실제 투자 판단 과정에서 어떻게 연결해야 하는지** 이해하기 어려웠습니다.

용어의 정의를 검색해도 각각의 설명은 흩어져 있었고, 실제 종목 화면에서는 다시 숫자만 보이는 경우가 많았습니다. 개념을 배웠더라도 곧바로 실제 돈을 투자하며 익히기에는 부담이 컸습니다.

FinMate는 이 간격을 줄이기 위해 만들었습니다. 초보 투자자가 어려운 금융 용어와 차트를 쉬운 설명으로 먼저 이해하고, 실제 시장 데이터를 관찰한 다음, **실제 돈이 아닌 가상 자산으로 주문과 체결을 경험하며 주식 투자 과정에 익숙해지는 것**이 프로젝트의 목표입니다.

### 지향하는 경험

FinMate는 특정 종목의 매수·매도를 추천하거나 수익을 예측하는 서비스가 아닙니다. 사용자가 스스로 정보를 읽고 판단하는 방법을 익히는 **투자 학습 샌드박스**를 지향합니다.

- 차트의 시가·고가·저가·종가와 거래량을 직접 움직이며 읽습니다.
- PER·EPS·PBR·BPS·ROE를 쉬운 생활 비유, 정확한 계산식, 실제 국내 종목 값으로 연결합니다.
- 재무제표의 숫자를 표에만 두지 않고 분기 흐름과 성장률 그래프로 비교합니다.
- 뉴스, 투자자 수급, 공매도·대차와 시장 지표를 함께 보며 종목을 여러 관점에서 관찰합니다.
- 시장가·지정가·예약 주문을 가상 자산으로 실행하고, 체결 결과와 포트폴리오 손익을 복기합니다.
- 일반 계좌에서 투자 예수금으로 돈이 이동하고 주문에 잠겼다가 체결·취소되는 전체 자산 흐름을 경험합니다.

## FinMate의 전체 사용자 흐름

| 단계 | 사용자가 하는 일 | FinMate가 제공하는 경험 |
|---|---|---|
| 1. 시작 | 로컬 또는 소셜 로그인 후 일반·투자 계좌 구성 | 다중 통화 계좌, 대표 계좌, 계좌이체와 거래 원장 |
| 2. 학습 | 관심 있는 투자 개념 검색 | 17개 학습 카드와 종목 상세 재무 개념 카드 |
| 3. 관찰 | 시장과 종목 탐색 | 국내·미국 종목 검색, 시장 지표, 랭킹, 관심 종목 |
| 4. 분석 | 차트·재무·수급·뉴스 확인 | KIS 시세, 캔들 차트, 재무 그래프, 키워드 뉴스 Top 10 |
| 5. 연습 | 가상 자산으로 주문 | 시장가·지정가·예약 주문과 실시간 가격 기반 모의 체결 |
| 6. 복기 | 주문 결과와 보유 자산 확인 | 체결 내역, 평가손익, 통화·시장·업종별 포트폴리오 |

이 흐름에서 계좌, 학습, 분석, 주문과 포트폴리오는 분리된 데모 기능이 아닙니다. 일반 계좌의 자금이 투자 계좌 예수금으로 이동하고, 주문 시 예수금이나 보유 수량으로 잠기며, 체결 후 보유 종목과 포트폴리오 평가에 반영되는 하나의 연결된 도메인으로 동작합니다.

## 설계 원칙

- **교육 우선:** 숫자를 많이 보여주는 것보다 숫자의 의미, 계산 방식, 기준 시점과 해석의 한계를 함께 전달합니다.
- **판단 보조:** 낮은 PER이나 높은 ROE를 곧바로 매수 신호로 표현하지 않고 비교 기준과 주의점을 제공합니다.
- **현실적인 연습:** 실제 KIS 시장 데이터를 사용하되 주문은 내부 모의 거래로 처리해 금전적 위험 없이 연습할 수 있게 합니다.
- **연결된 자산 흐름:** 은행형 계좌, 투자 예수금, 주문 잠금, 체결 원장과 포트폴리오를 하나의 흐름으로 관리합니다.
- **금융 정합성:** 금액과 수량은 `BigDecimal`로 계산하고 트랜잭션, 비관적 락, 보존식과 동시성 테스트로 보호합니다.
- **필요한 만큼 수집:** 외부 API 데이터를 무조건 쌓지 않고 화면에 필요한 범위, 시장 세션과 캐시 신선도를 기준으로 동기화합니다.

## 핵심 구현과 차별점

다음은 전체 사용자 흐름을 구현하면서 특히 중요하게 다룬 제품·기술적 선택입니다.

### 1. 필요한 순간에만 KIS 캔들 데이터를 동기화

전 종목의 방대한 차트 데이터를 주기적으로 수집하지 않습니다. 사용자가 종목과 봉 주기를 선택했을 때 저장된 범위를 먼저 확인하고, **부족하거나 오래된 구간만 KIS REST API에서 온디맨드로 가져옵니다.**

```text
차트 조회
  → DB 또는 Redis/JVM 캐시 확인
  → 시장 세션을 고려해 신선도 판단
  → 부족한 구간만 KIS REST 호출
  → 원본 데이터 저장
  → 서버 집계 및 응답
  → 현재 미완성 봉은 KIS WebSocket 체결가로 갱신
```

- 일봉은 최근 3년, 주봉은 최근 10년, 월봉·연봉은 상장 이후 범위를 화면 조회 시 증분 동기화합니다.
- 1분봉 원본은 Redis에 기본 3일간 캐시하고 Redis 장애 시 JVM 캐시로 fallback합니다.
- 3·5·15분봉은 KIS를 다시 호출하지 않고 저장된 1분봉의 OHLCV를 서버에서 집계합니다.
- 종목별 동시 캐시 미스는 하나의 갱신으로 합쳐 불필요한 중복 호출을 줄입니다.
- 차트의 이동평균선, 사용자 가로선, 기간 최고·최저, 거래량과 실시간 현재 봉을 한 화면에서 탐색할 수 있습니다.

### 2. 락 순서를 고정한 안전한 계좌이체

양방향 이체가 동시에 실행되면 두 트랜잭션이 서로의 계좌 락을 기다릴 수 있습니다. FinMate는 요청의 출금·입금 방향과 무관하게, 계좌번호로 찾은 두 계좌의 **DB `Account.id`가 작은 행부터 `PESSIMISTIC_WRITE` 락을 획득**하도록 순서를 고정합니다.

```text
A → B 이체 ─┐
             ├─ min(Account.id) → max(Account.id) 순서로 잠금
B → A 이체 ─┘
```

잔액 변경, `Transfer` 원장 1건, 출금·입금 `AccountTransaction` 2건은 하나의 트랜잭션에서 함께 커밋하거나 롤백됩니다. MySQL 동시성 테스트에서는 반대 방향 이체를 동시에 실행해 완료 여부, 잔액 비음수, 총액 보존과 원장 건수를 검증합니다.

### 3. 숫자를 나열하지 않고 이해시키는 투자 학습

PER 같은 지표를 사전식 정의로만 보여주지 않습니다. 하나의 **빵집을 공동 소유하는 상황**을 EPS → PER, BPS → PBR, 자기자본 → ROE까지 이어서 설명하고, 국내 종목 상세에서는 사용자가 보고 있는 종목의 실제 값과 계산식을 함께 표시합니다.

<p align="center">
  <img src="docs/images/readme/per-concept-card.png" alt="PER 빵집 비유와 실제 종목 계산 카드" width="88%" />
</p>

- 별도 투자 학습 화면에서 17개의 핵심 개념을 계좌·거래, 위험관리·포트폴리오, 펀드·ETF, 시장 안전장치, 파생상품·공매도 범주로 제공합니다.
- 비유 뒤에는 정확한 정의, 계산식, 기준 시점과 해석할 때의 주의점을 함께 둡니다.
- 낮은 PER처럼 하나의 수치를 곧바로 “좋음”이나 “매수”로 판정하지 않습니다.
- 분기 매출액·영업이익·당기순이익, 성장률, TTM과 런레이트를 그래프로 시각화합니다.

### 4. 단순 최신순이 아닌 키워드 기반 뉴스 Top 10

NAVER API HUB 뉴스 검색에서 종목별·시장 주제별 후보 40건을 가져온 뒤, 투자 판단에 중요한 제목 키워드의 일치 개수로 점수를 계산합니다.

```text
제목 점수 = 제목에 포함된 주요 키워드 수

한국 날짜 최신순
  → 같은 날짜 안에서 키워드 점수 내림차순
  → 발행 시각 내림차순
  → NAVER 원본 순서
  → Top 10
```

종목 뉴스에는 실적, 매출, 영업이익, 순이익, 애널리스트, 외국인, 기관, 순매수, 수주, 계약, 배당, 자사주, 급등·급락 등의 키워드를 사용합니다. 시장 리포트는 KOSPI, KOSDAQ, NASDAQ, S&P 500, 금리, 환율마다 별도 검색어와 키워드 집합을 적용합니다.

최종 Top 10은 MySQL에 기본 6시간 캐시하고, 같은 종목이나 주제의 동시 갱신 요청은 하나로 합쳐 NAVER API 중복 호출을 줄입니다.

### 5. 학습에서 끝나지 않는 연결형 모의투자

FinMate의 학습 기능은 별도의 읽기 자료로 끝나지 않습니다. 학습한 개념을 종목 상세의 실제 데이터에서 확인하고, 같은 종목을 가상으로 주문한 뒤 포트폴리오에서 결과를 다시 살펴볼 수 있습니다.

```text
일반 계좌
  → 투자 계좌로 예수금 입금
  → 종목 분석과 주문 조건 설정
  → 매수 예수금 / 매도 수량 잠금
  → KIS 실시간 가격을 입력으로 내부 모의 체결
  → 체결 원장과 보유 종목 갱신
  → 포트폴리오 평가손익·업종 비중 복기
```

- 국내 KOSPI·KOSDAQ과 미국 NASDAQ 종목을 하나의 검색·포트폴리오 경험으로 제공합니다.
- 시장가, 지정가와 조건 충족 시 일반 주문으로 전환되는 예약 주문을 지원합니다.
- 취소·만료·체결 시 잠긴 예수금과 보유 수량을 함께 정산합니다.
- 포트폴리오는 통화별 손익뿐 아니라 업종별 매입 비중을 제공하고, 실시간 가격이 없으면 최신 종가를 사용합니다.
- 주문과 거래 내역에는 주문 조건, 상태, 평균 체결가와 자산 변경 전후 값을 남겨 결과를 추적할 수 있습니다.

## 주요 화면

<table>
  <tr>
    <td width="50%" align="center"><strong>투자 개념 학습</strong></td>
    <td width="50%" align="center"><strong>분기 재무 분석</strong></td>
  </tr>
  <tr>
    <td><img src="docs/images/readme/investment-learning.png" alt="투자 개념 학습 카드 목록" /></td>
    <td><img src="docs/images/readme/financial-analysis.png" alt="매출액 영업이익 당기순이익 그래프" /></td>
  </tr>
  <tr>
    <td>검색과 주제 필터로 17개 핵심 개념 탐색</td>
    <td>분기 실적, 성장 속도, TTM·런레이트 시각화</td>
  </tr>
</table>

<table>
  <tr>
    <td width="50%" align="center"><strong>종목별 뉴스 랭킹</strong></td>
    <td width="50%" align="center"><strong>시장 리포트</strong></td>
  </tr>
  <tr>
    <td><img src="docs/images/readme/stock-news-ranking.png" alt="종목 관련 뉴스 Top 10" /></td>
    <td><img src="docs/images/readme/market-report.png" alt="시장 주제별 뉴스 리포트" /></td>
  </tr>
  <tr>
    <td>종목 핵심 키워드 점수 기반 Top 10</td>
    <td>6개 시장 주제별 키워드 랭킹</td>
  </tr>
</table>

<table>
  <tr>
    <td width="50%" align="center"><strong>통합 포트폴리오</strong></td>
    <td width="50%" align="center"><strong>주문·체결 내역</strong></td>
  </tr>
  <tr>
    <td><img src="docs/images/readme/portfolio.png" alt="국내 해외 통합 포트폴리오" /></td>
    <td><img src="docs/images/readme/orders.png" alt="시장가 지정가 주문과 체결 내역" /></td>
  </tr>
  <tr>
    <td>통화·시장·업종별 평가금액과 손익</td>
    <td>시장가·지정가·예약 주문의 상태와 평균 체결가</td>
  </tr>
</table>

<details>
<summary><strong>계좌 거래 원장 화면 더 보기</strong></summary>

![계좌 입출금과 이체 거래 원장](docs/images/readme/account-transactions.png)

</details>

## 구현 기능

| 영역 | 기능 |
|---|---|
| 인증 | 로컬 회원가입·로그인, BCrypt, HTTP Session, Google·Kakao OIDC, Naver OAuth2 |
| 일반 계좌 | 다중 통화 계좌, 대표 계좌, 입출금·계좌이체, 일일 이체 한도, 거래 원장 |
| 투자 계좌 | 증권계좌, 통화별 예수금, 일반↔투자 계좌 자금 이동, KRW/USD 환전 |
| 종목 탐색 | KOSPI·KOSDAQ·NASDAQ 마스터 동기화, 종목·업종 검색, 관심 종목 |
| 종목 분석 | 일·주·월·연·분봉 차트, 이동평균선, 사용자 가로선, 시세·재무·투자자 수급·공매도·대차 |
| 학습 | 17개 일반 투자 학습 카드, 국내 종목 상세의 빵집 비유·실제 값·계산식·해석 주의점 |
| 뉴스 | NAVER 후보 40건, 제목 키워드 스코어링, 종목·시장 주제별 Top 10, DB 캐시 |
| 모의 투자 | 시장가·지정가·예약 주문, 취소·만료, 실시간 가격 기반 체결, 예수금·수량 잠금 |
| 포트폴리오 | 국내·해외 보유 종목, 통화·업종별 비중, 실시간/최근 종가 평가가격 fallback |
| 실시간 | KIS WebSocket 지연 구독, 브라우저 raw WebSocket, 종목 체결가·호가·국내 지수·채팅 |
| 시장 데이터 | KOSPI·KOSDAQ·NASDAQ·S&P 500·금리·USD/KRW, 거래량·거래대금 랭킹 |
| 종목 커뮤니티 | 종목별 실시간 채팅, 이전 기록 조회, 답글, 본인 메시지 수정·소프트 삭제 |
| 품질 검증 | 금융 단위·통합·동시성 테스트, MySQL Testcontainers, GitHub Actions CI |

### 소셜 로그인

Google과 Kakao는 검증된 OIDC `sub`, Naver는 OAuth2 사용자 정보의 `response.id`를 공급자 식별자로 사용합니다. `provider + subject`로 로컬 사용자와 연결하고 인증 상태는 Spring Security의 HTTP Session에 보존합니다. 공급자의 비밀번호와 액세스·리프레시 토큰은 FinMate DB에 저장하지 않습니다.

### 모의 주문과 자산 정합성

- 매수 주문은 통화별 예수금을 `available`에서 `locked`로 이동합니다.
- 매도 주문은 보유 수량 중 주문 수량을 잠급니다.
- 체결·취소·만료는 대상 주문 행을 비관적 락으로 직렬화하며 하나의 종료 경로만 자산을 소비하거나 해제합니다.
- 금액, 가격, 잔액과 수량 계산은 `BigDecimal` 및 통화별 scale·rounding 정책을 사용합니다.
- 실시간 가격이 없을 때 포트폴리오는 DB의 최신 일봉 종가를 우선하고, 부족하면 KIS REST로 보충합니다.

## 아키텍처

```text
React 19 + Vite
  ├─ JSON API ───────────────→ Spring MVC → Service → JPA → MySQL 8.4
  ├─ /ws/stocks ─────────────→ StockRealtimeWebSocketHandler
  ├─ /ws/market-data ────────→ MarketRealtimeWebSocketHandler
  └─ /ws/chat ───────────────→ StockChatWebSocketHandler → MySQL

KIS REST API
  ├─ 종목·업종 마스터, 일·주·월·연·분봉
  ├─ 국내 시세·재무·수급·공매도·대차
  └─ 시장 지표·환율·종목 랭킹 → MySQL / Redis TTL 캐시

KIS WebSocket
  → 지연 연결·목적별 참조 수 관리
  → 종목 체결가·호가 / 국내 지수
  ├─ 브라우저 실시간 전파
  └─ 활성 모의 주문·예약 주문 체결 판단

NAVER News API
  → 후보 40건 → 제목 키워드 스코어링 → Top 10 → MySQL 캐시
```

백엔드는 `controller → service → repository → domain` 계층을 따르며, 외부 연동은 `infra.kis`, `infra.naver` 경계에 둡니다. 잔액·원장·주문 상태 변경은 서비스 트랜잭션 안에서 처리하고, 외부 API 호출 결과를 실제 금융기관의 주문 체결로 표현하지 않습니다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Backend | Java 17, Spring Boot 3.5.15, Spring MVC, Spring Data JPA, Bean Validation |
| Security | Spring Security, OAuth2 Client, OIDC, BCrypt, HTTP Session, CSRF |
| Frontend | React 19.2.8, React Router 7.18.2, Vite 8.2.1, ESLint 10.8.1 |
| Database | MySQL 8.4, Hibernate |
| Cache | Redis 7.2, `StringRedisTemplate`, JVM fallback cache |
| Realtime | Spring WebSocket, JDK `HttpClient` WebSocket |
| External API | 한국투자증권(KIS) Open API, NAVER API HUB News API |
| Build & Test | Gradle Wrapper, npm, JUnit 5, Spring Boot Test, Testcontainers |
| Infrastructure | Docker Compose, GitHub Actions CI |

## 주요 경로

| 화면 | 경로 |
|---|---|
| 홈·로그인 | `/home`, `/login`, `/signup` |
| 일반 계좌 | `/accounts`, `/accounts/transfer`, `/accounts/transactions` |
| 투자 계좌·환전 | `/investments`, `/investments/transfer`, `/investments/currency-exchange` |
| 종목 검색·상세 | `/investments/stocks/search`, `/investments/stocks/detail?stockId={id}` |
| 종목 랭킹 | `/investments/stocks/market-movers` |
| 포트폴리오·주문 내역 | `/investments/portfolio`, `/investments/orders` |
| 시장 리포트 | `/investments/reports` |
| 투자 학습 | `/investment-learning` |

## 로컬 실행

### 요구 사항

- JDK 17
- Node.js 20.19+ 또는 22.12+와 npm
- Docker 및 Docker Compose
- KIS·OAuth·NAVER 뉴스 기능을 사용할 경우 각 서비스의 유효한 자격 증명

환경변수 전체 예시와 OAuth callback 설정은 [개발 가이드](docs/DEVELOPMENT_GUIDE.md)를 참고하세요. 비밀값은 루트 `.env`에 두며 저장소에 커밋하지 않습니다.

```bash
# MySQL과 Redis
docker compose up -d mysql redis

# Spring API 서버
./gradlew bootRun

# 별도 터미널: React 개발 서버
cd frontend
npm ci
npm run dev
```

브라우저는 `http://localhost:5173`으로 접속합니다. Vite가 React 화면과 정적 자산을 제공하고 `/api`, 인증 경로와 WebSocket 요청만 `http://localhost:8080`의 Spring 서버로 프록시합니다. Gradle은 프런트엔드를 빌드하거나 실행 JAR에 포함하지 않습니다.

## 테스트와 CI

```bash
# Frontend
cd frontend
npm run lint
npm run build

# Backend 전체 회귀 테스트
cd ..
./gradlew test

# 전체 빌드와 실행 JAR
./gradlew clean build
./gradlew bootJar
```

MySQL 통합·동시성 테스트는 Testcontainers의 MySQL 8.4를 사용합니다. GitHub Actions는 `main` 대상 pull request와 push에서 전체 테스트를 실행하고 JUnit 리포트를 artifact로 보관합니다.

## 현재 범위와 한계

- 실제 은행 송금이나 실제 증권사 주문을 수행하지 않는 학습·모의 거래 프로젝트입니다.
- 부분 체결 상태 모델은 있지만 현재 체결 로직은 남은 수량 전체 체결 기준입니다.
- 거래시간은 주말과 시장 세션을 반영하지만 공휴일·조기폐장 캘린더는 아직 적용하지 않습니다.
- KIS REST token, WebSocket 연결과 최신 실시간 payload는 단일 JVM 메모리 기준입니다.
- JPA 스키마는 `ddl-auto=update`를 사용하며 Flyway/Liquibase는 도입하지 않았습니다.
- 상세한 금융 불변식과 알려진 동시성 위험은 [금융 불변식 문서](docs/FINANCIAL_INVARIANTS.md)를 기준으로 관리합니다.

## 문서

- [프로젝트 개요](docs/PROJECT_OVERVIEW.md)
- [아키텍처](docs/ARCHITECTURE.md)
- [도메인 모델](docs/DOMAIN_MODEL.md)
- [금융 불변식](docs/FINANCIAL_INVARIANTS.md)
- [주식 거래 흐름](docs/TRADING_FLOW.md)
- [KIS 연동](docs/KIS_INTEGRATION.md)
- [종목 상세 재무 학습 카드](docs/STOCK_FINANCIAL_DETAIL.md)
- [개발 가이드](docs/DEVELOPMENT_GUIDE.md)
