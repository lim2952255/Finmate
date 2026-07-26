# FinMate 업무 규칙 테스트 계약

## 1. 목적과 기준

이 문서는 FinMate의 로그인, 일반계좌, 투자계좌, 환전, 모의 주식 주문·체결·취소·만료가 지켜야 할 업무 규칙을 실행 가능한 테스트 계약으로 정의한다.

- 금융 상태 전이와 정합성의 상위 기준은 [금융 불변식](FINANCIAL_INVARIANTS.md)이다.
- 현재 주문 처리 순서는 [주식 거래 흐름](TRADING_FLOW.md), 엔티티 관계는 [도메인 모델](DOMAIN_MODEL.md)을 함께 따른다.
- 문서와 코드가 다르면 테스트로 차이를 드러내고 **알려진 공백/실패**로 기록한다.
- 목표 계약을 위반하는 테스트는 `@Disabled`, 조건부 skip, 완화된 기대값으로 숨기지 않는다.
- 테스트 구축 작업은 결함을 증명하는 데까지 책임지며, 프로덕션 금융 로직 수정은 별도 작업으로 수행한다.
- GitHub Actions CI는 이 문서 개편과 최초 테스트 구축 범위에 포함하지 않는다.

각 규칙은 다음 정보를 가진다.

| 항목 | 의미 |
|---|---|
| 규칙 ID | 테스트와 실패 보고서에서 사용하는 안정적인 식별자 |
| 필수 계약 | 구현이 반드시 만족해야 하는 업무 규칙 |
| 테스트 수준 | 규칙을 입증하는 최소 테스트 종류 |
| 핵심 시나리오 | Given / When / Then 형태의 정상·거부·경계 사례 |
| 검증 증거 | 상태, 원장, 보존식, 예외 등 반드시 함께 확인할 결과 |
| 현재 상태 | 현재 구현 사실 또는 알려진 공백 |
| 예정 테스트 | 구현할 테스트 클래스의 기준 이름 |

## 2. 테스트 수준과 실행 환경

### 2.1 테스트 수준

| 표기 | 수준 | 용도 |
|---|---|---|
| `U` | Unit | 순수 계산, 값 검증, 엔티티 상태 전이, 반올림 |
| `W` | Web/MVC | 공개·보호 경로, 로그인 세션, 요청 권한 경계 |
| `I` | MySQL Integration | JPA 제약, 트랜잭션 rollback, 상태와 원장 원자성 |
| `C` | MySQL Concurrency | 비관적 락, 동시 상태 전이, deadlock, 정확히 한 번 처리 |

서비스 mock 테스트만으로 DB 트랜잭션, 실제 unique 제약, `PESSIMISTIC_WRITE`, deadlock 부재를 보장했다고 주장하지 않는다. 해당 규칙은 `I` 또는 `C` 테스트를 반드시 포함한다.

### 2.2 테스트 MySQL 격리 계약

MySQL 통합·동시성 테스트는 Testcontainers로 실행마다 별도 MySQL 8.4 컨테이너를 생성한다.

1. 기존 개발용 MySQL 컨테이너와 개발 DB를 사용하지 않는다.
2. database는 `finmate_test`, username/password는 테스트 전용 값을 사용한다.
3. host port는 Testcontainers가 할당한 임의 포트를 사용한다.
4. Spring test context는 컨테이너가 제공한 JDBC 연결 정보만 사용한다.
5. 테스트 시작 시 현재 catalog가 `finmate_test`인지 검증한다.
6. JDBC URL이 개발 DB `finmate` 또는 고정 개발 DB 연결을 가리키면 전체 테스트를 즉시 실패시킨다.
7. 테스트 schema와 데이터는 테스트 종료 시 폐기한다.
8. 테스트는 개발 DB에 `create`, `drop`, `truncate`, `delete`를 수행하지 않는다.

### 2.3 공통 수치·보존 검증

모든 금액·수량 테스트는 필요한 범위에서 다음 경계를 포함한다.

- `null`, 0, 음수
- 잔액·수량과 정확히 같은 값
- 잔액·수량보다 최소 단위만큼 큰 값
- KRW 0자리, USD 2자리, 거래 수량 최대 6자리 scale
- 허용 scale과 초과 scale
- `CEILING`, `HALF_UP`, `DOWN`이 서로 다른 결과를 만드는 반올림 경계

주요 보존식은 다음과 같다.

```text
일반계좌 내부 이체:
fromAfter = fromBefore - amount
toAfter   = toBefore + amount
transfer.amount = amount

예수금 잠금/해제:
total = available + locked
lock/release 전후 total 동일

투자계좌 출금:
investmentAvailableAfter = investmentAvailableBefore - amount
accountBalanceAfter       = accountBalanceBefore + amount

보유 수량:
availableQuantity = quantity - lockedQuantity
quantity >= lockedQuantity >= 0

환전:
fromAfter = fromBefore - fromAmount
toAfter   = toBefore + roundedToAmount

활성 주문 종료:
FILLED / CANCELED / EXPIRED / TRIGGERED 중 허용된 하나만 승리
승리한 전이만 자산을 소비 또는 해제
```

## 3. 로그인과 세션

### AUTH-001 — 비로그인 접근 제한

- **필수 계약:** 비로그인 사용자는 `/`, `/home`, `/login`, `/signup`과 정적 리소스 외의 보호 경로에 접근할 수 없다.
- **테스트 수준:** `W`
- **핵심 시나리오:**
  - Given 로그인 세션이 없을 때, When 공개 경로를 요청하면, Then 정상 응답한다.
  - Given 로그인 세션이 없을 때, When 일반계좌·투자계좌·주문 경로를 요청하면, Then `/login`으로 이동하며 원래 요청 경로를 전달한다.
