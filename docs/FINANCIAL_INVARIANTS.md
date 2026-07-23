# 금융 불변식

## 1. 역할과 우선순위

이 문서는 잔액, 예수금, 보유 수량, 주문, 체결, 취소, 만료, 환전 및 원장을 변경할 때 지켜야 할 정합성 계약의 기준 문서다. 관련 흐름은 `docs/DOMAIN_MODEL.md`, `docs/TRADING_FLOW.md`, `docs/KIS_INTEGRATION.md`를 함께 읽는다. 문서끼리 충돌하면 금융 정합성은 이 문서를 우선하고, 현재 동작에 대한 사실 판단은 소스와 실행 가능한 테스트를 우선한다. 문서와 구현이 다르면 차이를 **알려진 공백/위험**으로 기록하고 둘 중 하나를 같은 변경에서 바로잡아야 한다.

아래 표기는 의도적으로 구분한다.

- **[현재 구현 사실]** 지금 코드에서 관찰되는 동작이다.
- **[필수 불변식]** 이후 변경도 깨뜨리면 안 되는 계약이다. 현재 코드가 충분히 보호한다는 뜻은 아니다.
- **[알려진 공백/위험]** 구현이나 검증 증거가 부족하여 보장을 주장할 수 없는 부분이다.

## 2. 수치 표현과 반올림

- **[필수 불변식]** 금액, 가격, 수량, 잔액 및 수수료 계산에 부동소수점 타입을 사용하지 않고 `BigDecimal`을 유지한다.
- **[현재 구현 사실]** 통화 입력 scale은 KRW 0자리, USD 2자리이며 초과 자릿수는 `RoundingMode.UNNECESSARY`로 거부한다. 거래 수량은 최대 6자리 scale로 정규화한다 (`src/main/java/com/finmate/domain/investment/CurrencyCode.java`, `src/main/java/com/finmate/domain/stock/trading/TradingAmountValidator.java`). DB 열은 예수금 2자리, 보유 수량·평균가는 6자리다 (`src/main/java/com/finmate/domain/investment/InvestmentCashBalance.java`, `src/main/java/com/finmate/domain/stock/trading/StockHolding.java`).
- **[현재 구현 사실]** 매수 예약의 순수 거래대금(`grossAmount`)은 통화 단위로 `CEILING`, 그 거래대금에 대한 예약 수수료는 통화 단위로 `HALF_UP`하며, 실제 잠그는 예수금은 두 금액의 합이다. 체결가·체결 총액과 체결 수수료·세금은 `HALF_UP`, 보유 평균 매수가는 6자리 `HALF_UP`이다 (`src/main/java/com/finmate/service/stock/trading/StockTradingAssetService.java`, `src/main/java/com/finmate/service/stock/trading/StockTradingExecutionService.java`, `src/main/java/com/finmate/domain/stock/trading/StockTradingFeePolicy.java`, `src/main/java/com/finmate/domain/stock/trading/StockHolding.java`). 환전 결과는 대상 통화 단위로 `DOWN`한다 (`src/main/java/com/finmate/service/investment/InvestmentCurrencyExchangeService.java`).
- **[알려진 공백/위험]** 엔티티 열의 scale만으로 모든 쓰기 경로의 반올림 계약이 보장되지는 않는다. 위 규칙은 관찰된 경로의 사실이며, 전 시스템 공통 보장이나 실제 금융기관의 정산 규칙이라고 확대 해석하지 않는다.

## 3. 잔액과 수량 보존

### 일반 계좌와 자금 이동

- **[필수 불변식]** 일반 계좌 출금은 잔액을 음수로 만들 수 없다. 동일 통화 내부 이체는 `출금 계좌 감소액 = 입금 계좌 증가액 = 이체 원장 금액`이어야 하며, 잔액 변경과 `Transfer` 및 양쪽 `AccountTransaction` 기록은 모두 성공하거나 모두 롤백돼야 한다 (`src/main/java/com/finmate/service/normal/account/AccountService.java`).
- **[필수 불변식]** 일반 계좌와 투자 계좌 사이 이동은 일반 계좌 잔액 변화, 투자 예수금 변화, `Transfer`, `AccountTransaction`, `SecuritiesCashTransaction`을 한 단위로 보존한다 (`src/main/java/com/finmate/service/investment/InvestmentService.java`).

