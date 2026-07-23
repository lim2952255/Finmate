# FinMate 개발 가이드

## 1. 전제 조건

- JDK 17
- Docker와 Docker Compose
- 프로젝트에 포함된 Gradle Wrapper 사용 권장
- KIS 연동 기능을 사용할 경우 유효한 KIS app key와 secret

## 2. 환경변수

`application.properties`는 루트 `.env`를 optional properties 파일로 읽는다. `.env`는 `.gitignore`에 포함되어 있다.

최소 예시는 다음과 같다. 실제 비밀값은 저장소에 커밋하지 않는다.

```properties
MYSQL_PORT=3306
MYSQL_ROOT_PASSWORD=change-me
MYSQL_DATABASE=finmate
MYSQL_USER=finmate
MYSQL_PASSWORD=change-me

SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/finmate?serverTimezone=Asia/Seoul&useUnicode=true&characterEncoding=utf8mb4&connectionCollation=utf8mb4_unicode_ci
SPRING_DATASOURCE_USERNAME=finmate
SPRING_DATASOURCE_PASSWORD=change-me

REDIS_PASSWORD=change-me

KIS_BASE_URL=https://openapi.koreainvestment.com:9443
KIS_APP_KEY=change-me
KIS_APP_SECRET=change-me
KIS_REQUEST_INTERVAL_MILLIS=700
KIS_WEBSOCKET_URL=ws://ops.koreainvestment.com:21000
KIS_WEBSOCKET_PATH=/tryitout
KIS_REALTIME_UNSUBSCRIBE_GRACE_MILLIS=60000
```

현재 `.env`에 추가 KIS 운영·모의 계좌 관련 이름이 존재할 수 있으나 `application.properties`와 `KisProperties`가 직접 읽는 것은 위 공통 키들이다. `KIS_ACCESS_TOKEN`도 현재 코드에서 직접 주입하지 않는다.

스케줄 조정용 선택 환경변수:

```properties
STOCK_MASTER_DOMESTIC_SYNC_CRON=0 0 8 * * MON-FRI
STOCK_MASTER_DOMESTIC_SYNC_ZONE=Asia/Seoul
STOCK_MASTER_NASDAQ_SYNC_CRON=0 0 8 * * MON-FRI
STOCK_MASTER_NASDAQ_SYNC_ZONE=America/New_York
STOCK_RANKING_REFRESH_INTERVAL_MILLIS=10000
STOCK_RANKING_INITIAL_DELAY_MILLIS=100
STOCK_RANKING_OPEN_CACHE_TTL_SECONDS=30
STOCK_RANKING_CLOSED_CACHE_TTL_SECONDS=86400
```

국내 업종코드 파일은 국내 종목 마스터와 같은 `STOCK_MASTER_DOMESTIC_SYNC_CRON` / `STOCK_MASTER_DOMESTIC_SYNC_ZONE` 설정으로 함께 갱신된다.

## 3. MySQL과 Redis 실행

루트 `docker-compose.yml`은 MySQL 8.4와 Redis 7.2를 제공한다.

```bash
docker compose up -d mysql redis
docker compose ps
docker compose logs -f mysql redis
```

종료:

```bash
docker compose down
```

`docker compose down -v`는 DB와 Redis 볼륨 데이터를 삭제하므로 일반 개발 종료 명령으로 사용하지 않는다.

## 4. 애플리케이션 실행

```bash
./gradlew bootRun
```

기본 datasource는 `localhost:3306/finmate`, 사용자 `finmate`, 비밀번호 `finmate-password`다. Docker Compose의 값과 일치하도록 환경변수를 설정해야 한다. Redis 기본 주소는 `localhost:6379`다.

KIS 키가 비어 있어도 context 생성 자체는 지연 호출 구조상 가능하지만, 서버 시작 직후 랭킹 스케줄러가 기본 100ms 뒤 실행되어 KIS 관련 경고를 반복할 수 있다. 로컬 UI만 확인할 때는 초기 지연을 크게 설정할 수 있다.

```bash
STOCK_RANKING_INITIAL_DELAY_MILLIS=600000 ./gradlew bootRun
```

## 5. 테스트와 빌드

