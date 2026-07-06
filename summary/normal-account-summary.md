# 일반 계좌 Summary

마지막 정리일: 2026-07-03

이 문서는 FinMate의 일반 계좌 도메인 구현과 설계 의도를 정리한다.

## 구현 범위

현재 일반 계좌 파트에서 구현한 기능은 다음과 같다.

- 계좌 개설
- 보유 계좌 목록 조회
- 대표계좌 설정
- 계좌별 이체한도 관리
- 계좌이체
- 거래내역 저장
- 전체 거래내역 조회
- 계좌별 거래내역 조회
- 거래내역 요약
- 일반 계좌이체와 증권 예수금 입금 메뉴 분리
- 계좌번호 Registry 기반 중복 방지
- 비관적 락 기반 동시성 제어

## 주요 엔티티

### Account

`Account`는 사용자의 일반 가상계좌를 표현한다.

주요 필드:

- `id`
- `user`
- `accountNumber`
- `balance`
- `primary`
- `bankCode`
- `dailyTransferLimit`
- `singleTransferLimit`

설계 포인트:

- `User`와 `Account`는 `User 1 : N Account` 관계다.
- FK를 가진 연관관계의 주인은 `Account`다.
- 계좌번호는 사용자가 입력하지 않고 시스템에서 생성한다.
- 초기 잔액은 `Const.INITIAL_BALANCE`, 현재 3,000,000원이다.
- 이체한도는 사용자 단위가 아니라 계좌 단위로 관리한다.
- `@Setter`를 제거하고 도메인 메서드로 상태를 변경한다.

주요 도메인 메서드:

- `create(accountNumber, bankCode)`
- `assignUser(user)`
- `markAsPrimary()`
- `unmarkPrimary()`
- `withdraw(amount)`
- `deposit(amount)`
- `updateTransferLimit(dailyTransferLimit, singleTransferLimit)`

설계 이유:

- 잔액과 이체한도는 핵심 금융 상태이므로 외부에서 임의로 변경되면 안 된다.
- 출금 금액이 0 이하인지, 잔액이 부족한지 같은 규칙은 `Account` 내부에서 검증한다.
- 서비스 코드가 `setBalance()`로 직접 잔액을 조작하지 않게 했다.

### BankCode

은행 목록을 enum으로 관리한다.

설계 포인트:

- `@Enumerated(EnumType.STRING)`으로 DB에 문자열 저장을 전제로 한다.
- enum 순서가 바뀌어도 데이터 의미가 깨지지 않게 ordinal 저장은 사용하지 않는다.
- 화면 표시용 `displayName`을 둔다.

### AccountNumberRegistry

일반 계좌와 증권 계좌의 계좌번호 중복을 공통으로 막는 엔티티다.

주요 필드:

- `accountNumber`
- `accountType`: `NORMAL`, `INVESTMENT`
- `createdAt`

설계 포인트:

- 일반 계좌와 증권 계좌는 다른 테이블에 저장된다.
- 그래도 계좌번호는 전체 시스템에서 중복되면 안 된다.
- 실제 계좌 엔티티 생성 전에 Registry에 계좌번호를 먼저 저장한다.
- `account_number` unique constraint가 최종 중복 방어선이다.
- setter 없이 `create()`로만 생성한다.
- `accountNumber`, `accountType`, `createdAt`은 `updatable = false`다.

계좌번호 발급 흐름:

```text
후보 계좌번호 생성
-> AccountNumberRegistry 저장 시도
-> unique constraint 위반 시 재시도
-> Registry 저장 성공
-> Account 생성
```

이유:

- `exists` 후 `save` 방식은 동시 요청에서 race condition이 가능하다.
- DB unique constraint를 최종 방어선으로 두는 것이 더 안전하다.

### Transfer

`Transfer`는 자금 이동 요청 1건을 표현한다.

주요 필드:

- `transferGroupId`
- `fromAccount`
- `toAccount`
- `fromInvestment`
- `toInvestment`
- `amount`
- `status`
- `createdAt`

설계 포인트:

- 사용자의 자금 이동 요청 1건을 별도 엔티티로 표현한다.
- 일반 계좌끼리의 계좌이체뿐 아니라 일반 계좌와 증권 계좌 사이의 예수금 입출금도 같은 자금 이동 이벤트로 묶는다.
- 일반 계좌이체에서는 `fromAccount`, `toAccount`가 채워진다.
- 증권 예수금 입금에서는 `fromAccount`, `toInvestment`가 채워진다.
- 증권 예수금 출금에서는 `fromInvestment`, `toAccount`가 채워진다.
- `AccountTransaction`과 `SecuritiesCashTransaction`을 하나의 이벤트로 묶기 위한 부모 역할을 한다.
- `transferGroupId`는 UUID 기반이고 unique constraint로 중복을 방어한다.

