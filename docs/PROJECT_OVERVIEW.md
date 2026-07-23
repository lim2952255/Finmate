# FinMate 프로젝트 개요

## 1. 프로젝트 목적

FinMate는 일반 은행 계좌와 모의 투자 계좌를 한 애플리케이션에서 관리하는 서버 렌더링 금융 포트폴리오 프로젝트다. 현재 소스가 제공하는 핵심 범위는 다음과 같다.

- 세션 기반 회원가입·로그인·로그아웃
- 다중 통화 일반 계좌 개설, 대표 계좌, 이체 한도, 계좌이체 및 거래 내역
- 투자 계좌 개설, 통화별 예수금, 일반 계좌와 투자 계좌 사이의 자금 이동, 증권계좌 안 KRW/USD 환전
- 국내·미국 종목 마스터, 업종명 표시, 시장별 종목/업종 검색, 관심 종목, 차트, 거래량·거래대금 순위
- 시장가·지정가·예약 모의 주문, 체결, 예수금·보유 종목 정산
- 한국투자증권(KIS) REST API와 WebSocket을 이용한 시세 수집

실제 증권사 계좌에 주문을 전송하는 코드는 없다. `StockOrder`와 `StockTradeTransaction`은 FinMate DB 안에서 처리되는 모의 거래다.

문서 탐색은 다음 기준을 따른다.

- 패키지·계층·트랜잭션 경계: [아키텍처](ARCHITECTURE.md)
- 엔티티와 관계: [도메인 모델](DOMAIN_MODEL.md)
- 잔액·수량·잠금·원장 정합성 계약: [금융 불변식](FINANCIAL_INVARIANTS.md)
- 주문 접수·체결·취소 흐름: [주식 거래 흐름](TRADING_FLOW.md)
- KIS REST·WebSocket 경계: [KIS 연동](KIS_INTEGRATION.md)
- 로컬 실행·설정·검증: [개발 가이드](DEVELOPMENT_GUIDE.md)

> README에 JWT, FDS, 뉴스, OpenAI, React, Spring Batch, QueryDSL, AWS 배포 등이 목표로 기재되어 있으나 현재 `build.gradle`과 `src/main/java`에서 해당 구현은 확인되지 않는다. **현재 구현되지 않음**.

## 2. 실제 기술 스택

| 구분 | 코드에서 확인된 기술 |
|---|---|
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 3.5.15 |
| 웹 | Spring MVC, Thymeleaf, Bean Validation |
| 보안 | Spring Security 의존성, BCrypt, 자체 MVC 인터셉터와 HTTP Session 로그인 |
| 영속성 | Spring Data JPA, Hibernate, MySQL Connector/J |
| DB | MySQL 8.4 (`docker-compose.yml`) |
| 캐시 | Redis 7.2, `StringRedisTemplate` |
| 실시간 | Spring WebSocket(브라우저 연결), JDK `HttpClient` WebSocket(KIS 연결) |
| 외부 통신 | JDK `java.net.http.HttpClient` |
| 뷰 | Thymeleaf 템플릿과 정적 CSS |
| 빌드·테스트 | Gradle Wrapper, JUnit 5, Spring Boot Test |
| 보조 | Lombok, Docker Compose |

`README.md`의 Spring Boot 버전 및 일부 기술 목록은 실제 빌드와 다르므로 이 문서는 `build.gradle`을 기준으로 한다.

## 3. 주요 기능

### 사용자와 인증

`LoginController`와 `UserService`가 회원가입·로그인을 처리하고 `SessionUser`를 HTTP Session에 저장한다. `LoginInterceptor`가 공개 경로 외 요청을 검사한다. 비밀번호는 `BCryptPasswordEncoder`로 암호화된다.

### 일반 계좌

`AccountService`가 계좌 개설, 대표 계좌 설정, 통화별 잔액 집계, 이체 한도 변경, 거래 내역 조회와 계좌이체를 담당한다. 계좌이체는 출금·입금 계좌의 잔액 변경, `Transfer` 1건, 양쪽 `AccountTransaction` 2건을 같은 트랜잭션에서 저장한다.

### 투자 계좌와 예수금

`Investment`는 사용자 소유 투자 계좌이고, `InvestmentCashBalance`가 계좌별·통화별 사용 가능 예수금과 주문 잠금 예수금을 관리한다. `InvestmentService`가 일반 계좌와 투자 계좌 사이의 자금 이동 및 양쪽 거래 내역을 같은 트랜잭션에서 처리하고, `InvestmentCurrencyExchangeService`가 증권계좌 안 KRW/USD 환전과 환전 내역 저장을 처리한다.

### 종목과 시장 데이터

