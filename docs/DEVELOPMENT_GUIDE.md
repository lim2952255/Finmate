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

# Google 로그인을 사용할 때만 활성화
GOOGLE_OAUTH_ENABLED=true
GOOGLE_CLIENT_ID=change-me.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=change-me

# Kakao 로그인을 사용할 때만 활성화
KAKAO_OAUTH_ENABLED=true
KAKAO_CLIENT_ID=change-me
KAKAO_CLIENT_SECRET=change-me

# Naver 로그인을 사용할 때만 활성화
NAVER_OAUTH_ENABLED=true
NAVER_CLIENT_ID=change-me
NAVER_CLIENT_SECRET=change-me
```

현재 `.env`에 추가 KIS 운영·모의 계좌 관련 이름이 존재할 수 있으나 `application.properties`와 `KisProperties`가 직접 읽는 것은 위 공통 키들이다. `KIS_ACCESS_TOKEN`도 현재 코드에서 직접 주입하지 않는다.

Google 로그인을 사용하지 않으면 `GOOGLE_OAUTH_ENABLED`를 생략하거나 `false`로 둔다. 사용할 때는 Google Cloud Console에서 Web application OAuth client를 만들고 로컬 Authorized redirect URI를 다음과 같이 등록한다.

```text
http://localhost:8080/login/oauth2/code/google
```

배포 환경에서는 `{서비스 base URL}/login/oauth2/code/google`을 별도로 등록한다. Client ID와 Client Secret은 `.env` 또는 운영 비밀 저장소로만 주입하고 저장소에 커밋하지 않는다.

Kakao 로그인은 [Kakao Developers](https://developers.kakao.com/)에서 애플리케이션을 만든 뒤 Kakao Login과 OpenID Connect를 활성화한다. `KAKAO_CLIENT_ID`에는 REST API key를, `KAKAO_CLIENT_SECRET`에는 Client secret code를 사용하고 다음 Redirect URI를 등록한다.

```text
http://localhost:8080/login/oauth2/code/kakao
```

현재 코드는 OIDC `openid`, `profile_nickname` 범위만 요청한다. 이메일이 필요하면 Kakao Developers에서 이메일 동의 항목 권한을 확인한 뒤 코드의 scope를 함께 확장해야 한다.

Naver 로그인은 [Naver Developers](https://developers.naver.com/)에서 애플리케이션을 등록하고 사용 API로 `네이버 로그인`을 선택한다. 서비스 URL은 `http://localhost:8080`, Callback URL은 다음과 같이 등록한다.

```text
http://localhost:8080/login/oauth2/code/naver
```

발급된 Client ID와 Client Secret을 각각 `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`에 설정한다. 배포 환경에서는 Kakao와 Naver에도 실제 HTTPS base URL을 사용한 callback을 별도로 등록한다.

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
TRADING_EXPIRATION_INTERVAL_MILLIS=10000
TRADING_EXPIRATION_INITIAL_DELAY_MILLIS=0
TRADING_EXPIRATION_ENABLED=true
```

국내 업종코드 파일은 국내 종목 마스터와 같은 `STOCK_MASTER_DOMESTIC_SYNC_CRON` / `STOCK_MASTER_DOMESTIC_SYNC_ZONE` 설정으로 함께 갱신된다.
주문 만료 스케줄러는 기본 10초 간격으로 만료된 활성 주문·예약을 처리하고, 서버 시작 직후에는 중단 중 만료된 건을 즉시 복구한다.

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

통합 테스트는 JVM마다 새 MySQL Testcontainer를 만들고 여러 Spring 테스트 컨텍스트가 이를 공유한다.
테스트 프로필은 `ddl-auto=update`로 스키마를 한 번 생성하며, 각 테스트 시작 전 공통 지원 클래스가 테스트 스키마의 모든 테이블을 비운다.
따라서 컨텍스트별 `create-drop` 종료 작업이 같은 FK를 반복 삭제하는 로그를 만들지 않는다.

```bash
# 전체 테스트
./gradlew test

# Gradle UP-TO-DATE 판정과 관계없이 전체 테스트 강제 재실행
./gradlew test --rerun-tasks --no-daemon --console=plain

# 정리 후 전체 빌드(테스트 포함)
./gradlew clean build

