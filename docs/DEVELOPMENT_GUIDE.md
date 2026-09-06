# FinMate 개발 가이드

## 1. 전제 조건

- JDK 17
- Node.js 20.19 이상 또는 22.12 이상과 npm
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

SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/finmate?serverTimezone=Asia/Seoul&useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci
SPRING_DATASOURCE_USERNAME=finmate
SPRING_DATASOURCE_PASSWORD=change-me

REDIS_PASSWORD=change-me

# OAuth 로그인 성공·실패 후 복귀할 React 주소
FINMATE_FRONTEND_BASE_URL=http://localhost:5173

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

# NAVER API HUB 뉴스 검색을 사용할 때 설정
NAVER_API_HUB_CLIENT_ID=change-me
NAVER_API_HUB_CLIENT_SECRET=change-me
NAVER_NEWS_CACHE_TTL_HOURS=6

# 종목 뉴스와 시장 리포트에서 KEYWORD, TF_IDF, KOREAN_TF_IDF, EMBEDDING 중 사용할 전략
NEWS_RANKING_STRATEGY=KOREAN_TF_IDF
# EMBEDDING 전략에서 사용할 로컬 모델 경로
NEWS_EMBEDDING_MODEL_PATH=models/multilingual-e5-small/model.onnx
NEWS_EMBEDDING_TOKENIZER_PATH=models/multilingual-e5-small/tokenizer.json

# 최종 종목 뉴스의 호재·보통·악재 판정 모델
NEWS_SENTIMENT_MODEL_PATH=models/kr-finbert-sentiment/model.onnx
NEWS_SENTIMENT_TOKENIZER_PATH=models/kr-finbert-sentiment/tokenizer.json
NEWS_SENTIMENT_MAX_TOKEN_LENGTH=256
NEWS_SENTIMENT_DIRECTIONAL_THRESHOLD=0.65
```

현재 `.env`에 추가 KIS 운영·모의 계좌 관련 이름이 존재할 수 있으나 `application.properties`와 `KisProperties`가 직접 읽는 것은 위 공통 키들이다. `KIS_ACCESS_TOKEN`도 현재 코드에서 직접 주입하지 않는다.

Google 로그인을 사용하지 않으면 `GOOGLE_OAUTH_ENABLED`를 생략하거나 `false`로 둔다. 사용할 때는 Google Cloud Console에서 Web application OAuth client를 만들고 로컬 Authorized redirect URI를 다음과 같이 등록한다.

```text
http://localhost:5173/login/oauth2/code/google
```

OAuth 성공·실패 후 이동 주소는 `FINMATE_FRONTEND_BASE_URL`에서 결정한다. 운영에서는
이 값을 실제 HTTPS 서비스 base URL로 설정해야 한다.
React 로그인 화면은 OAuth를 시작하기 전에 사용자가 보던 내부 경로를 HTTP session에 저장한다.
인증 성공 후에는 이 경로를 한 번 사용해 복귀하고 세션에서 제거한다. `/`로 시작하지 않거나
`//`로 시작하는 외부 주소 형식은 저장하지 않고 `/home`으로 대체한다.

배포 환경에서는 `{서비스 base URL}/login/oauth2/code/google`을 별도로 등록한다. Client ID와 Client Secret은 `.env` 또는 운영 비밀 저장소로만 주입하고 저장소에 커밋하지 않는다.