- **검증 증거:** HTTP status, redirect URL, session 미생성 여부.
- **현재 상태:** `LoginInterceptor`가 공개 경로를 제외한 `/**`를 검사한다.
- **예정 테스트:** `LoginInterceptorMvcTest`

### AUTH-002 — 서버 세션 기반 로그인 유지

- **필수 계약:** 인증 상태는 서버측 HTTP session에 저장한다. 브라우저가 같은 session identifier cookie를 보내는 동안 자격정보를 다시 제출하지 않고 보호 경로에 접근할 수 있다.
- **테스트 수준:** `W`
- **핵심 시나리오:**
  - Given 로그인 성공으로 생성된 session이 있을 때, When 같은 session으로 연속 요청하면, Then 모두 인증 사용자로 처리한다.
  - Given session cookie가 없거나 유효하지 않을 때, When 보호 경로를 요청하면, Then 재로그인을 요구한다.
- **검증 증거:** session의 로그인 사용자 속성, 후속 요청의 인증 성공, 새 로그인 요청 부재.
- **현재 상태:** HTTP session의 로그인 사용자 속성을 interceptor가 확인한다.
- **예정 테스트:** `LoginSessionMvcTest`

### AUTH-003 — 로그아웃과 세션 무효화

- **필수 계약:** 로그아웃 또는 session 만료 후 기존 session identifier로 보호 경로에 접근할 수 없다.
- **테스트 수준:** `W`
- **핵심 시나리오:** Given 로그인 session이 있을 때, When 로그아웃한 뒤 같은 session으로 요청하면, Then 로그인 페이지로 이동한다.
- **검증 증거:** session invalidation, 보호 경로 redirect.
- **비목표:** remember-me, 브라우저 cookie 삭제나 서버 재시작을 넘어서는 영구 로그인.
- **예정 테스트:** `LoginSessionMvcTest`

## 4. 일반계좌

### ACC-001 — 사용자당 최대 10개 계좌

- **필수 계약:** 한 사용자는 일반계좌를 최대 10개까지만 보유할 수 있다. 동시 개설 요청으로도 10개를 초과할 수 없다.
- **테스트 수준:** `I`, `C`
- **핵심 시나리오:**
  - Given 9개 계좌, When 1개를 개설하면, Then 10개가 된다.
  - Given 10개 계좌, When 추가 개설하면, Then 거부하고 계좌번호 registry도 증가하지 않는다.
  - Given 9개 계좌, When 2개를 동시에 개설하면, Then 최종 계좌 수는 10개 이하다.
- **검증 증거:** 사용자 계좌 수, registry 행 수, 예외, rollback.
- **현재 상태:** 서비스가 `count` 후 저장하므로 동시 요청의 10개 상한 보장 증거가 없다.
- **예정 테스트:** `AccountOpeningIntegrationTest`, `AccountOpeningConcurrencyTest`

### ACC-002 — 계좌 소유권

- **필수 계약:** 사용자는 본인 명의 일반계좌만 조회·대표설정·한도변경·출금 출발계좌로 사용할 수 있다.
- **테스트 수준:** `I`, 필요한 경로는 `W`
- **핵심 시나리오:** Given 사용자 A와 B의 계좌가 있을 때, When A가 B 계좌를 조회하거나 변경하면, Then 거부한다.
- **검증 증거:** 예외 또는 접근 거부, B 계좌 상태 불변.
- **현재 상태:** 주요 조회와 이체 출금계좌에서 사용자 ID를 검증한다.
- **예정 테스트:** `AccountOwnershipIntegrationTest`

### ACC-003 — 일반계좌 이체 보존식

- **필수 계약:** 동일 통화 계좌 사이에서만 양수 금액을 이체한다. 출금 잔액은 음수가 될 수 없고 이체 전후 양 계좌 합계는 보존된다.
- **테스트 수준:** `U`, `I`
- **핵심 시나리오:** 정상 이체, 전액 이체, 잔액 초과, 0/음수, 다른 통화, 같은 계좌 이체.
- **검증 증거:** 양 계좌 before/after, 합계, 통화, 예외.
- **현재 상태:** 엔티티와 서비스가 잔액·통화·동일계좌를 검증한다.
- **예정 테스트:** `AccountTest`, `AccountTransferIntegrationTest`

### ACC-004 — 1회 이체한도

- **필수 계약:** 한 번의 일반계좌 출금 이체 금액은 해당 계좌의 1회 이체한도를 초과할 수 없다. 정확히 같은 금액은 허용한다.
- **테스트 수준:** `U`, `I`
- **핵심 시나리오:** 한도 미만, 정확히 일치, 최소 통화 단위 초과.
- **검증 증거:** 잔액·한도사용량·원장 불변 또는 정상 변경.
- **현재 상태:** `TransferLimitUsageService`가 검사한다.
- **예정 테스트:** `TransferLimitUsageServiceTest`, `AccountTransferIntegrationTest`

### ACC-005 — 일일 누적 이체한도

- **필수 계약:** Asia/Seoul 기준 같은 날짜의 누적 일반계좌 출금액은 일일 한도를 초과할 수 없다.
- **테스트 수준:** `U`, `I`, `C`
- **핵심 시나리오:** 누적 미만, 정확히 일치, 초과, 날짜 변경, 동시 이체.
- **검증 증거:** `DailyTransferUsage.usedAmount`, 계좌 잔액, 원장 수, rollback.
- **현재 상태:** 부모 Account를 잠근 이체 경로에서 사용량을 조회·생성하지만 동시성 증거가 없다.
- **예정 테스트:** `DailyTransferUsageTest`, `AccountTransferConcurrencyTest`