### 예수금

- **[현재 구현 사실]** 통화별 총 예수금은 `availableBalance + lockedBalance`다. `lock`과 `releaseLocked`는 두 구성요소 사이만 이동하므로 총액이 보존된다. 출금은 available만 감소시키고, 잠금액을 출금할 수 없다 (`src/main/java/com/finmate/domain/investment/InvestmentCashBalance.java`).
- **[필수 불변식]** `availableBalance >= 0`, `lockedBalance >= 0`을 유지한다. 매수 접수 시 available에서 예약액을 locked로 옮기고, 취소·만료 시 남은 예약액을 정확히 한 번 되돌린다. 체결 시 locked에서 예약액을 제거하고 실제 정산액과의 차이만 available에 반영한다.
- **[필수 불변식]** 환전은 from available 감소와 to available 증가 및 환전 원장 저장이 원자적이어야 하며 locked는 바꾸지 않는다. 환율 변환 자체는 통화 간 명목 총액 보존식이 아니라 기록된 환율과 `DOWN` 규칙으로 검증한다 (`src/main/java/com/finmate/service/investment/InvestmentCurrencyExchangeService.java`, `src/main/java/com/finmate/domain/investment/cash/exchange/InvestmentCurrencyExchangeTransaction.java`).

### 보유 수량

- **[현재 구현 사실]** 매도 가능 수량은 `quantity - lockedQuantity`다. 매도 접수는 locked만 늘리고, 취소·만료는 locked만 줄이며, 매도 체결은 quantity와 locked를 같은 체결 수량만큼 줄인다. 매수 체결은 quantity를 늘린다 (`src/main/java/com/finmate/domain/stock/trading/StockHolding.java`).
- **[필수 불변식]** `quantity >= lockedQuantity >= 0`과 `availableQuantity >= 0`을 항상 유지한다. 한 주문의 예약 수량은 취소·만료·체결 중 정확히 한 종료 경로에서만 해제 또는 소비되어야 한다.
- **[현재 구현 사실]** 현재 확인된 매수 체결 경로는 최초 `StockHolding` 조회·생성 전에 해당 통화의 `InvestmentCashBalance`를 잠그며, 즉시 동기 접수 경로는 그보다 앞서 부모 `Investment`도 잠근다. 따라서 현재 경로의 최초 보유 행 생성은 같은 투자계좌·통화의 예수금 락으로 직렬화된다. 새 `StockHolding` 생성 경로는 기존 경로와 동일하게 해당 투자계좌·통화의 `InvestmentCashBalance` 락을 먼저 획득해야 한다. 다른 공통 락을 직렬화 기준으로 선택하려면 모든 `StockHolding` 생성 경로를 함께 전환하는 별도 승인된 마이그레이션이 필요하다 (`src/main/java/com/finmate/service/stock/trading/StockTradingCommandService.java`, `src/main/java/com/finmate/service/stock/trading/StockTradingExecutionService.java`, `src/main/java/com/finmate/service/stock/trading/StockTradingAssetService.java`).
- **[알려진 공백/위험]** 부분 체결 상태 모델은 있으나 실행 서비스는 남은 수량 전부만 체결하고 예약 자산을 부분 비율로 줄이지 않는다 (`docs/TRADING_FLOW.md`, `src/main/java/com/finmate/service/stock/trading/StockTradingExecutionService.java`).

## 4. 트랜잭션과 원장 원자성

