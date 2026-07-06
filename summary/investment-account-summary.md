# 증권 계좌 Summary

마지막 정리일: 2026-07-03

이 문서는 FinMate의 증권 계좌 도메인 구현과 앞으로의 증권 거래 설계 방향을 정리한다.

## 구현 범위

현재 증권 파트에서 구현한 기능은 다음과 같다.

- 증권 계좌 엔티티 설계
- 증권사 enum 관리
- 증권 계좌 개설
- 증권 계좌 목록 조회
- 대표 증권계좌 설정
- 투자 홈 요약
- 계좌번호 Registry 기반 중복 방지
- 일반 계좌에서 증권 계좌로 예수금 입금
- 증권 계좌에서 일반 계좌로 예수금 출금
- 예수금 입출금 DTO 분리
- 예수금 입출금 로그 엔티티 분리
- 예수금 입출금 내역 조회
- 일반 계좌와 증권 계좌 사이 자금 이동 시 비관적 락 적용

아직 구현하지 않은 기능:

- 주문
- 체결
- 보유 종목
- 현재가 연동
- 수익률 계산
- 포트폴리오 화면

주식/시세 쪽에서 새로 구현한 기능:

- KIS 종목 마스터 기반 국내/해외 종목 관리
- 종목 검색
- 관심 종목
- 종목 상세 화면
- 일봉 데이터 on-demand 동기화
- 캔들 차트와 이동평균선 표시
- 기간 내 최고가/최저가 표시
- KOSPI/KOSDAQ/NASDAQ 거래량/거래대금 TOP10
- Redis 기반 랭킹 캐시

## 주요 엔티티

### Investment

`Investment`는 사용자의 증권 계좌를 표현한다.

주요 필드:

- `id`
- `user`
- `accountNumber`
- `securitiesCompanyCode`
- `depositBalance`
- `primary`
- `createdAt`

설계 포인트:

- 일반 계좌의 `balance`와 달리 증권 계좌는 주식 주문에 사용할 현금성 잔고를 `depositBalance`로 표현한다.
- 계좌번호는 `AccountNumberRegistry`에 먼저 등록된 값을 사용한다.
- 증권 계좌번호와 증권사는 개설 후 바뀌면 안 되므로 `updatable = false`로 설정했다.
- setter를 열지 않고 도메인 메서드로 상태를 변경한다.

주요 도메인 메서드:

- `create(user, accountNumber, securitiesCompanyCode)`
- `depositCash(amount)`
- `withdrawCash(amount)`
- `markAsPrimary()`
- `unmarkPrimary()`

### SecuritiesCompanyCode

증권사 목록을 enum으로 관리한다.

예시:

- 키움증권
- NH투자증권
- KB증권
- 신한투자증권
- 삼성증권
- 미래에셋증권
- 한국투자증권
- 토스증권
- 카카오페이증권

설계 포인트:

- 은행과 증권사는 다른 도메인이므로 `BankCode`와 분리했다.
- 화면 표시용 `displayName`을 둔다.
- DB 저장은 `EnumType.STRING`을 전제로 한다.

### PrimaryInvestment

대표 증권계좌 화면 표시용 DTO다.

주요 필드:

- `accountNumber`
- `depositBalance`
- `securitiesCompanyCode`

사용처:

- 투자 홈에서 대표 증권계좌 정보를 표시한다.

### InvestmentDepositRequest

일반 계좌에서 증권 계좌로 예수금을 입금할 때 사용하는 DTO다.

주요 필드:

- `fromAccountId`
- `fromBankCode`
- `toInvestmentId`
- `toSecuritiesCompanyCode`
- `amount`

설계 포인트:

- 출금 일반계좌는 화면에서 직접 입력하지 않고 쿼리 파라미터로 전달된 계좌번호와 은행코드를 기반으로 서버에서 조회한다.
- 서버가 찾은 일반계좌의 id와 은행코드를 DTO에 세팅하고 hidden field로 전달한다.
- 입금할 증권계좌는 사용자의 증권계좌 목록에서 선택한다.
- DTO의 코드값과 실제 조회된 계좌의 코드값이 일치하는지 서비스에서 다시 검증한다.