### ACC-006 — 일반계좌 락 순서와 동시 이체

- **필수 계약:** 두 Account는 계좌번호 문자열이 아니라 **Account ID 오름차순**으로 `PESSIMISTIC_WRITE` 잠금을 획득한다. 반대 방향 동시 이체도 timeout 내 완료하고 최종 보존식을 만족해야 한다.
- **테스트 수준:** `C`
- **핵심 시나리오:** Given A와 B 계좌, When A→B와 B→A를 barrier로 동시에 시작하면, Then deadlock 없이 완료하거나 명시된 업무 예외로 종료한다.
- **검증 증거:** timeout, thread별 결과, 최종 잔액 합계, 원장 수, 음수 잔액 부재.
- **현재 상태:** 현재 코드는 Account ID 오름차순으로 잠근다. 기존 문서의 “계좌번호가 작은 계좌” 표현은 잘못되어 교정했다.
- **예정 테스트:** `AccountTransferConcurrencyTest`

### ACC-007 — 이체 상태와 원장 원자성

- **필수 계약:** 계좌 잔액 변경, `Transfer`, 출금 `AccountTransaction`, 입금 `AccountTransaction`, 일일 한도 사용량은 동일 DB 트랜잭션에서 함께 commit하거나 함께 rollback한다. “로그를 먼저 저장”이라는 호출 순서는 계약이 아니다.
- **테스트 수준:** `I`
- **핵심 시나리오:** 정상 이체와 각 원장 저장 실패 주입.
- **검증 증거:** 정상 시 정확히 1개의 Transfer와 양쪽 원장, 실패 시 잔액·사용량·모든 원장 원복.
- **현재 상태:** 하나의 `@Transactional` 서비스 경계에 있으나 실패 주입 통합 테스트가 없다.
- **예정 테스트:** `AccountTransferAtomicityIntegrationTest`

### ACC-008 — 계좌번호 전역 유일성

- **필수 계약:** 일반계좌와 투자계좌 유형을 합친 시스템 전체에서 계좌번호는 전역 유일하다. DB unique 제약이 최종 방어선이어야 한다.
- **테스트 수준:** `I`, `C`
- **핵심 시나리오:** 일반/일반, 투자/투자, 일반/투자 동시 중복 후보 발급.
- **검증 증거:** registry와 실제 계좌의 중복 부재, 충돌 요청의 재시도 또는 명시적 실패.
- **현재 상태:** 공통 `AccountNumberRegistry`의 account number에 global unique 제약이 있다.
- **예정 테스트:** `AccountNumberRegistryIntegrationTest`, `AccountNumberRegistryConcurrencyTest`

### ACC-009 — 계좌 생성과 번호 등록 rollback

- **필수 계약:** 계좌번호 registry 등록과 실제 일반/투자 계좌 생성은 하나의 원자적 작업이다. 실제 계좌 생성이 실패하면 registry 행도 rollback되어 번호가 orphan 상태로 남지 않아야 한다.
- **테스트 수준:** `I`
- **핵심 시나리오:** Given 번호 등록 성공 후 계좌 저장 실패를 주입했을 때, Then 계좌와 registry가 모두 존재하지 않는다.
- **검증 증거:** account/investment count와 registry count, 동일 번호 재사용 가능성.
- **해결 상태:** 계좌번호 registry 저장을 별도 `REQUIRES_NEW` 트랜잭션에서 분리하지 않고 계좌 생성 트랜잭션에 참여시켰다. 실제 계좌 저장이 실패하면 registry도 함께 rollback된다.
- **현재 증거:** `AccountOpeningIntegrationTest`, `InvestmentOpeningIntegrationTest`

## 5. 투자계좌와 예수금

### INV-001 — 사용자당 최대 10개 투자계좌

- **필수 계약:** 한 사용자는 투자계좌를 최대 10개까지만 보유할 수 있고 동시 요청도 상한을 넘을 수 없다.
- **테스트 수준:** `I`, `C`
- **검증 증거:** 투자계좌 수, registry 행, 통화별 예수금 행, rollback.
- **현재 상태:** 일반계좌와 같이 `count` 후 저장하므로 동시 상한 보장 증거가 없다.
- **예정 테스트:** `InvestmentOpeningIntegrationTest`, `InvestmentOpeningConcurrencyTest`

### INV-002 — 일반계좌에서 투자계좌로 입금

- **필수 계약:** 출발 일반계좌와 목적 투자계좌가 모두 인증 사용자 소유일 때만 허용한다. 본인 명의 계좌 사이의 내부 자금 이동이므로 일반계좌의 1회·일일 이체한도를 적용하지 않고 `DailyTransferUsage`에도 포함하지 않는다. `Account → Investment → 해당 CashBalance` 순서로 잠근다.
- **테스트 수준:** `I`, `C`
- **핵심 시나리오:** 정상 입금, 타인 명의 출발·목적 계좌 거부, 일반계좌 한도보다 큰 본인계좌 간 입금, 잔액 초과, 양방향 동시 이동.
- **검증 증거:** Account 감소, 같은 통화 available 증가, `DailyTransferUsage` 불변, 모든 원장, timeout.
- **현재 상태:** 현재 입금 경로는 양쪽 계좌의 사용자 ID를 검증하고 일반계좌 이체한도 사용량을 생성·증가시키지 않는다.
- **예정 테스트:** `InvestmentDepositIntegrationTest`, `AccountInvestmentTransferConcurrencyTest`

### INV-003 — 투자계좌에서 본인 일반계좌로 출금