- **[현재 구현 사실]** 계좌 이체, 일반↔투자 계좌 자금 이동, 환전, 주문 접수·취소 및 실시간 체결은 서비스의 `@Transactional` 경계 안에서 엔티티 변경과 원장 저장을 수행한다 (`src/main/java/com/finmate/service/normal/account/AccountService.java`, `src/main/java/com/finmate/service/investment/InvestmentService.java`, `src/main/java/com/finmate/service/investment/InvestmentCurrencyExchangeService.java`, `src/main/java/com/finmate/service/stock/trading/StockTradingCommandService.java`, `src/main/java/com/finmate/service/stock/trading/StockTradingExecutionService.java`). 구독 증감 이벤트는 `AFTER_COMMIT`에서 처리된다 (`src/main/java/com/finmate/service/stock/trading/StockTradingRealtimeSubscriptionService.java`).
- **[필수 불변식]** 잔액/수량/잠금/주문 상태와 대응 원장은 동일 DB 트랜잭션에서 함께 커밋하거나 함께 롤백한다. 원장에는 변경 전후 값과 동일한 통화·수량·정산액을 기록하고, 이미 커밋된 원장을 수정해 과거를 재작성하지 않는다. 수정이 필요하면 명시적인 보정 거래를 추가한다.
- **[현재 구현 사실]** 모의 체결 원장은 `StockTradeTransaction`이며 `externalExecutionId`는 KIS 체결 ID가 아니라 내부 UUID다 (`src/main/java/com/finmate/domain/stock/trading/StockTradeTransaction.java`, `src/main/java/com/finmate/service/stock/trading/StockTradingExecutionService.java`).
- **[알려진 공백/위험]** DB 장애·프로세스 중단을 포함한 롤백 원자성과 원장 불변성을 입증하는 통합 테스트가 현재 없다.

## 5. 락 순서와 동시성 경계

- **[현재 구현 사실]** 일반 계좌 간 이체는 두 `Account`를 ID 오름차순으로 `PESSIMISTIC_WRITE` 잠근다 (`src/main/java/com/finmate/service/normal/account/AccountService.java`, `src/main/java/com/finmate/repository/normal/account/AccountRepository.java`).
- **[현재 구현 사실]** 일반↔투자 계좌 이동은 방향과 무관하게 `Account` 후 `Investment`, 이어서 해당 `InvestmentCashBalance`를 잠근다 (`src/main/java/com/finmate/service/investment/InvestmentService.java`). 환전은 `Investment` 후 KRW 예수금, USD 예수금 순서다 (`src/main/java/com/finmate/service/investment/InvestmentCurrencyExchangeService.java`).
- **[현재 구현 사실]** 즉시 동기 주문 접수는 `Investment`를 먼저 잠근 뒤 매수면 통화별 예수금, 매도면 종목별 `StockHolding`을 잠근다. 즉시 체결되는 매도는 같은 트랜잭션에서 그 Holding 락을 유지한 채 이후 예수금 락을 요청하므로 실효 순서는 `Investment → Holding → Cash`다. 반면 실시간 처리는 종목별 활성 예약 행을 생성시각 순으로 잠그고 처리한 뒤 활성 주문 행을 생성시각 순으로 잠그며, 각 체결에서 `Cash → Holding` 순으로 요청한다 (`src/main/java/com/finmate/service/stock/trading/StockTradingCommandService.java`, `src/main/java/com/finmate/service/stock/trading/StockTradingLookupService.java`, `src/main/java/com/finmate/repository/stock/trading/StockOrderReservationRepository.java`, `src/main/java/com/finmate/repository/stock/trading/StockOrderRepository.java`, `src/main/java/com/finmate/service/stock/trading/StockTradingAssetService.java`, `src/main/java/com/finmate/service/stock/trading/StockTradingExecutionService.java`).
- **[필수 불변식]** 기존 다중 행 잠금 순서를 바꾸거나 새 금융 경로를 추가할 때는 모든 교차 경로의 순서를 하나의 전역 순서로 비교하고, 역순 획득을 만들지 않는다. 비관적 락은 해당 DB 트랜잭션 안에서만 보호하므로 Redis/KIS/WebSocket 처리까지 원자적이라고 간주하지 않는다.
- **[알려진 공백/위험]** 취소는 주문/예약을 `findById`로 읽고 상태를 검사하며 해당 주문 행에 비관적 락을 잡지 않는다. 따라서 실시간 체결·만료와 취소가 동시에 실행될 때 상태 전이와 자산 해제의 정확히 한 번 처리가 직렬화된다고 보장할 증거가 없다 (`src/main/java/com/finmate/service/stock/trading/StockTradingCommandService.java`).
- **[알려진 공백/위험]** 즉시 매도 체결의 `Holding → Cash`와 실시간 체결의 `Cash → Holding`은 같은 자산 행에 대한 역순 획득이다. 두 경로가 교차 실행되면 서로 상대 락을 기다리는 대기 사이클과 데드락 가능성이 있으며, 현재 구현이나 동시성 테스트로 안전하다고 주장할 수 없다.

## 6. 체결·취소·만료 경합