### AccountTransaction

`AccountTransaction`은 일반 계좌 입장에서 보이는 거래내역 장부다.

주요 필드:

- `account`
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

- `TRANSFER_IN`
- `TRANSFER_OUT`
- `DEPOSIT`
- `WITHDRAW`

설계 포인트:

- 거래내역은 감사 로그 성격이 강하므로 setter를 제거했다.
- 계좌이체 1건이 발생하면 출금 계좌와 입금 계좌 기준으로 거래내역 2건을 저장한다.
- `balanceBeforeTransaction`과 `balanceAfterTransaction`을 모두 저장한다.

전/후 잔액을 모두 저장한 이유:

- 거래 당시 잔액 흐름을 로그만 보고도 추적할 수 있다.
- `거래 전 잔액 - 거래 금액 = 거래 후 잔액` 검증이 가능하다.
- 장애 복구, 고객 문의, 이상거래 분석, 정산 오류 추적에 유리하다.

### DailyTransferUsage

계좌별 일일 이체 사용액을 관리한다.

주요 필드:

- `account`
- `usageDate`
- `usedAmount`

설계 포인트:

- 일일 이체한도는 “이번 이체 금액”만 보면 안 된다.
- “오늘 이미 사용한 금액 + 이번 이체 금액”으로 검사해야 한다.
- 동시 요청에서 한도 초과를 막기 위해 `DailyTransferUsage` row에 비관적 락을 건다.
- `account_id + usage_date` unique constraint를 둔다.

## 계좌 개설

처리 메서드:

- `AccountService.openAccount()`

흐름:

```text
1. 현재 사용자의 계좌 수 조회
2. 10개 이상이면 예외
3. 계좌번호 후보 생성
4. AccountNumberRegistry에 NORMAL 타입으로 먼저 등록
5. Registry 등록 성공 시 Account 생성
6. user.addAccount(account)
7. Account 저장
```

정책:

- 사용자별 일반 계좌는 최대 10개다.
- 계좌번호는 시스템에서 자동 생성한다.
- 계좌번호 형식은 `000000-00-000000`이다.
- 최대 100번까지 계좌번호 생성을 재시도한다.

보완점:

- 계좌 10개 제한은 현재 count 기반이다.
- 동시 계좌 개설 요청이 들어오면 count 검사 경쟁 조건이 생길 수 있다.
- 이후 사용자 row lock 또는 별도 계좌 수 관리 전략을 검토할 수 있다.

## 대표계좌 설정

처리 메서드:

- `AccountService.setPrimary(accountId, userId)`

흐름:

```text
1. 대표계좌로 설정할 accountId 전달
2. 현재 사용자의 모든 계좌를 PESSIMISTIC_WRITE lock으로 조회
3. 요청한 accountId가 현재 사용자의 계좌인지 확인
4. 기존 대표계좌 해제
5. 새 대표계좌 설정
```

설계 포인트:

- 내부 상태 변경은 계좌번호가 아니라 `accountId` 기반으로 처리한다.
- 동시 대표계좌 변경 요청에 대비해 사용자 계좌 목록에 비관적 락을 건다.
- 과거 데이터 오류로 대표계좌가 여러 개 있어도 모두 해제한 뒤 하나만 설정한다.

보완점:

- 현재는 서비스 로직과 비관적 락으로 사용자당 대표계좌 1개를 유지한다.
- 더 강한 방어가 필요하면 DB partial unique index 또는 별도 대표계좌 테이블을 검토할 수 있다.

## 계좌이체

처리 메서드:

- `AccountService.transfer()`

화면:

- `/accounts/transfer`: 출금 일반계좌 선택
- `/accounts/transfer-target`: 입금 일반계좌 정보와 금액 입력

설계 포인트:

- 일반 계좌이체는 `Account` 테이블에 존재하는 일반 계좌끼리의 자금 이동이다.
- 입금 대상은 계좌번호와 `BankCode`로 조회한다.
- 증권 계좌는 `Investment` 테이블과 `SecuritiesCompanyCode`를 사용하므로 일반 계좌이체 화면에서 직접 이체되지 않는다.
- 증권 계좌로 투자금을 옮기는 경우 `/accounts/transfer-investment` 예수금 입금 화면을 사용하도록 안내한다.

흐름:

```text
1. TransferRequest에서 출금 계좌, 입금 계좌, 금액 추출
2. 계좌번호 + 은행코드로 출금/입금 계좌 id만 조회
3. 같은 계좌 이체인지 검사
4. id가 작은 계좌부터 SELECT FOR UPDATE lock 획득
5. 출금 계좌가 현재 사용자의 계좌인지 검사
6. 1회 이체한도 검사
7. DailyTransferUsage row lock 후 일일 이체한도 검사
8. 출금 계좌 withdraw()
9. 입금 계좌 deposit()
10. Transfer 저장
11. AccountTransaction 2건 저장
```