- **필수 계약:** 출발 투자계좌와 목적 일반계좌가 모두 인증 사용자 소유일 때만 허용한다. 1회·일일 이체한도는 적용하지 않고, 해당 통화의 `availableBalance`만 상한으로 사용한다. `lockedBalance`는 출금할 수 없다.
- **테스트 수준:** `I`, `C`
- **핵심 시나리오:** available 이내, available과 정확히 일치, available 초과지만 total 이내, 타인 목적계좌, 동시 출금.
- **검증 증거:** available 감소, locked 불변, 일반계좌 증가, 한도 사용량 불변, 원장.
- **현재 상태:** 현재 출금 경로는 일반계좌 이체한도를 적용하지 않는다.
- **예정 테스트:** `InvestmentWithdrawalIntegrationTest`, `AccountInvestmentTransferConcurrencyTest`

### INV-004 — 일반·투자 자금 이동 원자성

- **필수 계약:** 일반계좌 잔액, 투자계좌 available, `Transfer`, `AccountTransaction`, `SecuritiesCashTransaction`은 한 트랜잭션에서 commit/rollback한다. 이 내부 자금 이동은 일반계좌 한도 사용량을 생성하거나 변경하지 않는다.
- **테스트 수준:** `I`
- **핵심 시나리오:** 각 원장 저장 실패 주입과 상태 변경 이후 예외.
- **검증 증거:** 실패 후 새 트랜잭션에서 모든 상태·원장 원복.
- **예정 테스트:** `InvestmentCashTransferAtomicityIntegrationTest`

### INV-005 — 통화별 예수금 분리

- **필수 계약:** 투자계좌는 KRW와 USD 예수금을 별도 행으로 관리하며 한 통화 변경이 다른 통화에 섞이지 않는다. `(investment, currency)`는 unique다.
- **테스트 수준:** `U`, `I`, `C`
- **검증 증거:** 통화별 before/after, unique 제약, 최초 행 생성 경합.
- **현재 상태:** 투자계좌 생성 시 통화별 balance를 만들고 DB unique 제약이 있다.
- **예정 테스트:** `InvestmentCashBalanceTest`, `InvestmentCashBalanceIntegrationTest`

### INV-006 — available/locked/total 예수금

- **필수 계약:** `availableBalance >= 0`, `lockedBalance >= 0`, `totalBalance = availableBalance + lockedBalance`를 유지한다. lock/release는 total을 보존하고 출금은 available만 감소시킨다.
- **테스트 수준:** `U`, 체결 흐름은 `I`
- **핵심 시나리오:** lock/release, 부족 available, 부족 locked, 실제 정산액이 예약액보다 작음/같음/큼.
- **검증 증거:** 각 구성요소와 total before/after.
- **예정 테스트:** `InvestmentCashBalanceTest`, `StockBuySettlementIntegrationTest`

### INV-007 — 투자계좌 소유권

- **필수 계약:** 사용자는 본인 투자계좌만 조회·대표설정·환전·자금이동·주문에 사용할 수 있다.
- **테스트 수준:** `I`, 필요한 경로는 `W`
- **검증 증거:** 타인 요청 거부와 타인 계좌·예수금·보유수량 불변.
- **예정 테스트:** `InvestmentOwnershipIntegrationTest`

## 6. 환전

### FX-001 — 최신 USD/KRW 환율 사용과 외부 경계

- **필수 계약:** 환전은 조회 시점에 제공된 최신 USD/KRW 가격을 입력으로 사용한다. KIS/Redis 조회 실패 시 금융 상태를 변경하지 않는다.
- **테스트 수준:** `U`, `I`
- **핵심 시나리오:** 정상 환율, 환율 없음, 0/음수 환율, stale 여부를 판별할 정보가 없는 입력.
- **검증 증거:** 적용 환율, 상태·원장 변경 부재 또는 정상 원장.
- **현재 상태:** 최신값 조회 실패 시 예외를 발생시키지만 외부 시세 freshness 계약은 별도 정의가 없다.
- **예정 테스트:** `InvestmentCurrencyExchangeServiceTest`, `CurrencyExchangeIntegrationTest`

### FX-002 — 환전 락 순서

- **필수 계약:** 환전 방향과 관계없이 `Investment → KRW CashBalance → USD CashBalance` 순서로 비관적 락을 획득한다.
- **테스트 수준:** `C`
- **핵심 시나리오:** KRW→USD와 USD→KRW를 같은 투자계좌에서 동시에 시작한다.
- **검증 증거:** timeout 내 종료, 음수 잔액 부재, 방향별 원장, 반올림 보존식.
- **현재 상태:** 현재 코드가 KRW 후 USD 순서로 잠근다.
- **예정 테스트:** `CurrencyExchangeConcurrencyTest`

### FX-003 — 환전 계산·scale·rounding

- **필수 계약:** KRW→USD는 나눗셈, USD→KRW는 곱셈 후 대상 통화 최소 단위로 `DOWN`한다. 결과가 최소 단위보다 작으면 거부한다.
- **테스트 수준:** `U`, `I`
- **핵심 시나리오:** 정확히 나누어짐, 버림 발생, 최소 결과, 최소 미만, scale 초과.
- **검증 증거:** from/to amount, rate, before/after snapshot.
- **예정 테스트:** `CurrencyExchangeCalculationTest`, `CurrencyExchangeIntegrationTest`

### FX-004 — 환전과 원장 원자성

- **필수 계약:** from available 감소, to available 증가, `InvestmentCurrencyExchangeTransaction` 저장은 함께 commit/rollback하고 locked는 바꾸지 않는다.
- **테스트 수준:** `I`
- **핵심 시나리오:** 정상 환전과 환전 원장 저장 실패 주입.
- **검증 증거:** 양 통화 available/locked, 환전 원장 수와 snapshot.
- **예정 테스트:** `CurrencyExchangeAtomicityIntegrationTest`