종목 마스터와 국내 업종코드 마스터는 평일 스케줄러가 파일을 내려받아 DB에 반영한다. 해외 업종코드는 종목 상세·목록·포트폴리오 화면에서 필요한 거래소 코드 목록이 DB에 없을 때 KIS REST API로 조회해 DB에 캐시한다. 검색 화면은 종목명/종목코드 기준 검색과 업종명/업종코드 기준 검색을 분리하고, 전체·KOSPI·KOSDAQ·NASDAQ 시장 필터를 함께 적용한다. 국내 종목은 소·중·대 업종 중 가장 세부적인 유효 업종명을 표시하고, 포트폴리오는 업종별 매입금액 비중을 통화별로 계산하되 해외 종목은 거래소별 업종 체계가 다르므로 거래소 그룹별로 분리 집계한다. 포트폴리오 평가손익은 실시간 시세가 들어오면 실시간 가격을 사용하고, 초기 표시나 장마감처럼 실시간 체결가가 없을 때는 최신 일봉 종가를 KIS REST API로 보충해 정적 fallback 가격으로 사용한다. 일봉과 환율·해외 지수 일봉은 화면 조회 시 부족한 기간을 KIS REST API에서 가져와 DB에 보충한다. 국내 KOSPI/KOSDAQ 지수 상세 화면은 KIS WebSocket 실시간 지수 체결을 구독하고, 환율·해외 지수 실시간 값은 1분 스케줄러가 KIS REST API에서 갱신해 Redis에 TTL 캐시한다. 거래량·거래대금 TOP 10도 스케줄러가 KIS에서 갱신하고 Redis에 TTL 캐시한다.

### 모의 주식 거래

매수 주문은 예수금을, 매도 주문은 보유 수량을 먼저 잠근다. 실시간 체결가·호가가 조건을 만족하면 예수금, 보유 수량, 주문 상태와 체결 기록을 하나의 트랜잭션으로 갱신한다. 예약 주문은 조건 충족 시 일반 주문으로 전환된다.

이 절은 기능 개요일 뿐 정합성 계약을 정의하지 않는다. 자산 보존, 원장 원자성, 종료 상태 경합의 기준은 [금융 불변식](FINANCIAL_INVARIANTS.md)을 따른다.

## 4. 전체 시스템 흐름

```text
브라우저
  ├─ HTTP 요청 ─> Controller ─> Service ─> Spring Data JPA ─> MySQL
  └─ /ws/stocks WebSocket
         └─ StockRealtimeWebSocketHandler
              └─ StockRealtimeClientSessionService
                   └─ StockRealtimeSubscriptionManager
                        └─ KisRealtimeWebSocketClient ─> KIS WebSocket

KIS REST API
  └─ KisRestClient
       ├─ 토큰 메모리 캐시
       ├─ 호출 간격 제한·제한 응답 재시도
       └─ 일봉/지수/랭킹/환율·해외지수 분봉 서비스

KIS 실시간 payload
  ├─ KisRealtimeStore(JVM 메모리 최신값)
  ├─ Spring event ─> 브라우저 WebSocket 전파
  └─ Spring event ─> 활성 주문·예약 주문 체결 판단

랭킹 스케줄러 ─> KIS REST ─> Redis JSON+TTL ─> 화면 조회
환율·해외지수 1분 스케줄러 ─> KIS REST ─> Redis JSON+TTL ─> 화면 조회
```

## 5. 현재 구현 상태와 경계

- DB 스키마는 `spring.jpa.hibernate.ddl-auto=update`로 관리된다. 버전 관리형 마이그레이션은 **현재 구현되지 않음**.
- KIS WebSocket 실시간 payload, KIS 토큰, WebSocket 구독자와 브라우저 세션은 단일 JVM 메모리에 있다. 환율·해외 지수 1분 조회 결과는 Redis에 TTL 캐시하지만, 다중 인스턴스 WebSocket fan-out 구조는 **현재 구현되지 않음**.
- Redis 장애 시 랭킹 조회는 빈 보드를 반환하고 저장 실패는 경고 로그로 끝난다.
- 실제 주문은 항상 남은 수량 전체를 한 번에 체결한다. 상태 모델에는 `PARTIALLY_FILLED`가 있으나 부분 체결 수량을 결정하는 로직은 **현재 구현되지 않음**.
- 증권계좌 안의 KRW/USD 예수금 환전은 `USD_KRW` 최신 시세를 기준으로 처리하고 환전 내역을 저장한다. 일반 계좌↔증권 계좌 이체 자체는 서로 다른 통화 간 직접 이체를 하지 않는다.
- 운영 배포, CI/CD, 관측성, FDS, 뉴스·AI 기능은 **현재 구현되지 않음**.