# 실행 가능한 jar 생성
./gradlew bootJar
```

현재 회귀 suite는 단위·MVC·MySQL 통합·MySQL 동시성 테스트 170개로 구성된다. 계좌이체, 일반↔투자계좌 자금 이동, 환전, 주문·예약·체결·취소·만료, 원장 rollback과 주요 락 경합을 검증한다.

두 번째 `./gradlew test`가 매우 빠르게 끝나고 모든 task가 `UP-TO-DATE`라면 테스트를 다시 실행한 것이 아니다. 실제 전체 회귀를 다시 실행하려면 위의 `--rerun-tasks` 명령을 사용한다.

별도의 Checkstyle, SpotBugs, PMD, JaCoCo, 전용 lint/typecheck Gradle task는 `build.gradle`에서 확인되지 않는다.

## 6. GitHub Actions CI

`.github/workflows/ci.yml`은 다음 경우 전체 테스트를 실행한다.

- `main`을 대상으로 pull request를 생성하거나 새 commit을 push한 경우
- `main`에 commit이 push된 경우
- Actions 화면에서 수동으로 실행한 경우

같은 pull request 또는 branch에 새 실행이 시작되면 이전 실행은 취소한다. CI는 Java 17과 프로젝트 Gradle Wrapper를 사용하며, MySQL은 별도 service container가 아니라 테스트 코드의 MySQL 8.4 Testcontainers가 실행한다. KIS·개발 MySQL·Redis credential은 필요하지 않다.

테스트 실패 시 Actions 실행도 실패하며, JUnit XML·HTML test report와 Gradle 문제 report를 14일 동안 artifact로 보관한다. branch protection과 required status check 설정은 저장소 운영 설정이므로 이 workflow가 자동으로 변경하지 않는다.

## 7. DB 스키마

`spring.jpa.hibernate.ddl-auto=update`이므로 애플리케이션 시작 시 엔티티 변경이 DB에 반영된다. Flyway/Liquibase 마이그레이션은 **현재 구현되지 않음**. 운영 또는 협업 환경에서 재현 가능한 스키마 변경 절차는 **확인 필요**.

## 8. 자주 발생할 수 있는 실행 오류

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

### 종목 채팅 연결 또는 기록 조회 실패

- 채팅 WebSocket은 `/ws/chat`, 과거 기록은 `/api/stocks/{stockId}/chat/messages`를 사용한다.
- `/ws/chat` handshake에는 로그인 HTTP 세션이 필요하다. 로그인 쿠키 없이 연결하면 정책 위반 상태로 종료된다.
- 메시지 기록은 MySQL에 남지만 접속 인원과 실시간 전파 대상은 단일 애플리케이션 JVM 메모리에 있다.
- 여러 애플리케이션 인스턴스를 실행하면 인스턴스 사이 실시간 메시지가 자동 전파되지 않는다. 현재 Redis Pub/Sub은 구현되지 않았다.

### Spring Security 로그인 문제

`SecurityConfig`의 공개 경로, 로그인 처리 URL(`/login`), 아이디 파라미터명(`userId`)과 로그아웃 URL(`/logout`)을 확인한다. 인증 정보는 `FinMateUserDetailsService`가 조회하고 `BCryptPasswordEncoder`가 비밀번호를 검증한다. 로그인·로그아웃 POST는 CSRF 토큰이 필요하며 Thymeleaf의 `th:action` 폼은 토큰을 자동 렌더링한다. 인증 성공 상태는 서버 HTTP session의 `SecurityContext`에 저장된다.

소셜 로그인 버튼이 보이지 않으면 해당 공급자의 `*_OAUTH_ENABLED=true`와 Client ID/Secret 주입을 확인한다. callback 오류가 발생하면 실제 접속 주소와 공급자 콘솔에 등록한 Redirect URI가 정확히 일치하는지 확인한다. 인증 성공 후에는 `OAuthAccount(provider, providerSubject)`로 로컬 `User`를 찾으며, 최초 사용자는 로컬 비밀번호 없이 생성된다.

Kakao에서 ID token이 발급되지 않으면 Kakao Login의 OpenID Connect 활성화 여부를 확인한다. Naver 사용자 정보 오류가 발생하면 애플리케이션의 제공 정보 설정과 `response.id` 반환 여부를 확인한다.

## 9. 테스트 보강 우선순위

1. Testcontainers 기반 MySQL 계좌이체 동시성·데드락 회귀 테스트
2. 일반↔투자 자금 이동 원자성 테스트
3. 매수·매도·취소·만료 정산 단위 테스트
4. 실시간 체결과 취소 경쟁 테스트
5. KIS payload parser와 rate-limit 재시도 테스트
6. Redis 직렬화·TTL·장애 fallback 테스트