## 동시성 설계

### 락 전에 id만 조회

계좌이체에서는 `Account` 엔티티를 먼저 조회하지 않고 id만 조회한다.

이유:

- JPA는 조회한 엔티티를 1차 캐시에 올린다.
- 락 전에 엔티티를 조회하면 이후 `SELECT FOR UPDATE`를 호출해도 오래된 객체를 기준으로 처리할 위험이 있다.
- 따라서 id만 먼저 찾고, 실제 검증과 잔액 변경은 lock을 획득한 엔티티로 수행한다.

### 계좌 row 비관적 락

잔액 변경은 `PESSIMISTIC_WRITE` 기반으로 처리한다.

이유:

- 계좌이체는 `조회 -> 검증 -> 변경`이 하나의 논리적 작업이다.
- `@Transactional`만으로는 여러 요청이 같은 잔액을 동시에 읽고 각각 검증하는 문제를 막기에 부족하다.
- 잔액 검증 전에 DB row lock이 필요하다.

### 데드락 방지

출금 계좌와 입금 계좌 두 row에 lock을 걸기 때문에 데드락 가능성이 있다.

해결 방식:

```text
항상 account id가 작은 계좌부터 lock을 획득한다.
```

효과:

- 모든 이체 요청이 같은 lock 순서를 따르므로 순환 대기 가능성을 줄인다.

운영체제 개념과의 연결:

- 운영체제에서 deadlock은 상호 배제, 점유 대기, 비선점, 순환 대기 조건이 함께 만족될 때 발생한다.
- 계좌이체에서는 출금 계좌와 입금 계좌 row가 각각 lock 대상 자원이다.
- A 계좌에서 B 계좌로 이체하는 요청과 B 계좌에서 A 계좌로 이체하는 요청이 동시에 들어오면 서로 반대 순서로 lock을 잡아 순환 대기가 생길 수 있다.
- 이를 막기 위해 모든 요청이 `accountId` 오름차순이라는 동일한 자원 획득 순서를 따른다.
- 이 방식은 운영체제에서 자원에 번호를 매기고 항상 낮은 번호부터 획득하도록 해서 circular wait 조건을 깨는 방식과 같다.

### 일일 이체한도 동시성

단순히 거래내역 합계를 매번 계산하면 동시 요청에서 둘 다 가능하다고 판단할 수 있다.

해결 방식:

```text
DailyTransferUsage(account, usageDate) row를 lock
-> usedAmount + amount 검사
-> 한도 이내면 usedAmount 증가
```

## 거래내역 조회

지원 기능:

- 전체 계좌 거래내역 조회
- 특정 계좌 거래내역 조회
- 기간 필터
- 20개 단위 페이징
- 총 입금액 / 총 출금액 / 순금액 표시

JPQL을 직접 작성한 이유:

- 거래내역 화면에서 `transaction.account` 정보가 필요하다.
- `AccountTransaction.account`는 LAZY 로딩이다.
- 화면에서 하나씩 조회하면 N+1 문제가 생길 수 있다.
- 그래서 `join fetch t.account`를 사용한다.
- 페이징에는 별도 `countQuery`가 필요하다.

거래 요약:

- 입금성 거래: `TRANSFER_IN`, `DEPOSIT`
- 출금성 거래: `TRANSFER_OUT`, `WITHDRAW`
- 순금액: 총 입금액 - 총 출금액

요약 쿼리 단순화:

- 처음에는 JPQL에서 조건부 합계와 DTO 생성자 표현식을 한 번에 처리했다.
- 코드가 길고 enum 풀패키지명 때문에 유지보수가 좋지 않았다.
- 현재는 Repository가 `sum(t.amount) where t.type in :types`만 담당한다.
- Service가 입금 타입/출금 타입을 나눠 전달하고 `TransactionSummary`를 조립한다.

## 화면

일반 계좌 화면:

- `/accounts`: 계좌 관리 홈
- `/accounts/open`: 계좌 개설
- `/accounts/list`: 보유 계좌 목록
- `/accounts/transfer`: 계좌이체
- `/accounts/transfer-limit`: 계좌별 이체한도 변경
- `/accounts/transactions`: 거래내역 조회

계좌 관리 홈 표시 정보:

- 총 보유금액
- 보유 계좌 수
- 대표계좌 정보

거래내역 화면 표시 정보:

- 전체/계좌별 거래내역
- 기간 필터
- 총 입금액
- 총 출금액
- 순금액
- 페이징

## 현재 보완점

- 대표계좌 DB 제약 검토
- 계좌 상태 도입
- 도메인 예외 클래스 정리
- 이체 요청 멱등성
- 계좌 10개 제한 동시성 보강
- `DailyTransferUsage` 최초 생성 경쟁 조건 보강
- FDS 확장