## 7. 주식 주문·예약·체결

### ORD-001 — 일반 주문 접수 시간

- **필수 계약:** 일반 시장가·지정가 주문은 해당 시장의 정규장 또는 지원하는 시간외 거래시간에만 접수한다.
- **테스트 수준:** `U`, `I`
- **핵심 시나리오:** KOSPI/KOSDAQ 09:00, 15:30, 15:40, 18:00 경계; NASDAQ 정규장·시간외·DST 전환; 주말.
- **검증 증거:** 접수 또는 거부, 자산·주문 불변.
- **현재 정책:** 공휴일·조기폐장·종목별 특수 제한은 반영하지 않는다.
- **예정 테스트:** `StockMarketTradingHoursTest`, `StockOrderAcceptanceIntegrationTest`

### ORD-002 — 예약주문 장외 등록·취소

- **필수 계약:** 예약주문 등록과 취소는 장중·장외 모두 허용한다. 예약 등록 시 자산을 즉시 잠그지만 트리거·일반주문 전환·체결은 거래시간에만 수행한다.
- **테스트 수준:** `U`, `I`
- **핵심 시나리오:** 주말 또는 장 마감 후 예약 등록, 장외 가격 입력, 다음 거래시간의 트리거, 장외 취소.
- **검증 증거:** 예약 상태, locked 자산, 일반주문/체결 원장 부재 또는 생성 시점.
- **해결 상태:** 예약 접수 경로에서 거래시간 검증을 제거했다. 자산은 즉시 잠그되 트리거와 체결 단계에서만 거래시간을 확인한다.
- **현재 증거:** `StockOrderPolicyIntegrationTest`

### ORD-003 — 주문·예약 만료기한

- **필수 계약:** 일반 시장가 주문은 만료기한이 없다. 일반 지정가 주문과 모든 예약주문은 접수 시각 기준 최소 5분, 최대 30일 범위의 만료기한이 필수다.
- **테스트 수준:** `U`, `I`
- **핵심 시나리오:** null, 과거, 현재, 5분 미만, 정확히 5분, 정확히 30일, 30일 초과; 시장가 일반주문; 시장가형 예약; 지정가형 예약.
- **검증 증거:** 생성 여부, 저장된 expiresAt, 자산 잠금 부재 또는 정상 잠금.
- **현재 증거:** `TradingAmountValidatorTest`, `StockOrderPolicyIntegrationTest`

### ORD-004 — 시세 payload와 독립적인 만료

- **필수 계약:** 지정가 주문과 예약주문은 해당 종목의 실시간 시세가 오지 않아도 만료되어야 하며 남은 잠금 자산을 정확히 한 번 해제한다.
- **테스트 수준:** `I`, `C`
- **핵심 시나리오:** 만료 전후에 payload 없음, 만료와 취소 동시, 만료와 payload 동시.
- **검증 증거:** 단일 종료 상태, available/locked/holding, 중복 해제 부재.
- **해결 상태:** `StockOrderExpirationScheduler`가 실시간 payload와 독립적으로 만료 대상을 주기 조회한다. 서버 시작 직후에도 한 번 실행하므로 중단 중 만료된 주문과 예약을 복구한다.
- **현재 증거:** `StockOrderPolicyIntegrationTest`, `StockSettlementTerminalIntegrationTest`, `StockTerminalRaceConcurrencyTest`

### ORD-005 — 시장과 결제 통화 일치

- **필수 계약:** KOSPI/KOSDAQ 종목은 KRW 예수금, NASDAQ 종목은 USD 예수금과 보유수량을 사용한다. 다른 통화 예수금은 변경하지 않는다.
- **테스트 수준:** `U`, `I`
- **검증 증거:** 선택 통화, 양 통화 before/after, 잘못된 통화 요청 거부.
- **예정 테스트:** `StockTradingCurrencyPolicyTest`, `StockOrderIntegrationTest`

### ORD-006 — 주문 가능 자산과 잠금

- **필수 계약:** 매수는 available cash, 매도는 available holding quantity 이내에서만 접수한다. 지정가 주문과 예약주문은 필요한 금액 또는 수량을 즉시 잠근다.
- **테스트 수준:** `U`, `I`, `C`
- **핵심 시나리오:** available 미만/정확히 일치/초과, total은 충분하지만 available 부족, 동시 주문.
- **검증 증거:** cash available/locked/total 또는 holding quantity/locked/available.
- **예정 테스트:** `InvestmentCashBalanceTest`, `StockHoldingTest`, `StockOrderAssetLockIntegrationTest`

### ORD-007 — 매수 예약액과 체결 정산

- **필수 계약:** 매수 예약의 gross는 통화 단위 `CEILING`, 예약 수수료는 `HALF_UP`하고 합계를 잠근다. 체결 시 locked에서 예약액을 제거하고 실제 정산액과 차이를 available에 반영한다.
- **테스트 수준:** `U`, `I`
- **핵심 시나리오:** 실제 정산액이 예약액보다 작음/같음/큼, 추가 available 충분/부족.
- **검증 증거:** available/locked/total, gross, commission, trade ledger.
- **예정 테스트:** `StockTradingAssetServiceTest`, `StockBuySettlementIntegrationTest`

### ORD-008 — 매도 보유수량과 체결 정산

- **필수 계약:** 매도 접수는 holding 수량만 잠그고, 체결은 quantity와 lockedQuantity를 같은 수량만큼 줄이며 net cash를 해당 통화 available에 입금한다.
- **테스트 수준:** `U`, `I`
- **검증 증거:** holding before/after, cash before/after, 수수료·세금, trade ledger.
- **예정 테스트:** `StockHoldingTest`, `StockSellSettlementIntegrationTest`

