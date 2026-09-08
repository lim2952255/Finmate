# FinMate 프로젝트 개요

## 1. 프로젝트 목적

FinMate는 일반 은행 계좌와 모의 투자 계좌를 한 애플리케이션에서 관리하는 React 기반 금융 포트폴리오 프로젝트다. 현재 소스가 제공하는 핵심 범위는 다음과 같다.

- 세션 기반 로컬·Google·Kakao·Naver 로그인과 로그아웃
- 다중 통화 일반 계좌 개설, 대표 계좌, 이체 한도, 계좌이체 및 거래 내역
- 투자 계좌 개설, 통화별 예수금, 일반 계좌와 투자 계좌 사이의 자금 이동, 증권계좌 안 KRW/USD 환전
- 국내·미국 종목 마스터, 업종명 표시, 시장별 종목/업종 검색, 관심 종목, 사용자별 차트 가로선, 거래량·거래대금 순위
- 시장가·지정가·예약 모의 주문, 체결, 예수금·보유 종목 정산
- 한국투자증권(KIS) REST API와 WebSocket을 이용한 시세 수집

실제 증권사 계좌에 주문을 전송하는 코드는 없다. `StockOrder`와 `StockTradeTransaction`은 FinMate DB 안에서 처리되는 모의 거래다.

문서 탐색은 다음 기준을 따른다.

- 패키지·계층·트랜잭션 경계: [아키텍처](ARCHITECTURE.md)
- 엔티티와 관계: [도메인 모델](DOMAIN_MODEL.md)
- 잔액·수량·잠금·원장 정합성 계약: [금융 불변식](FINANCIAL_INVARIANTS.md)
- 주문 접수·체결·취소 흐름: [주식 거래 흐름](TRADING_FLOW.md)
- KIS REST·WebSocket 경계: [KIS 연동](KIS_INTEGRATION.md)
- 종목 상세 재무 학습 카드의 제품·데이터·표현 계약: [종목 상세 재무 학습 카드](STOCK_FINANCIAL_DETAIL.md)
- 로컬 실행·설정·검증: [개발 가이드](DEVELOPMENT_GUIDE.md)

> JWT, FDS, Spring Batch, QueryDSL, AWS 배포 등은 현재 `build.gradle`과 `src/main/java`에서 확인되지 않는다.
> OpenAI 연동은 운영 코드가 아니라 `src/evaluation`의 뉴스 랭킹 LLM judge에만 존재한다. 모든 사용자 화면은 React로 렌더링된다.

## 2. 실제 기술 스택

| 구분 | 코드에서 확인된 기술 |
|---|---|
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 3.5.15 |
| 웹 | React 19, Vite 8, Spring MVC REST API, Bean Validation |
| 보안 | Spring Security 폼 로그인·Google/Kakao OIDC·Naver OAuth2·인가, BCrypt, HTTP Session 기반 SecurityContext |
| 영속성 | Spring Data JPA, Hibernate, MySQL Connector/J |
| DB | MySQL 8.4 (`docker-compose.local.yml`) |
| 캐시 | Redis 7.2, `StringRedisTemplate` |
| 실시간 | Spring WebSocket(브라우저 연결), JDK `HttpClient` WebSocket(KIS 연결) |
| 외부 통신 | JDK `java.net.http.HttpClient` |
| 뷰 | React 전체 사용자 화면, 공용 정적 CSS |
| 빌드·테스트 | Gradle Wrapper, JUnit 5, Spring Boot Test, GitHub Actions CI |
| 보조 | Lombok, Docker Compose |

`README.md`의 Spring Boot 버전 및 일부 기술 목록은 실제 빌드와 다르므로 이 문서는 `build.gradle`을 기준으로 한다.

## 3. 주요 기능

### 사용자와 인증

`AuthApiController`와 `UserService`가 비밀번호 확인을 포함한 로컬 회원가입과 이름·전화번호·이메일 기반의 아이디 찾기를 처리하고, `UserAccountApiController`가 로그인된 로컬 사용자의 현재 비밀번호를 다시 확인한 뒤 비밀번호를 변경한다. Spring Security가 폼 로그인과 Google·Kakao·Naver 로그인을 함께 처리한다. 로컬 로그인은 `FinMateUserDetailsService`와 `BCryptPasswordEncoder`로 아이디·비밀번호를 검증한다. Google·Kakao는 `FinMateOidcUserService`가 검증된 OIDC `sub`를 사용하고, Naver는 `FinMateOAuth2UserService`가 사용자 정보 응답의 `response.id`를 사용해 `OAuthAccount`의 로컬 `User`와 연결한다. 최초 소셜 로그인에는 비밀번호가 없는 `User`를 생성하며 공급자 비밀번호·액세스 토큰·리프레시 토큰은 DB에 저장하지 않는다.