### InvestmentWithdrawalRequest

증권 계좌에서 일반 계좌로 예수금을 출금할 때 사용할 DTO다.

주요 필드:

- `fromInvestmentId`
- `fromSecuritiesCompanyCode`
- `toAccountId`
- `toBankCode`
- `amount`

설계 포인트:

- 입금과 출금은 방향이 다르므로 하나의 DTO로 합치지 않고 분리했다.
- 출금에서는 증권계좌가 출금 대상이고 일반계좌가 입금 대상이다.
- 출금 증권계좌는 URL 파라미터의 계좌번호와 증권사코드로 조회하되, 반드시 현재 사용자 소유 조건을 포함한다.
- 입금 일반계좌는 현재 사용자의 일반 계좌 목록에서 선택한다.
- DTO의 증권사코드와 은행코드는 hidden field로 전달되지만 서비스에서 실제 엔티티 값과 다시 비교한다.

### SecuritiesCashTransaction

`SecuritiesCashTransaction`은 증권 계좌의 예수금 변화 장부다.

주요 필드:

- `investment`
- `transfer`
- `type`
- `amount`
- `balanceBeforeTransaction`
- `balanceAfterTransaction`
- `counterpartyBankCode`
- `counterpartyAccountNumber`
- `counterpartyName`
- `description`
- `createdAt`

거래 타입:

- `DEPOSIT`
- `WITHDRAW`

설계 포인트:

- 일반 계좌의 현금 장부인 `AccountTransaction`과 분리했다.
- 증권 계좌 예수금은 `Investment.depositBalance` 기준으로 변한다.
- 거래 전/후 예수금을 모두 남겨 로그만 보고도 예수금 흐름을 추적할 수 있게 했다.
- `Transfer`를 참조해서 일반 계좌 쪽 거래내역과 같은 자금 이동 이벤트로 묶는다.

## 수익률 필드를 Investment에 넣지 않은 이유

`Investment`에는 총 평가금액, 평가손익, 수익률을 저장하지 않았다.

이유:

- 이 값들은 고정 상태가 아니라 보유 종목과 현재가에 따라 계속 변하는 계산값이다.
- 현재가가 바뀔 때마다 `Investment`를 계속 update하면 정합성 관리 포인트가 늘어난다.
- 보유 수량, 평균 매입가, 현재가를 기반으로 서비스에서 계산해 DTO로 내려주는 방식이 더 자연스럽다.

계산 방향:

```text
총 매입금액 = 평균매입가 * 보유수량 합계
총 평가금액 = 현재가 * 보유수량 합계
평가손익 = 총 평가금액 - 총 매입금액
수익률 = 평가손익 / 총 매입금액 * 100
```

추후 저장이 필요한 경우:

- 일별 자산 추이
- 월별 수익률
- 특정 시점 평가금액

이런 값은 `Investment`에 직접 넣지 않고 `PortfolioSnapshot` 같은 별도 스냅샷 테이블로 분리하는 것이 좋다.

## 증권 계좌 개설

처리 메서드:

- `InvestmentService.openInvestment()`

흐름:

```text
1. 현재 사용자의 증권 계좌 개수 조회
2. 10개 이상이면 예외
3. 계좌번호 후보 생성
4. AccountNumberRegistry에 INVESTMENT 타입으로 먼저 등록
5. Registry 등록 성공 시 Investment 생성
6. Investment 저장
```

정책:

- 사용자별 증권 계좌는 최대 10개다.
- 증권 계좌번호도 일반 계좌와 같은 형식인 `000000-00-000000`을 사용한다.
- 일반 계좌와 증권 계좌 간 계좌번호 중복은 `AccountNumberRegistry`로 막는다.
- 초기 예수금은 0원이다.

보완점:

- 증권 계좌 10개 제한은 현재 count 기반이다.
- 동시 개설 요청에 대한 사용자 row lock 또는 별도 제한 전략을 검토할 수 있다.

## 대표 증권계좌 설정

처리 메서드:

- `InvestmentService.setPrimary(investmentId, userId)`