### ORD-009 — 체결 상태와 원장 원자성

- **필수 계약:** 주문 상태, cash, holding, reserved asset, `StockTradeTransaction`은 같은 DB 트랜잭션에서 함께 commit/rollback한다.
- **테스트 수준:** `I`
- **핵심 시나리오:** 정상 매수·매도와 trade ledger 저장 실패 주입.
- **검증 증거:** 실패 후 주문 상태·cash·holding·trade ledger 전부 원복.
- **현재 상태:** 서비스 트랜잭션은 존재하지만 실패 주입 증거가 없다.
- **예정 테스트:** `StockTradeAtomicityIntegrationTest`

### ORD-010 — 취소·만료·체결의 정확히 한 번 종료

- **필수 계약:** 활성 주문/예약에서 체결, 취소, 만료, 예약 트리거 중 허용된 하나만 승리한다. 승리한 전이만 잠금 자산을 소비·해제하고 뒤늦은 경로는 상태를 바꾸지 않는다.
- **테스트 수준:** `C`
- **핵심 시나리오:** 이중 취소, 취소 대 체결, 취소 대 만료, 예약 트리거 대 취소.
- **검증 증거:** 최종 상태 하나, 자산 정확히 한 번 변경, 원장 중복 부재.
- **해결 상태:** 소유권 확인 후 주문·예약 행을 `PESSIMISTIC_WRITE`로 다시 조회하고, 잠근 최신 상태를 기준으로 취소 가능 여부를 판정한다. 체결·만료·트리거 경로와 동일한 상태 행 락으로 직렬화한다.
- **현재 증거:** `StockSettlementTerminalIntegrationTest`, `StockTerminalRaceConcurrencyTest`

### ORD-011 — 중복·동시 실시간 payload

- **필수 계약:** 같은 가격 payload가 중복 또는 동시에 들어와도 한 주문을 두 번 체결하거나 원장을 중복 생성할 수 없다.
- **테스트 수준:** `C`
- **검증 증거:** 주문 상태, 체결 원장 수, externalExecutionId unique, cash/holding 보존식.
- **현재 상태:** 활성 목록 조회에 락이 있지만 중복·동시 payload 검증 증거가 없다.
- **예정 테스트:** `StockRealtimeExecutionConcurrencyTest`

### ORD-012 — 주식 거래 전역 락 순서

- **필수 계약:** 주문 접수와 체결이 같은 트랜잭션에서 이어지는 현재 구조에서는 매수·매도 모두 `CashBalance → StockHolding` 순서로 비관적 락을 획득해 역순 획득과 순환 대기를 만들지 않아야 한다.
- **테스트 수준:** `C`
- **핵심 시나리오:** 같은 투자계좌와 종목의 매수·매도 주문을 barrier로 동시에 시작하고 두 주문이 timeout 안에 체결되는지 확인한다.
- **검증 증거:** thread별 예외 부재, timeout 내 종료, 체결 원장 2건, 최종 예수금·보유수량·잠금수량 보존식.
- **현재 상태:** 주문 접수와 체결 경로가 모두 `CashBalance → StockHolding` 순서로 잠근다. 공개 주문 경로는 앞단의 Investment 락으로 직렬화될 수 있으므로 테스트는 실제 요청 흐름의 deadlock 부재를 검증하고, 구체적인 자산 락 순서는 구현과 함께 확인한다.
- **후속 검토:** 주문 접수와 체결의 트랜잭션 분리는 멱등성·실패 복구·상태 전이를 함께 설계해야 하는 별도 변경으로 다룬다.
- **예정 테스트:** `StockLockOrderConcurrencyTest`

### ORD-013 — 최초 보유 행 생성 경합

- **필수 계약:** 같은 투자계좌·종목의 최초 매수 체결이 동시에 발생해도 `StockHolding`은 하나만 존재하고 수량·평균가가 모든 성공 체결을 반영해야 한다.
- **테스트 수준:** `C`
- **검증 증거:** `(investment, stock)` unique, holding 수량·평균가, trade ledger 수.
- **현재 상태:** 현재 경로는 CashBalance 락을 직렬화 기준으로 사용하지만 실제 동시성 테스트가 없다.
- **예정 테스트:** `StockHoldingCreationConcurrencyTest`

### ORD-014 — 수수료·세금·평균가 반올림

- **필수 계약:** 체결 gross, 수수료, 세금은 통화 단위 `HALF_UP`, 보유 평균 매수가는 6자리 `HALF_UP`을 사용한다. 수수료는 현재 평균 매수가에 포함하지 않는다.
- **테스트 수준:** `U`, `I`
- **핵심 시나리오:** KOSPI/KOSDAQ/NASDAQ 매수·매도와 반올림 경계.
- **검증 증거:** gross, commission, tax, net cash, average price.
- **예정 테스트:** `StockTradingFeePolicyTest`, `StockTradeCalculationIntegrationTest`

### ORD-015 — KIS 시세와 내부 모의 거래 경계

- **필수 계약:** KIS 데이터는 내부 모의 거래의 가격 입력일 뿐 외부 주문 접수·체결 확인이 아니다. KIS 오류·지연·중복은 내부 금융 상태를 부분 commit하게 해서는 안 된다.
- **테스트 수준:** `U`, `I`, 중복은 `C`
- **핵심 시나리오:** 시세 없음, malformed/invalid 가격, 중복 payload, 처리 도중 예외.
- **검증 증거:** 외부 주문 ID 생성 부재, 내부 상태·원장 atomicity.
- **예정 테스트:** `StockTradingRealtimePriceServiceTest`, `StockRealtimeExecutionIntegrationTest`