금융 상태 전이, 계산식, 트랜잭션 경계 또는 락 순서를 변경할 때 필요한 회귀·보존식·실패 주입·동시성 증거는 [금융 불변식](FINANCIAL_INVARIANTS.md)의 변경 기준을 함께 따른다.

```bash
# 전체 테스트
./gradlew test

# 정리 후 전체 빌드(테스트 포함)
./gradlew clean build

# 실행 가능한 jar 생성
./gradlew bootJar
```

현재 테스트는 `FinmateApplicationTests.contextLoads()` 한 개뿐이며 랭킹 스케줄러 초기 지연을 10분으로 덮어쓴다. 계좌이체, 락 순서, 주문·정산, KIS parser/client, Redis 캐시 테스트는 **현재 구현되지 않음**.

별도의 Checkstyle, SpotBugs, PMD, JaCoCo, 전용 lint/typecheck Gradle task는 `build.gradle`에서 확인되지 않는다.

## 6. DB 스키마

`spring.jpa.hibernate.ddl-auto=update`이므로 애플리케이션 시작 시 엔티티 변경이 DB에 반영된다. Flyway/Liquibase 마이그레이션은 **현재 구현되지 않음**. 운영 또는 협업 환경에서 재현 가능한 스키마 변경 절차는 **확인 필요**.

## 7. 자주 발생할 수 있는 실행 오류

### MySQL 연결 실패

- Docker container 상태와 `MYSQL_PORT` 확인
- `SPRING_DATASOURCE_*`가 Compose의 DB·사용자·비밀번호와 같은지 확인
- URL의 DB 이름과 `MYSQL_DATABASE`가 같은지 확인

### Redis 인증 실패

- Compose는 `--requirepass ${REDIS_PASSWORD}`를 사용한다.
- 앱의 `REDIS_PASSWORD`가 같아야 한다.
- Redis가 없어도 일부 화면은 열릴 수 있지만 랭킹 캐시는 빈 결과와 경고 로그를 낸다.

### KIS credential 오류

- `KIS_APP_KEY`, `KIS_APP_SECRET`, `KIS_BASE_URL` 확인
- REST 호출 시 값이 비면 `KisProperties.validateApiCredentials()`에서 예외가 발생한다.
- 실전/모의 URL과 키 조합을 코드가 자동 선택하지 않는다. 선택한 endpoint와 credential 조합은 **확인 필요**.

### KIS 호출 제한

- 로그 body에 `EGW00201`이 있으면 client가 총 5회까지 시도한다.
- 네트워크 예외, HTTP 429, HTTP 5xx도 같은 재시도 경로를 사용한다.
- KIS WebSocket 최초 연결도 총 5회까지 시도한다.
- 반복되면 `KIS_REQUEST_INTERVAL_MILLIS`를 늘린다.
- 공식 계정·API별 제한과 적정값은 **확인 필요**.

### WebSocket 실시간 값이 없음

- 종목 마스터에 `symbol`/`realtimeSymbol`이 올바르게 저장되었는지 확인
- 브라우저가 `/ws/stocks`에 연결하고 구독 메시지를 보냈는지 확인
- KIS approval key 발급과 WebSocket endpoint 확인
- 최신값은 JVM 메모리이므로 재시작 직후에는 새 payload가 올 때까지 비어 있다.

### Spring Security 기본 동작과 인터셉터 충돌

`SecurityConfig`에는 `PasswordEncoder`만 있고 명시적 `SecurityFilterChain` bean은 없다. 실제 로그인 제어는 MVC 인터셉터와 HTTP Session이 담당한다. Spring Boot 3.5.15 자동 설정에서의 최종 필터 동작은 실행 환경에서 **확인 필요**.

## 8. 테스트 보강 우선순위

1. Testcontainers 기반 MySQL 계좌이체 동시성·데드락 회귀 테스트
2. 일반↔투자 자금 이동 원자성 테스트
3. 매수·매도·취소·만료 정산 단위 테스트
4. 실시간 체결과 취소 경쟁 테스트
5. KIS payload parser와 rate-limit 재시도 테스트
6. Redis 직렬화·TTL·장애 fallback 테스트