- **[필수 불변식]** 활성 주문/예약의 종료 상태는 체결, 취소, 만료, 예약 트리거 중 하나만 승리해야 한다. 승리한 전이만 자산을 소비/해제하고 원장을 만들며, 뒤늦은 경로는 재처리 없이 종료해야 한다.
- **[현재 구현 사실]** 실시간 처리 한 트랜잭션 안에서는 예약을 먼저, 일반 주문을 다음에 처리한다. 활성 목록을 비관적 락으로 가져와 만료를 우선 검사하고, 거래 시간이면서 가격 조건이 맞으면 남은 수량 전부를 체결한다 (`src/main/java/com/finmate/service/stock/trading/StockTradingExecutionService.java`).
- **[현재 구현 사실]** 만료는 별도 스케줄러가 아니라 해당 종목의 실시간 payload 처리 때 수행된다. 시장가 주문에는 만료 시각이 없고 실행 가격이 없을 때의 자동 만료 경로도 없다 (`docs/TRADING_FLOW.md`).
- **[알려진 공백/위험]** 취소 대 체결/만료, 중복 payload, 동시 payload, 예약 트리거 대 취소를 검증하는 경쟁 테스트가 없다. 위 경합은 현재 안전하다고 간주하지 않으며, 관련 변경 전 주문/예약 행의 공통 잠금 또는 조건부 상태 전이 전략과 회귀 테스트가 필요하다.

## 7. KIS와 내부 모의 거래의 경계

- **[현재 구현 사실]** KIS 연동은 종목/시장 기준정보, 일봉·분봉, 랭킹, 환율 및 WebSocket 실시간 가격/호가를 제공한다 (`docs/KIS_INTEGRATION.md`, `src/main/java/com/finmate/infra/kis`). 프로젝트의 주문은 KIS 주문 API로 전송되지 않는다. 내부 서비스가 KIS 시세를 체결 판단 입력으로 사용해 DB의 모의 주문, 예수금, 보유 수량과 체결 원장을 갱신한다 (`src/main/java/com/finmate/service/stock/trading/StockTradingRealtimePriceService.java`, `src/main/java/com/finmate/service/stock/trading/StockTradingExecutionService.java`).
- **[필수 불변식]** KIS 시세 수신 성공을 외부 주문 접수·체결 확인으로 표현하지 않는다. KIS 장애·지연·중복·stale 데이터는 내부 금융 상태를 부분 커밋하게 해서는 안 되며, 외부 실제 주문 기능을 도입하려면 멱등키, 외부 주문/체결 ID, 재조정(reconciliation), 타임아웃 및 불확실 상태를 별도 설계하고 승인받아야 한다.

## 8. 변경 시 최소 증거

금융 상태 전이, 계산식, 트랜잭션 경계 또는 락 순서를 바꾸는 변경은 최소한 다음 증거를 함께 제출한다.

1. 변경 전 동작을 고정하는 단위/통합 회귀 테스트와 정상·거부 경계값: 0, 음수, 잔액/수량 정확히 일치, 최소 통화 단위, 허용/초과 scale, 반올림 경계.
2. 보존식 검증: 일반 이체, 일반↔투자 이동, 예수금 lock/release/settle, 매수·매도, 환전에 대해 변경 전후 잔액·locked·수량과 원장 스냅샷을 함께 검증.
3. 실패 주입 통합 테스트: 원장 저장 또는 상태 갱신 실패 시 잔액·수량·주문·원장이 모두 롤백되는지 검증.
4. 실제 DB를 사용한 동시성 테스트: 반대 방향 이체, 동시 주문, 이중 취소, 체결 대 취소/만료, 예약 트리거 대 취소 및 최초 행 생성 경합을 timeout과 최종 보존식으로 검증.
5. 계산 정책 변경이면 통화별 scale·rounding 기대값과 수수료/세금/환전 예제를 명시하고 `./gradlew test` 및 필요 범위의 빌드 결과를 첨부.

**[알려진 공백/위험]** 현재 `src/test/java/com/finmate/FinmateApplicationTests.java`에는 컨텍스트 로드 테스트만 있어 위 금융 불변식을 입증하지 못한다. 테스트가 추가되기 전에는 현재 구현을 운영 수준의 동시성·원자성 보장으로 주장하지 않는다.