## 8. 공통 원장 불변성

### LEDGER-001 — 원장 snapshot 정확성

- **필수 계약:** 원장은 상태 변경과 동일한 통화·금액·수량을 기록하고 before/after snapshot이 실제 엔티티 변경과 일치해야 한다.
- **테스트 수준:** `I`
- **검증 증거:** 일반이체, 일반↔투자, 환전, 매수·매도 각각의 상태와 원장 snapshot 비교.
- **예정 테스트:** 각 도메인 `*AtomicityIntegrationTest`

### LEDGER-002 — 커밋된 원장 불변성

- **필수 계약:** 이미 커밋된 금융 원장을 수정해 과거를 재작성하지 않는다. 수정이 필요하면 별도의 보정 거래를 추가한다.
- **테스트 수준:** `I`
- **검증 증거:** 공개 서비스에 기존 원장 변경 경로가 없고, 후속 거래가 이전 snapshot을 바꾸지 않음.
- **예정 테스트:** `FinancialLedgerImmutabilityIntegrationTest`

## 9. 동시성 테스트 작성 규칙

동시성 테스트는 다음 조건을 모두 만족해야 한다.

1. 각 thread는 독립된 Spring transaction을 사용한다.
2. `CountDownLatch`, `CyclicBarrier` 등으로 시작점을 결정적으로 맞춘다.
3. 완료 timeout을 명시하고 timeout 자체를 실패로 처리한다.
4. 성공/업무 예외/DB deadlock 예외를 thread별로 수집한다.
5. 모든 thread 종료 후 새 transaction에서 최종 상태와 원장을 조회한다.
6. 단순 `Thread.sleep`만으로 경합 시점을 추정하지 않는다.
7. 반복 실행이 필요한 stress test는 횟수와 시간 상한을 둔다.
8. “예외가 없었다”만 확인하지 않고 최종 보존식과 정확히 한 번 처리를 검증한다.

## 10. 실패 주입 테스트 작성 규칙

원장 저장 또는 상태 변경 실패를 주입하는 테스트는 다음을 확인한다.

1. 실패 지점 전까지 영속성 context에서 변경된 값만 보지 않는다.
2. 대상 service transaction이 종료된 뒤 새로운 transaction으로 DB를 다시 조회한다.
3. 잔액, available/locked, holding, 주문 상태, 사용량, 모든 관련 원장이 함께 rollback됐는지 확인한다.
4. 특정 repository 저장만 실패시키기 위해 테스트 spy/mock을 사용할 수 있지만 실제 transaction manager와 MySQL은 유지한다.

## 11. 최초 구현 순서와 완료 기준

### 권장 구현 순서

1. Testcontainers MySQL과 개발 DB 차단 guard
2. 순수 도메인 단위 테스트
3. 로그인·소유권 테스트
4. 일반계좌와 투자계좌 통합·원자성 테스트
5. 환전 통합·동시성 테스트
6. 주문·예약·체결 통합 테스트
7. 종료 상태 경합과 전역 락 순서 동시성 테스트

### 완료 기준

- 이 문서의 모든 규칙 ID가 하나 이상의 테스트에 매핑된다.
- 아직 구현이 계약을 위반하면 해당 테스트는 활성 상태로 실패하며 규칙 ID와 코드 위치를 보고한다.
- 테스트 실행이 개발 DB를 변경하지 않았다는 격리 증거가 있다.
- 각 통합 테스트는 상태와 원장을 함께 검증한다.
- 각 동시성 테스트는 timeout과 최종 보존식을 검증한다.
- 전체 테스트가 통과해야 금융 계약 준수를 주장할 수 있다.

## 12. 테스트 분석으로 식별한 기존 코드 문제와 해결 이력

테스트는 구현을 통과시키기 위한 부속물이 아니라, 업무 계약과 실제 코드 사이의 차이를 재현하는 진단 도구로 사용했다. 아래 항목은 기존 구현의 문제를 실패 테스트로 식별한 뒤 프로덕션 코드 또는 테스트 인프라를 수정하고 회귀 증거를 남긴 사례다.