흐름:

```text
1. 대표 증권계좌로 설정할 investmentId 전달
2. 현재 사용자의 모든 증권 계좌를 PESSIMISTIC_WRITE lock으로 조회
3. 요청한 investmentId가 현재 사용자의 증권 계좌인지 확인
4. 기존 대표 증권계좌 해제
5. 새 증권 계좌를 대표 증권계좌로 설정
```

설계 포인트:

- 사용자가 증권 계좌를 여러 개 가질 수 있으므로 기본 증권계좌가 필요하다.
- 대표 증권계좌는 투자 홈 요약, 투자금 입출금 기본 대상, 포트폴리오 기본 조회에서 사용할 수 있다.
- 내부 상태 변경은 계좌번호가 아니라 `investmentId` 기반으로 처리한다.
- 동시 대표 증권계좌 변경 요청에 대비해 비관적 락을 적용했다.

보완점:

- 현재는 서비스 로직과 비관적 락으로 사용자당 대표 증권계좌 1개를 유지한다.
- 더 강한 방어가 필요하면 DB partial unique index 또는 별도 대표 증권계좌 테이블을 검토할 수 있다.

## 투자 홈

경로:

- `/investments`

현재 표시 정보:

- 총 예수금
- 증권 계좌 수
- 대표 증권계좌 정보
- 내 수익률 미구현 영역
- 증권 메뉴 링크

증권 계좌가 없을 때:

- “아직 증권 계좌가 없습니다.”
- 증권 계좌 개설 링크

증권 계좌가 있을 때:

- 총 예수금
- 계좌 수
- 대표 증권계좌
- 수익률 영역

## 증권 계좌 목록

경로:

- `/investments/list`

현재 표시 정보:

- 증권사
- 계좌번호
- 예수금
- 대표계좌 여부
- 투자금 이체 링크
- 포트폴리오 링크
- 대표계좌 설정 버튼

## 투자금 입출금

일반 계좌이체와 다른 점:

```text
일반 계좌이체
-> 타인 계좌로 자금 이동 가능
-> 1회/일일 이체한도 필요

투자금 입출금
-> 내 일반 계좌 <-> 내 증권 계좌
-> 본인 명의 계좌 사이 이동으로 제한
-> 별도 이체한도보다 소유자 검증과 잔액 정합성이 중요
```

증권 계좌에 별도 이체한도를 두지 않는 이유:

- 투자금 입출금은 제3자 계좌로 직접 송금하는 기능이 아니다.
- 사용자 본인 명의의 일반 계좌와 증권 계좌 사이에서만 이동하도록 제한한다.
- 따라서 일반 계좌이체처럼 별도 1회/일일 한도를 두기보다 소유자 검증, 잔액 검증, 비관적 락, 로그 저장을 우선한다.

필수 검증:

- 일반 계좌가 현재 사용자의 계좌인지 확인
- 증권 계좌가 현재 사용자의 계좌인지 확인
- DTO로 전달된 은행코드와 실제 일반 계좌의 은행코드가 일치하는지 확인
- DTO로 전달된 증권사코드와 실제 증권 계좌의 증권사코드가 일치하는지 확인
- 일반 계좌에서 증권 계좌로 입금 시 일반 계좌 잔액 확인
- 증권 계좌에서 일반 계좌로 출금 시 예수금 확인
- 금액이 0보다 큰지 확인
- 같은 트랜잭션 안에서 양쪽 잔액 변경
- 잔액 변경 대상 row에 비관적 락 적용

### 예수금 입금

처리 메서드:

- `InvestmentService.depositToInvestment()`

화면 흐름:

```text
1. /accounts/transfer-investment?from=계좌번호&fromBankCode=은행코드 로 진입
2. 서버에서 출금 일반계좌가 현재 사용자의 계좌인지 조회
3. 출금 계좌는 화면에 고정 표시
4. 사용자는 본인 증권계좌 목록에서 입금 대상 선택
5. 금액 입력
6. POST 요청으로 예수금 입금 처리
```

서비스 흐름:

```text
1. 일반 계좌와 증권 계좌를 비관적 락으로 조회
2. 두 계좌가 모두 현재 사용자의 계좌인지 확인
3. 은행코드와 증권사코드 일치 여부 확인
4. 일반 계좌 잔액과 증권 계좌 예수금의 변경 전 금액 기록
5. 일반 계좌 withdraw()
6. 증권 계좌 depositCash()
7. Transfer 저장
8. AccountTransaction 저장
9. SecuritiesCashTransaction 저장
```

저장되는 데이터:

```text
Transfer 1건
-> 자금 이동 이벤트

AccountTransaction 1건
-> 일반 계좌 관점의 출금 로그

SecuritiesCashTransaction 1건
-> 증권 계좌 관점의 예수금 입금 로그
```

### 예수금 출금

처리 메서드:

- `InvestmentService.withdrawFromInvestment()`

화면 흐름:

```text
1. /investments/transfer 로 진입
2. 사용자의 증권계좌 목록에서 출금 증권계좌 선택
3. /investments/transfer?from=증권계좌번호&fromSecuritiesCompanyCode=증권사코드 로 이동
4. 서버에서 출금 증권계좌가 현재 사용자의 계좌인지 조회
5. 출금 증권계좌는 화면에 고정 표시
6. 사용자는 본인 일반계좌 목록에서 입금 대상 선택
7. 금액 입력
8. POST 요청으로 예수금 출금 처리
```

화면 분리:

- `investments/cash/transfer`: 출금 증권계좌 선택
- `investments/cash/transfer-target`: 입금 일반계좌 선택과 금액 입력

컨트롤러:

- `InvestmentController.securityCashTransfer()` GET
- `InvestmentController.securityCashTransfer()` POST

설계 포인트:

- 증권계좌 목록이 필요한 선택 화면에만 `investments`를 model에 담는다.
- 입금 일반계좌와 금액을 입력하는 target 화면에는 전체 증권계좌 목록을 넘기지 않는다.
- 화면에 필요한 데이터만 전달해서 템플릿 책임과 노출 범위를 줄인다.

서비스 흐름:

```text
1. 일반 계좌와 증권 계좌를 비관적 락으로 조회
2. 두 계좌가 모두 현재 사용자의 계좌인지 확인
3. 증권사코드와 은행코드 일치 여부 확인
4. 증권 계좌 예수금과 일반 계좌 잔액의 변경 전 금액 기록
5. 증권 계좌 withdrawCash()
6. 일반 계좌 deposit()
7. Transfer 저장
8. AccountTransaction 저장
9. SecuritiesCashTransaction 저장
```

저장되는 데이터:

```text
Transfer 1건
-> 자금 이동 이벤트

SecuritiesCashTransaction 1건
-> 증권 계좌 관점의 예수금 출금 로그

AccountTransaction 1건
-> 일반 계좌 관점의 입금 로그
```

### 예수금 거래내역 조회

경로:

- `/investments/securityCashTransaction`

지원 기능:

- 전체 증권계좌 예수금 거래내역 조회
- 특정 증권계좌 예수금 거래내역 조회
- 기간 필터
- 20개 단위 페이징
- 총 입금액 / 총 출금액 / 순금액 표시

처리 메서드:

- `InvestmentService.findSecuritiesCashTransactions(userId, period, page)`
- `InvestmentService.findSecuritiesCashTransactions(userId, investmentNumber, securitiesCompanyCode, period, page)`
- `InvestmentService.summarizeSecuritiesCashTransactions(userId, period)`
- `InvestmentService.summarizeSecuritiesCashTransactions(userId, investmentNumber, securitiesCompanyCode, period)`

조회 화면:

- `investments/cash/transactions`

설계 포인트:

- 일반 계좌 거래내역 화면과 비슷한 UX를 제공하되 조회 대상은 `SecuritiesCashTransaction`이다.
- 특정 증권계좌 조회 시 계좌번호와 증권사코드만 믿지 않고 `findOwnedInvestment()`로 현재 사용자 소유 여부를 먼저 검증한다.
- 거래내역 화면에서 `transaction.investment` 정보가 필요하므로 JPQL fetch join과 별도 count query를 사용한다.
- 예수금 입금 합계는 `SecuritiesCashTransactionType.DEPOSIT`, 출금 합계는 `WITHDRAW` 기준으로 계산한다.