Kakao 로그인은 [Kakao Developers](https://developers.kakao.com/)에서 애플리케이션을 만든 뒤 Kakao Login과 OpenID Connect를 활성화한다. `KAKAO_CLIENT_ID`에는 REST API key를, `KAKAO_CLIENT_SECRET`에는 Client secret code를 사용하고 다음 Redirect URI를 등록한다.

```text
http://localhost:5173/login/oauth2/code/kakao
```

현재 코드는 OIDC `openid`, `profile_nickname` 범위만 요청한다. 이메일이 필요하면 Kakao Developers에서 이메일 동의 항목 권한을 확인한 뒤 코드의 scope를 함께 확장해야 한다.

Naver 로그인은 [Naver Developers](https://developers.naver.com/)에서 애플리케이션을 등록하고 사용 API로 `네이버 로그인`을 선택한다. 서비스 URL은 `http://localhost:5173`, Callback URL은 다음과 같이 등록한다.

```text
http://localhost:5173/login/oauth2/code/naver
```

발급된 Client ID와 Client Secret을 각각 `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`에 설정한다. 배포 환경에서는 Kakao와 Naver에도 실제 HTTPS base URL을 사용한 callback을 별도로 등록한다.

종목 뉴스 탭은 NAVER Cloud Platform의 NAVER API HUB에서 발급한 별도 인증정보를 사용한다. Client ID와
Client Secret을 각각 `NAVER_API_HUB_CLIENT_ID`, `NAVER_API_HUB_CLIENT_SECRET`에 설정한다. 이 값은
네이버 로그인용 `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`과 다른 자격증명이다. 뉴스 검색 결과는 종목별로
MySQL에 저장되고 `NAVER_NEWS_CACHE_TTL_HOURS`가 지난 뒤 다음 조회에서 갱신되며 기본값은 6시간이다.
검색어는 `{종목명} 시장정보`이고, 관련도순 후보 80건을 한 번 조회해 `NEWS_RANKING_STRATEGY`로 선택한
전략 하나의 Top 10만 계산해 종목 캐시에 저장한다. 개선된 TF-IDF는 Lucene Nori로 한국어 조사와 어미의
영향을 줄이고 복합어를 분해한 토큰을 사용한다.

`NEWS_RANKING_STRATEGY`는 종목 뉴스와 시장 리포트에서 `KEYWORD`, `TF_IDF`, `KOREAN_TF_IDF`,
`EMBEDDING` 중 하나를 선택하는 설정이며 기본값은 `KOREAN_TF_IDF`다. 임베딩 전략을 사용하려면 다음 스크립트로
저장소에 고정된 E5-small 리비전의 ONNX 모델과
토크나이저 파일을 내려받는다. `models/`는 Git에서 제외된다.

```bash
./scripts/download-e5-small-model.sh
```

`EMBEDDING`을 선택하면 모델과 토크나이저를 한 번 로딩하고 이후 뉴스 캐시 갱신에서 재사용한다.
운영 cosine 유사도 임계값은 오프라인 평가로 확정한 `0.92`이며, 후보는 기존 키워드 순서대로 검사하고 선택된 모든 기사와의 최대
유사도가 이 값보다 낮을 때만 최대 10건까지 포함한다. Docker에서 사용할 때는 다운로드한 모델 디렉터리를
컨테이너에 마운트하고 두 모델 경로를 컨테이너 내부 절대 경로로 지정해야 한다.

선택된 랭킹 전략과 관계없이 종목 뉴스와 시장 리포트 Top 10에는 `snunlp/KR-FinBert-SC` 기반 감성 분석을 적용한다.
다음 스크립트는 모델의 고정 리비전을 ONNX로 내보내고 CPU 추론용 INT8 동적 양자화를 수행한다.

```bash
./scripts/download-kr-finbert-sentiment-model.sh
```

스크립트 실행에는 Python 3과 venv가 필요하며 변환용 패키지는 임시 디렉터리에만 설치된다. 애플리케이션은
제목과 네이버 요약문을 결합해 최종 기사들을 한 배치로 분석한다. 모델의 `positive`, `neutral`, `negative`는
각각 화면의 `호재`, `보통`, `악재`로 표시한다. 긍정 또는 부정 예측의 최대 확률이
`NEWS_SENTIMENT_DIRECTIONAL_THRESHOLD`보다 낮으면 보수적으로 `보통`으로 처리한다.
기존 캐시에 감성 결과가 없으면 TTL과 관계없이 해당 종목 또는 시장 주제 뉴스를 한 번 다시 조회해 새 형식으로 저장한다.

이 모델 저장소에는 명시적인 라이선스가 없으므로 공개 또는 상업 운영에 배포하기 전 모델 제작자에게 사용 조건을
확인해야 한다. Docker 로컬 구성은 `models/kr-finbert-sentiment`를 자동 마운트한다. EC2에서는 다음처럼 운영
마운트 경로에 모델을 한 번 준비한다.

```bash
./scripts/download-kr-finbert-sentiment-model.sh /opt/finmate/models/kr-finbert-sentiment
```

같은 인증정보는 `/investments/reports`의 시장 리포트에도 사용한다. KOSPI, KOSDAQ, NASDAQ, S&P 500, 금리, 환율
각 주제는 후보 80건 중 제목 2점·요약문 보조 1점의 키워드 점수와 발행일시로 정렬한 상위 10건만
주제별 MySQL 캐시에 저장한다. 같은 키워드가 제목과 요약문에 모두 있어도 한 번만 점수화한다.
이 캐시는 사용자별 데이터가 아니며 모든 사용자가 `NAVER_NEWS_CACHE_TTL_HOURS` 동안 공유한다.

### 뉴스 랭킹 오프라인 평가

평가 코드는 `src/evaluation/java` source set에 있으며 운영 `bootJar`에 포함되지 않는다. 평가용 Gradle 설정은
`gradle/evaluation.gradle`에 분리되어 있고 `-PwithEvaluation`을 지정할 때만 로드된다. KOSPI, KOSDAQ,
NASDAQ, S&P 500, 금리, 환율과 삼성전자, SK하이닉스, NAVER, 카카오, KB금융, 현대차 후보를
네이버 API에서 JSON으로 고정하려면 다음 명령을 실행한다.

평가 Gradle task는 프로젝트 루트의 기존 `.env`를 자동으로 읽는다. 현재 셸이나 CI에 같은 환경변수가
이미 설정돼 있으면 외부 값을 우선하므로 로컬 `.env`가 배포 설정을 덮어쓰지 않는다.
기존 로컬 설정의 네이버 헤더 이름인 `X-NCP-APIGW-API-KEY-ID:`와 `X-NCP-APIGW-API-KEY:`도 각각
`NAVER_API_HUB_CLIENT_ID`, `NAVER_API_HUB_CLIENT_SECRET`으로 변환해 평가 프로세스에 전달한다.

```bash
./gradlew -PwithEvaluation collectNewsEvaluationDataset
```

평가 실행에는 E5 모델과 프로젝트 루트 `.env`의 다음 설정이 필요하다.

```env
OPENAI_API_KEY=...
NEWS_EVALUATION_JUDGE_MODEL=사용할-고정-모델-ID
```

별도로 `source .env`를 실행하지 않고 바로 평가할 수 있다.

평가기는 기본적으로 TF-IDF와 한국어 TF-IDF의 `0.20,0.30,0.40,0.50`, 임베딩의
`0.90,0.92,0.94,0.96`을 모두 실행한다. 다른 범위를 비교하려면 루트 `.env`에서 다음 목록을 변경한다.

```env
NEWS_EVALUATION_TF_IDF_THRESHOLDS=0.20,0.30,0.40,0.50
NEWS_EVALUATION_KOREAN_TF_IDF_THRESHOLDS=0.20,0.30,0.40,0.50
NEWS_EVALUATION_EMBEDDING_THRESHOLDS=0.90,0.92,0.94,0.96
```

```bash
./gradlew -PwithEvaluation evaluateNewsRanking
```

평가기는 동일 후보에 네 전략을 실행하고 Top 10 합집합을 OpenAI Responses API의 Structured Outputs로
판정한다. 생성된 관련도·사건 라벨로 nDCG@10, 중복률, 투자 관련 고유 사건 수, Yield@10과 처리 시간을
계산한다. 원본 데이터·생성 라벨·결과 CSV는 `evaluation/news-ranking` 아래 로컬 산출물이며 Git에서 제외한다.
자세한 실행 방법은 `evaluation/news-ranking/README.md`를 참고한다.

스케줄 조정용 선택 환경변수:

```properties
STOCK_MASTER_DOMESTIC_SYNC_CRON=0 0 8 * * MON-FRI
STOCK_MASTER_DOMESTIC_SYNC_ZONE=Asia/Seoul
STOCK_MASTER_NASDAQ_SYNC_CRON=0 0 8 * * MON-FRI
STOCK_MASTER_NASDAQ_SYNC_ZONE=America/New_York
STOCK_MASTER_SYNC_ON_STARTUP=false
STOCK_RANKING_REFRESH_INTERVAL_MILLIS=10000
STOCK_RANKING_INITIAL_DELAY_MILLIS=100
STOCK_RANKING_OPEN_CACHE_TTL_SECONDS=30
STOCK_RANKING_CLOSED_CACHE_TTL_SECONDS=86400
TRADING_EXPIRATION_INTERVAL_MILLIS=10000
TRADING_EXPIRATION_INITIAL_DELAY_MILLIS=0
TRADING_EXPIRATION_ENABLED=true
STOCK_CONCEPT_SYNC_ENABLED=false
STOCK_CONCEPT_SYNC_ON_STARTUP=false
STOCK_CONCEPT_SYNC_CRON=0 0 4 * * MON
STOCK_CONCEPT_SYNC_ZONE=Asia/Seoul
```

국내 업종코드 파일은 국내 종목 마스터와 같은 `STOCK_MASTER_DOMESTIC_SYNC_CRON` / `STOCK_MASTER_DOMESTIC_SYNC_ZONE` 설정으로 함께 갱신된다.
`STOCK_MASTER_SYNC_ON_STARTUP=true`로 설정하면 애플리케이션을 시작할 때 국내·나스닥 종목 마스터를 한 번 동기화한다.
주문 만료 스케줄러는 기본 10초 간격으로 만료된 활성 주문·예약을 처리하고, 서버 시작 직후에는 중단 중 만료된 건을 즉시 복구한다.
공식 주식 개념 카드 동기화는 검토 전 DB 변경을 막기 위해 기본 비활성화되어 있다. `STOCK_CONCEPT_SYNC_ENABLED=true`로 활성화하면
기본적으로 매주 월요일 오전 4시에 `src/main/resources/stock-concepts/stock-concepts.yml`을 DB에 멱등 반영한다.
변경사항을 즉시 반영할 때는 `STOCK_CONCEPT_SYNC_ON_STARTUP=true`로 애플리케이션을 한 번 시작한 뒤 다시 `false`로 되돌린다.

## 3. MySQL과 Redis 실행

루트 `docker-compose.local.yml`은 로컬 개발에 필요한 MySQL 8.4와 Redis 7.2를 제공한다.
Spring과 React를 IDE에서 직접 실행할 때는 인프라 서비스만 시작한다.

```bash
docker compose --env-file .env -f docker-compose.local.yml up -d mysql redis
docker compose --env-file .env -f docker-compose.local.yml ps
docker compose --env-file .env -f docker-compose.local.yml logs -f mysql redis
```

종료:

```bash
docker compose --env-file .env -f docker-compose.local.yml down
```

`docker compose down -v`는 DB와 Redis 볼륨 데이터를 삭제하므로 일반 개발 종료 명령으로 사용하지 않는다.

## 4. 애플리케이션 실행

```bash
# 터미널 1: API, 인증, WebSocket 서버
./gradlew bootRun

# 터미널 2: React 화면과 정적 자산을 제공하는 개발 서버
cd frontend
npm ci
npm run dev
```

브라우저는 `http://localhost:5173/home`, `http://localhost:5173/investment-learning`,
`http://localhost:5173/investments/reports` 또는
`http://localhost:5173/investments/stocks/market-movers`로 접속한다.
Vite가 React 화면과 정적 자산을 제공하고 `/api`, 로그인·OAuth 경로와 WebSocket 요청을
`http://localhost:8080`의 Spring 서버로 전달한다. `http://localhost:8080`은 백엔드 전용이며 React 화면을
제공하지 않는다.

Gradle과 프런트엔드 빌드는 독립되어 있다. `./gradlew bootJar`가 만든 JAR에는 React 빌드 결과가 들어가지
않는다. 운영에서는 `frontend`에서 `npm ci && npm run build`로 만든 `dist`를 Nginx 같은 정적 웹 서버에
배포하고, `/api`, 로그인·OAuth, `/ws` 요청만 Spring으로 reverse proxy한다.

Vite proxy는 개발 요청의 `localhost:5173` Host와 프로토콜을 forwarded headers로 Spring에 전달하고,
Spring redirect의 내부 `localhost:8080` 주소도 요청 origin으로 재작성한다. 운영 Nginx도 다음 헤더를
Spring에 전달해야 한다.

```nginx
proxy_set_header Host $host;
proxy_set_header X-Forwarded-Host $host;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Port $server_port;
```

따라서 운영 redirect는 Spring의 내부 `8080`이나 포트 번호 `443`을 직접 하드코딩하지 않고 사용자가 접속한
`https://서비스도메인`을 기준으로 생성된다.

기본 datasource는 `localhost:3306/finmate`, 사용자 `finmate`, 비밀번호 `finmate-password`다. Docker Compose의 값과 일치하도록 환경변수를 설정해야 한다. Redis 기본 주소는 `localhost:6379`다.

종목 분봉 캐시의 TTL은 기본 3일이며 `STOCK_DETAIL_MINUTE_CHART_CACHE_DAYS`로 조정할 수 있다.
KIS REST로 조회한 1분봉 스냅샷은 DB에 적재되지 않고 Redis/JVM fallback에 저장된다. 화면의 미확정 봉은
브라우저가 WebSocket 체결 메시지로 실시간 갱신한다. 국내 과거 분봉과 해외 종목 분봉은
KIS 실전 환경 전용이므로 해당 기능을 로컬에서 실제 호출하려면 실전 REST 자격 증명이 필요하다.

KIS 키가 비어 있어도 context 생성 자체는 지연 호출 구조상 가능하지만, 서버 시작 직후 랭킹 스케줄러가 기본 100ms 뒤 실행되어 KIS 관련 경고를 반복할 수 있다. 로컬 UI만 확인할 때는 초기 지연을 크게 설정할 수 있다.

```bash
STOCK_RANKING_INITIAL_DELAY_MILLIS=600000 ./gradlew bootRun
```

### 전체 배포 형태를 로컬에서 실행

루트 `Dockerfile.local`은 Spring Boot 실행 JAR를 빌드한 뒤 JRE만 포함한 이미지에서 비루트 사용자로 실행한다.
`frontend/Dockerfile.local`은 Node로 React를 빌드하고, 최종 이미지에서는 Nginx가 빌드 결과만 제공한다.
`frontend/nginx.local.conf`는 React Router 경로는 `index.html`로 보내고 `/api`, 로그인·OAuth, `/ws` 요청은
`backend:8080`으로 reverse proxy한다. `docker-compose.local.yml`은 Nginx, Spring, MySQL, Redis를 하나의
Docker 네트워크에서 함께 실행한다.
같은 파일에서 `mysql redis` 서비스만 지정하면 IDE 개발용 인프라만 실행할 수 있다.

루트 `.env`에 실제 비밀번호와 필요한 외부 연동 자격증명을 `KEY=value` 형식으로 입력한다. 이 파일은 기존
로컬 Spring 실행과 배포 형태의 Compose가 공통으로 사용하며 Git에 포함되지 않는다. Compose가 컨테이너
환경에 맞는 datasource URL과 Redis host를 덮어쓰므로 `.env`의 로컬 `localhost` 설정은 컨테이너에 적용되지
않는다. 전체 서비스를 빌드하고 실행한다. 기본 공개 포트는 기존 Vite 개발 주소와 동일한 5173이므로 OAuth
공급자의 로컬 callback URL을 변경하지 않아도 된다.

```bash
docker compose --env-file .env -f docker-compose.local.yml up -d --build
docker compose --env-file .env -f docker-compose.local.yml ps
docker compose --env-file .env -f docker-compose.local.yml logs -f nginx backend
```

브라우저는 `http://localhost:5173`으로 접속한다. Nginx는 React 화면을 제공하고 백엔드 요청만 Spring으로
전달한다. Spring의 `127.0.0.1:8080` 포트는 직접 점검용이다. MySQL 3306과 Redis 6379는 IDE 개발도
지원하도록 호스트의 loopback에만 공개한다. 컨테이너 내부에서 Nginx는 `backend:8080`, Spring은
`mysql:3306`, `redis:6379`로 연결한다.

```bash
curl http://localhost:5173/api/session
```

운영 서버에서는 로컬용 Compose를 사용하지 않는다. `Dockerfile.server`와
`frontend/Dockerfile.server`로 배포 이미지를 빌드해 registry에 push하고, 서버에서는
`docker-compose.server.yml`로 Backend와 Frontend 이미지를 pull한다.

```bash
docker compose --env-file .env -f docker-compose.server.yml pull
docker compose --env-file .env -f docker-compose.server.yml up -d
```

Compose 실행만으로 애플리케이션 컨테이너들은 구동되지만 AWS 운영 준비 전체가 끝나는 것은 아니다. 서버의
보안 그룹에서 80/443을 설정하고, 도메인 DNS와 Certbot 인증서를 구성하고, 실제 HTTPS
OAuth callback URL을 공급자 콘솔에 등록해야 한다. `.env`는 Git으로 전송하지 말고 AWS 서버나 비밀 저장소에서
별도로 준비한다. MySQL을 컨테이너로 운영한다면 볼륨 백업과 장애 복구도 별도로 구성해야 한다.

종료할 때는 볼륨을 보존한다.

```bash
docker compose --env-file .env -f docker-compose.local.yml down
```

`down -v`는 이 배포 구성의 MySQL과 Redis 데이터를 삭제하므로 데이터 삭제가 명확히 필요한 경우에만 사용한다.

## 5. 테스트와 빌드

금융 상태 전이, 계산식, 트랜잭션 경계 또는 락 순서를 변경할 때 필요한 회귀·보존식·실패 주입·동시성 증거는 [금융 불변식](FINANCIAL_INVARIANTS.md)의 변경 기준을 함께 따른다.

통합 테스트는 JVM마다 새 MySQL Testcontainer를 만들고 여러 Spring 테스트 컨텍스트가 이를 공유한다.
테스트 프로필은 `ddl-auto=update`로 스키마를 한 번 생성하며, 각 테스트 시작 전 공통 지원 클래스가 테스트 스키마의 모든 테이블을 비운다.
따라서 컨텍스트별 `create-drop` 종료 작업이 같은 FK를 반복 삭제하는 로그를 만들지 않는다.

```bash
# React 정적 검사와 production build
cd frontend
npm run lint
npm run build
cd ..

# 전체 테스트
./gradlew test

# Gradle UP-TO-DATE 판정과 관계없이 전체 테스트 강제 재실행
./gradlew test --rerun-tasks --no-daemon --console=plain

# 정리 후 전체 빌드(테스트 포함)
./gradlew clean build

# React 정적 파일이 포함되지 않은 실행 가능한 jar 생성
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

### 시작 자금 정책 변경

`User.simulationFundingGranted`는 `boolean default false`, `nullable=false` 열로 추가된다. 신규 사용자는 첫 KRW 일반계좌 개설 시 1억원을 받고, 지급 이력과 `DEPOSIT` 원장을 함께 저장한다. 계좌 개설 한도는 일반·증권 각각 3개이며, 기존 계좌나 잔액을 소급 변경하지 않는다.

기존 KRW 일반계좌를 가진 사용자는 개설 서비스에서 이미 지급받은 것으로 처리한다. 지급 이력을 배포 시 미리 채우려면 스키마 반영 후 아래 쿼리를 별도 배포 변경으로 적용할 수 있다. 신규 지급을 위한 쿼리가 아니며 잔액은 변경하지 않는다.

```sql
UPDATE `user` u
SET u.simulation_funding_granted = TRUE
WHERE u.simulation_funding_granted = FALSE
  AND EXISTS (SELECT 1 FROM account a WHERE a.user_id = u.id AND a.currency_code = 'KRW');
```

기존 계좌가 이미 삭제되어 기록이 없다면 이 쿼리나 개설 서비스로 과거 지급을 판별할 수 없다. 현재 서비스에 계좌 삭제 API는 없으며, 향후 삭제 기능을 추가할 때도 사용자 지급 이력을 초기화하지 않아야 한다.

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

`SecurityConfig`의 공개 경로, 로그인 처리 URL(`/login`), 아이디 파라미터명(`userId`)과 로그아웃 URL(`/logout`)을 확인한다. 인증 정보는 `FinMateUserDetailsService`가 조회하고 `BCryptPasswordEncoder`가 비밀번호를 검증한다. 로그인·로그아웃과 JSON 상태 변경 POST는 `/api/session`이 제공한 CSRF 토큰을 포함해야 한다. 인증 성공 상태는 서버 HTTP session의 `SecurityContext`에 저장된다.

소셜 로그인 버튼이 보이지 않으면 해당 공급자의 `*_OAUTH_ENABLED=true`와 Client ID/Secret 주입을 확인한다. callback 오류가 발생하면 실제 접속 주소와 공급자 콘솔에 등록한 Redirect URI가 정확히 일치하는지 확인한다. 인증 성공 후에는 `OAuthAccount(provider, providerSubject)`로 로컬 `User`를 찾으며, 최초 사용자는 로컬 비밀번호 없이 생성된다.

Kakao에서 ID token이 발급되지 않으면 Kakao Login의 OpenID Connect 활성화 여부를 확인한다. Naver 사용자 정보 오류가 발생하면 애플리케이션의 제공 정보 설정과 `response.id` 반환 여부를 확인한다.

## 9. 테스트 보강 우선순위

1. Testcontainers 기반 MySQL 계좌이체 동시성·데드락 회귀 테스트
2. 일반↔투자 자금 이동 원자성 테스트
3. 매수·매도·취소·만료 정산 단위 테스트
4. 실시간 체결과 취소 경쟁 테스트
5. KIS payload parser와 rate-limit 재시도 테스트
6. Redis 직렬화·TTL·장애 fallback 테스트