| 문제 | 식별한 테스트·증거 | 원인 | 해결 | 회귀 증거 |
|---|---|---|---|---|
| 계좌 생성 실패 후 계좌번호 registry가 남을 수 있음 | `AccountOpeningIntegrationTest`, `InvestmentOpeningIntegrationTest`의 `ACC-009` 저장 실패 주입 | registry 저장만 `REQUIRES_NEW`로 먼저 commit되어 외부 계좌 생성 rollback과 분리됨 | registry 저장을 계좌 생성 트랜잭션에 참여시켜 함께 commit/rollback | 일반·투자계좌 저장 실패 후 계좌와 registry가 모두 0건임을 새 트랜잭션에서 확인 |
| 본인 일반계좌에서 투자계좌로 옮긴 내부 자금이 외부 이체 한도를 소비함 | `InvestmentCashTransferIntegrationTest`의 `INV-002/003/004` | 내부 자금 이동에도 `TransferLimitUsageService.use()`를 호출 | 본인 명의 일반↔투자 자금 이동에서는 1회·일일 이체한도를 사용하지 않도록 제거 | 입출금 후 잔액·원장은 변경되지만 `DailyTransferUsage`는 불변 |
| 예약주문을 장외에 접수할 수 없음 | `StockOrderPolicyIntegrationTest`의 `ORD-002 계약 실패 증거` | 일반주문과 예약주문이 동일하게 접수 시 거래시간을 검증 | 예약 접수에서는 거래시간 검증을 제거하고 트리거·체결 시점에만 검사 | 장외 예약 등록·자산 잠금·장외 취소가 정상 처리됨 |
| 시장가형 예약주문이 만료기한 검증을 우회함 | `TradingAmountValidatorTest`, `StockOrderPolicyIntegrationTest`의 `ORD-003 계약 실패 증거` | 일반주문용 validator가 `MARKET`이면 즉시 반환했고 예약도 같은 validator를 사용 | 일반주문과 예약주문의 만료 정책을 분리하고 모든 예약에 만료기한을 요구 | null·과거·5분 미만·5분·30일·30일 초과 경계를 단위·통합 테스트로 확인 |
| 너무 짧은 만료기한은 주문 접수 중 만료될 수 있고 무제한 장기 주문도 허용됨 | `TradingAmountValidatorTest`의 `ORD-003` 경계 테스트 | “현재보다 미래”만 확인하고 운영 가능한 시간 범위를 제한하지 않음 | 접수 시각 기준 최소 5분, 최대 30일 정책 추가 | 정확히 5분과 30일은 허용하고 범위 밖은 자산 잠금 전에 거부 |
| 실시간 시세가 없거나 서버가 중단된 동안 만료된 주문이 계속 활성 상태로 남음 | `StockOrderPolicyIntegrationTest`의 `ORD-004` | 만료 검사가 실시간 payload 처리 경로에만 존재 | DB 기반 만료 서비스와 주기 스케줄러를 추가하고 시작 직후 복구 검사 수행 | payload 없는 지정가 주문과 중단 중 만료된 예약이 `EXPIRED`로 전이되고 자산이 한 번 해제됨 |
| 취소가 먼저 활성 상태를 읽은 뒤 체결·만료·트리거가 끝나면 뒤늦게 자산을 다시 해제할 수 있음 | `StockTerminalRaceConcurrencyTest`의 `ORD-010` 세 경합 | 취소 경로가 상태 행을 잠그지 않은 채 과거 조회 결과로 판단 | 소유권 검사 후 주문·예약을 `PESSIMISTIC_WRITE`로 재조회하고 최신 상태로 종료 전이 판정 | 취소 대 체결, 취소 대 만료, 예약 취소 대 트리거에서 하나의 종료 효과와 원장 1건만 확인 |
| 동시 매수·매도가 서로 반대 순서로 Cash와 Holding을 잠가 deadlock 가능 | `StockLockOrderConcurrencyTest`의 `ORD-012` | 매수 접수는 Cash부터, 매도 접수는 Holding부터 잠가 트랜잭션 전체 락 순서가 역전 | 양 방향 모두 `CashBalance → StockHolding` 순서로 잠금 | barrier로 동시 시작한 매수·매도가 timeout 없이 끝나고 예수금·보유수량·원장 보존식 만족 |
| 여러 Spring test context 종료 시 Hibernate가 같은 FK와 테이블을 반복 drop하며 `HHH000478` 오류 로그 출력 | 전체 `./gradlew test --rerun-tasks` 종료 로그 | 하나의 static Testcontainer를 공유하면서 context마다 `create-drop` 지연 삭제 작업을 예약 | 테스트 profile을 `ddl-auto=update`로 바꾸고 공통 지원 클래스가 매 테스트 전 모든 테이블을 FK 검사 비활성화 후 truncate | 170개 전체 테스트 성공, 종료 로그에서 `SchemaDropperImpl`, `HHH000478`, `Unsuccessful` 미검출 |

### 12.1 문제 분석 방식

1. 업무 규칙을 `AUTH`, `ACC`, `INV`, `FX`, `ORD`, `LEDGER` ID로 고정한다.
2. 정상 결과뿐 아니라 실패 시 상태·원장·잠금 자산이 그대로인지 함께 확인한다.
3. 원자성은 repository mock만으로 주장하지 않고 실제 MySQL 트랜잭션에서 실패를 주입한 뒤 새 트랜잭션으로 재조회한다.
4. 동시성은 단순 `Thread.sleep`에 의존하지 않고 목적에 맞는 동기화 도구와 timeout을 사용한다.
5. 결함 수정 후 동일 테스트를 회귀 테스트로 유지하고 전체 suite에서 상호작용을 확인한다.

### 12.2 동시성 테스트 도구 선택 기준

테스트마다 `CyclicBarrier`와 `CountDownLatch`가 섞여 있는 것은 무조건적인 불일치가 아니라 검증하려는 순서가 다르기 때문이다.

- `CyclicBarrier`: 여러 작업이 같은 지점까지 준비된 뒤 **동시에 출발**해야 하는 이체·환전·주문 경합에 사용한다.
- `CountDownLatch`: 한 작업이 특정 지점까지 진행한 사실을 알리고, 다른 작업이 끝난 뒤 **의도한 순서로 재개**해야 하는 취소 대 체결·만료 경합에 사용한다.

따라서 도구 이름을 통일하기보다 “동시 출발”과 “결정적 순서 제어”라는 테스트 목적을 기준으로 선택한다. 공통적으로 독립 트랜잭션, 제한 시간, thread별 예외 수집, 최종 상태와 보존식 검증을 유지한다.

### 12.3 현재 검증 결과

- 강제 재실행 명령: `./gradlew test --rerun-tasks --console=plain`
- 결과: 전체 170개 테스트 성공
- 테스트 DB: JVM당 새 MySQL 8.4 Testcontainer, Spring context 간 공유
- 데이터 격리: 각 테스트 전 `finmate_test`의 모든 base table truncate
- Gradle의 두 번째 `./gradlew test`가 수백 ms에 끝나는 것은 테스트 재실행이 아니라 모든 task가 `UP-TO-DATE`였기 때문이다.