### 데드락 방지

처리 메서드:

- `InvestmentService.lockAccountAndInvestmentAvoidingDeadlock()`

규칙:

```text
일반 계좌와 증권 계좌 사이 자금 이동은 방향과 관계없이 항상 일반 계좌를 먼저 lock한다.
그 다음 증권 계좌를 lock한다.
```

이유:

- 예수금 입금은 일반 계좌에서 출금하고 증권 계좌에 입금한다.
- 예수금 출금은 증권 계좌에서 출금하고 일반 계좌에 입금한다.
- 두 요청이 동시에 들어와도 lock 획득 순서가 같아야 순환 대기 가능성을 줄일 수 있다.
- 그래서 입금/출금 방향과 무관하게 `Account -> Investment` 순서로 `SELECT FOR UPDATE`를 수행한다.

운영체제 개념과의 연결:

- 일반 계좌와 증권 계좌는 서로 다른 테이블의 row지만, 트랜잭션 관점에서는 둘 다 lock을 획득해야 하는 자원이다.
- 예수금 입금은 `Account -> Investment` 방향이고, 예수금 출금은 논리적으로 `Investment -> Account` 방향이다.
- 방향대로 lock을 잡으면 두 요청이 동시에 들어올 때 서로 반대 순서로 자원을 기다릴 수 있다.
- 이를 피하기 위해 실제 자금 이동 방향과 무관하게 항상 `Account`를 먼저 lock하고 `Investment`를 나중에 lock한다.
- 이는 운영체제의 resource ordering 방식으로 circular wait 조건을 제거하는 설계다.

## 증권 거래내역 설계 방향

일반 계좌의 거래내역은 `AccountTransaction`으로 관리한다.

증권 계좌의 예수금 거래내역은 `SecuritiesCashTransaction`으로 분리했다.

이유:

- `AccountTransaction`은 `Account`에 묶인 일반 계좌 현금 장부다.
- 증권 계좌는 `Investment`에 묶인 예수금 장부가 필요하다.
- 주식 매수/매도는 종목, 수량, 체결가, 수수료, 세금, 보유수량 변화 등 일반 계좌 거래와 다른 정보가 필요하다.

추천 구조:

```text
Transfer
-> 일반 계좌와 증권 계좌 사이 투자금 입출금 이벤트

SecuritiesCashTransaction
-> 증권 계좌 예수금 변화 장부

StockOrder
-> 사용자가 낸 주문

StockExecution
-> 실제 체결 내역

Holding
-> 현재 보유 종목 상태
```

투자금 입금 예시:

```text
내 일반 계좌 -> 내 증권 계좌

Transfer 1건
AccountTransaction 1건
SecuritiesCashTransaction 1건
```

투자금 출금 예시:

```text
내 증권 계좌 -> 내 일반 계좌

Transfer 1건
SecuritiesCashTransaction 1건
AccountTransaction 1건
```

주식 매수 예시:

```text
StockOrder 1건
StockExecution 1건 이상
SecuritiesCashTransaction 1건
Holding 변경
```

설계 판단:

- 일반 계좌 현금 장부와 증권 계좌 예수금 장부를 분리한다.
- 투자금 입출금은 하나의 이벤트로 묶을 수 있도록 `Transfer`를 사용한다.
- 주식 매매는 `Transfer`가 아니라 주문/체결 도메인으로 관리한다.

## 현재 보완점

- 기존 DB의 `account_transfer.from_account_id`, `to_account_id`, `from_investment_id`, `to_investment_id` nullable 제약 확인
- 예수금 입출금 통합 테스트 추가
- 주문 엔티티 `StockOrder`
- 체결 엔티티 `StockExecution`
- 보유 종목 엔티티 `Holding`
- 현재가 연동
- 수익률 계산 DTO
- 포트폴리오 요약 화면
- 대표 증권계좌 DB 제약 검토
- 증권 계좌 개설 10개 제한 동시성 보강