두 로그인 방식 모두 인증 결과를 공통 `FinMateAuthenticatedPrincipal`로 다루고 `SecurityContext`를 서버 HTTP Session에 보존한다. 따라서 계좌와 투자 자산의 소유권 기준은 로그인 방식과 관계없이 기존 `User.id`다. 이메일이 같다는 이유로 기존 로컬 계정과 자동 연결하지 않으며, 명시적인 계정 연결 기능은 현재 구현되지 않았다.

### 일반 계좌

`AccountService`가 계좌 개설, 대표 계좌 설정, 통화별 잔액 집계, 이체 한도 변경, 거래 내역 조회와 계좌이체를 담당한다. 계좌이체는 출금·입금 계좌의 잔액 변경, `Transfer` 1건, 양쪽 `AccountTransaction` 2건을 같은 트랜잭션에서 저장한다.

### 투자 계좌와 예수금

`Investment`는 사용자 소유 투자 계좌이고, `InvestmentCashBalance`가 계좌별·통화별 사용 가능 예수금과 주문 잠금 예수금을 관리한다. `InvestmentService`가 일반 계좌와 투자 계좌 사이의 자금 이동 및 양쪽 거래 내역을 같은 트랜잭션에서 처리하고, `InvestmentCurrencyExchangeService`가 증권계좌 안 KRW/USD 환전과 환전 내역 저장을 처리한다.

### 종목과 시장 데이터

종목 마스터와 국내 업종코드 마스터는 평일 스케줄러가 파일을 내려받아 DB에 반영한다. 해외 업종코드는 종목 상세·목록·포트폴리오 화면에서 필요한 거래소 코드 목록이 DB에 없을 때 KIS REST API로 조회해 DB에 캐시한다. 검색 화면은 종목명/종목코드 기준 검색과 업종명/업종코드 기준 검색을 분리하고, 전체·KOSPI·KOSDAQ·NASDAQ 시장 필터를 함께 적용한다. 국내 종목은 소·중·대 업종 중 가장 세부적인 유효 업종명을 표시하고, 포트폴리오는 업종별 매입금액 비중을 통화별로 계산하되 해외 종목은 거래소별 업종 체계가 다르므로 거래소 그룹별로 분리 집계한다. 포트폴리오는 통화별 보기와 현재 USD/KRW 환율을 적용한 KRW·USD 통합 환산 보기를 제공하며, 환산은 표시 계산에만 사용하고 실제 잔고와 거래금액은 변경하지 않는다. 포트폴리오 평가손익은 실시간 시세가 들어오면 실시간 가격을 사용하고, 초기 표시나 장마감처럼 실시간 체결가가 없을 때는 최신 일봉 종가를 KIS REST API로 보충해 정적 fallback 가격으로 사용한다. 종목 상세 차트는 기본 일봉(최근 3년), 주봉(최근 10년), 월봉·연봉(상장 이후)을 온디맨드로 동기화하고 현재 미완성 봉은 실시간 체결가로 갱신한다. 환율·해외 지수 일봉도 화면 조회 시 부족한 기간을 KIS REST API에서 가져와 DB에 보충한다. 국내 KOSPI/KOSDAQ 지수 상세 화면은 KIS WebSocket 실시간 지수 체결을 구독하고, 환율·해외 지수 실시간 값은 1분 스케줄러가 KIS REST API에서 갱신해 Redis에 TTL 캐시한다. 거래량·거래대금 TOP 10도 스케줄러가 KIS에서 갱신하고 Redis에 TTL 캐시한다.

종목 상세의 실시간 채팅은 별도 `/ws/chat` 연결을 사용한다. 로그인 HTTP 세션에서 사용자를 식별하며, 메시지는 MySQL에 저장해 재접속한 사용자도 과거 기록을 조회할 수 있다. 작성자는 본인 메시지를 수정하거나 소프트 삭제할 수 있고, 다른 메시지를 대상으로 한 답글을 작성할 수 있다.

종목 상세의 뉴스 탭은 탭을 처음 열 때 `/api/stocks/{stockId}/news`를 호출한다. 서버는 `{한글 종목명} 시장정보`를
검색어로 NAVER API HUB 뉴스 검색의 관련도순 후보 80건을 한 번 조회하고, `NEWS_RANKING_STRATEGY`로 선택한
전략 하나만 실행한다. 결과 목록과 전략 타입은 종목별 `stock_news_cache` 행에 저장하며 `updatedAt`이 기본
6시간 이내이고 전략 타입이 현재 설정과 같으면 외부 API와 랭킹 계산을 다시 수행하지 않는다. 같은 JVM에서
동일 종목의 캐시 갱신 요청이 겹치면 하나의 갱신만 수행한다.
랭킹 전략이 최종 선별한 종목 뉴스에는 경량 ONNX KR-FinBert-SC 분석을 한 배치로 수행하고 각 기사를
호재·보통·악재로 표시한다. 분석 결과도 뉴스 JSON에 포함해 캐시하므로 일반 조회에서는 반복 추론하지 않는다.

네 전략의 정량 비교는 운영 애플리케이션과 분리된 Gradle `evaluation` source set에서 수행한다. 평가 설정은
`gradle/evaluation.gradle`에 있으며 `-PwithEvaluation`을 지정한 실행에서만 로드된다. KOSPI,
KOSDAQ, NASDAQ, S&P 500, 금리, 환율, 삼성전자, SK하이닉스, NAVER, 카카오, KB금융, 현대차의 고정 후보
데이터에 네 전략을 적용하고 OpenAI LLM judge가 관련도와 동일
사건 그룹을 Structured Outputs로 반환한다. 평가기는 이를 이용해 nDCG, 중복률, 고유 사건 수, Yield와
처리 시간을 CSV로 생성하며 평가 클래스는 운영 JAR에 포함되지 않는다.

`/investments/reports`는 KOSPI, KOSDAQ, NASDAQ, S&P 500, 금리, 환율의 6개 시장 뉴스 주제를 탭으로 제공한다. 각 주제는 관련도순 후보 80건을 조회하고 `NEWS_RANKING_STRATEGY`로 선택한 전략 하나를 적용해 상위 10건을 보여준다. 결과는 사용자별로 저장하지 않고 주제별 `market_report_cache` 한 행을 모든 사용자가 공유하며, 기본 6시간이 지난 뒤 첫 조회에서만 갱신한다.
시장 리포트의 최종 기사에도 같은 KR-FinBert-SC 감성 분석을 적용하며 주제별 호재·보통·악재 개수와 기사별 결과를 함께 표시한다.

### 모의 주식 거래

매수 주문은 예수금을, 매도 주문은 보유 수량을 먼저 잠근다. 실시간 체결가·호가가 조건을 만족하면 예수금, 보유 수량, 주문 상태와 체결 기록을 하나의 트랜잭션으로 갱신한다. 예약 주문은 조건 충족 시 일반 주문으로 전환된다.

이 절은 기능 개요일 뿐 정합성 계약을 정의하지 않는다. 자산 보존, 원장 원자성, 종료 상태 경합의 기준은 [금융 불변식](FINANCIAL_INVARIANTS.md)을 따른다.

## 4. 전체 시스템 흐름

```text
브라우저
  ├─ HTTP 요청 ─> Controller ─> Service ─> Spring Data JPA ─> MySQL
  ├─ /ws/stocks WebSocket
         └─ StockRealtimeWebSocketHandler
              └─ StockRealtimeClientSessionService
                   └─ StockRealtimeSubscriptionManager
                        └─ KisRealtimeWebSocketClient ─> KIS WebSocket
  └─ /ws/chat WebSocket
         └─ StockChatWebSocketHandler
              └─ StockChatClientSessionService
                   ├─ StockChatService ─> MySQL 채팅 기록
                   └─ JVM 메모리 종목별 세션 fan-out

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
종목 뉴스 탭 ─> NAVER 뉴스 검색 ─> MySQL 6시간 캐시 ─> 화면 조회
시장 리포트 탭 ─> NAVER 뉴스 검색 ─> 주제별 MySQL 6시간 공유 캐시 ─> 화면 조회
```

## 5. 현재 구현 상태와 경계

- DB 스키마는 `spring.jpa.hibernate.ddl-auto=update`로 관리된다. 버전 관리형 마이그레이션은 **현재 구현되지 않음**.
- KIS WebSocket 실시간 payload, KIS 토큰, WebSocket 구독자와 브라우저 세션은 단일 JVM 메모리에 있다. 채팅 기록은 MySQL에 남지만 채팅 fan-out과 접속 인원도 단일 JVM 메모리 기준이다. 환율·해외 지수 1분 조회 결과는 Redis에 TTL 캐시하지만, 다중 인스턴스 WebSocket fan-out 구조는 **현재 구현되지 않음**.
- Redis 장애 시 랭킹 조회는 빈 보드를 반환하고 저장 실패는 경고 로그로 끝난다.
- 실제 주문은 항상 남은 수량 전체를 한 번에 체결한다. 상태 모델에는 `PARTIALLY_FILLED`가 있으나 부분 체결 수량을 결정하는 로직은 **현재 구현되지 않음**.
- 증권계좌 안의 KRW/USD 예수금 환전은 `USD_KRW` 최신 시세를 기준으로 처리하고 환전 내역을 저장한다. 일반 계좌↔증권 계좌 이체 자체는 서로 다른 통화 간 직접 이체를 하지 않는다.
- GitHub Actions CI는 `main` 대상 pull request와 `main` push에서 전체 테스트를 실행한다. 운영 배포(CD), 관측성, FDS, 뉴스 AI 요약 기능은 **현재 구현되지 않음**.
