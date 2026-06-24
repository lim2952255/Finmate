# FinMate

> **은행 + 증권 + AI 분석을 결합한 금융권 IT 백엔드 포트폴리오 프로젝트**

FinMate는 계좌 관리, 입출금, 계좌이체, 거래내역, 이상거래 탐지, 주식 시세 조회, 실시간 체결가, 모의투자, 뉴스 분석, AI 투자 리포트를 제공하는 통합 금융 플랫폼입니다.

실제 금융 거래를 수행하지 않으며, 학습 및 포트폴리오 목적의 가상 금융 서비스입니다.

---

## 목차

```text
1. 프로젝트 소개
2. 주요 기능
3. 기술 스택
4. 시스템 아키텍처
5. 핵심 설계 포인트
6. 도메인 설계
7. API 명세
8. Redis 설계
9. Batch 설계
10. 테스트 전략
11. 인프라 및 배포
12. 실행 방법
13. 프로젝트 구조
14. 개발 로드맵
15. 주의사항
```

---

## 1. 프로젝트 소개

FinMate는 금융권 IT 취업을 목표로 설계한 백엔드 중심 프로젝트입니다.

단순 CRUD 서비스가 아니라 금융 시스템에서 중요한 다음 요소를 직접 구현하는 것을 목표로 합니다.

```text
거래 정합성
동시성 제어
이상거래 탐지
실시간 시세 처리
외부 금융 API 연동
모의투자 주문/체결 로직
뉴스 수집
AI 기반 금융 데이터 분석
Redis / Batch / WebSocket / Docker / AWS 기반 운영 구조
```

---

## 2. 주요 기능

## 회원 / 인증

```text
회원가입
로그인
로그아웃
JWT Access Token 발급
Refresh Token 재발급
내 정보 조회
비밀번호 암호화
권한 관리
```

---

## 계좌

```text
가상 계좌 생성
계좌 목록 조회
계좌 상세 조회
잔고 조회
입금
출금
계좌이체
거래내역 조회
```

---

## 이상거래 탐지 FDS

```text
거래 요청 시점 Risk Score 계산
1분 내 반복 송금 탐지
하루 누적 송금액 탐지
새벽 시간대 고액 송금 탐지
최근 평균 거래금액 대비 이상 거래 탐지
새 IP / 새 기기 로그인 후 즉시 송금 탐지
정상 / 추가 인증 필요 / 거래 차단 판단
```

---

## 증권

```text
종목 검색
현재가 조회
등락률 조회
거래량 조회
일봉 / 주봉 / 월봉 / 분봉 차트 조회
실시간 체결가 조회
관심종목 등록 / 삭제 / 조회
```

---

## 모의투자

```text
가상 투자 계좌 생성
초기 예수금 지급
매수 주문
매도 주문
주문 내역 조회
체결 내역 조회
보유 종목 조회
평균 매수가 계산
평가 손익 계산
수익률 계산
포트폴리오 조회
```

---

## 뉴스 / AI

```text
종목별 뉴스 검색
뉴스 중복 제거
뉴스 저장
뉴스 요약
호재 / 악재 / 중립 감성 분석
주요 키워드 추출
AI 투자 리포트 생성
투자 리스크 요약
```

---

## 3. 기술 스택

## Backend

```text
Java 17
Spring Boot 3.5.6
Gradle
Spring MVC
Spring Security
JWT
Spring Data JPA
QueryDSL
Bean Validation
MySQL
Redis
Spring Batch
WebSocket / STOMP
```

## Frontend

```text
React
TypeScript
TanStack Query
Zustand
Tailwind CSS
TradingView Lightweight Charts
```

## Infra

```text
Docker
Docker Compose
AWS EC2
AWS RDS MySQL
Nginx
HTTPS
GitHub Actions
```

## External API

```text
한국투자증권 Open API
- 주식 현재가 조회
- 주식 차트 데이터 조회
- 실시간 체결가 WebSocket

네이버 뉴스 검색 API
- 종목별 뉴스 검색

OpenAI API
- 뉴스 요약
- 감성 분석
- AI 투자 리포트 생성
```

---

## 4. 시스템 아키텍처

```text
[React Frontend]
        |
        | REST API / WebSocket
        v
[Nginx]
        |
        v
[Spring Boot Backend]
        |
        |---- MySQL / AWS RDS
        |       - 회원
        |       - 계좌
        |       - 거래내역
        |       - FDS 로그
        |       - 주문
        |       - 체결
        |       - 보유 종목
        |       - 뉴스
        |       - AI 리포트
        |
        |---- Redis
        |       - Refresh Token
        |       - Access Token Blacklist
        |       - 현재가 캐싱
        |       - 뉴스 캐싱
        |       - FDS 카운팅
        |       - 실시간 시세 Pub/Sub
        |
        |---- 한국투자증권 Open API
        |       - 현재가
        |       - 차트
        |       - 실시간 체결가
        |
        |---- 네이버 뉴스 API
        |
        |---- OpenAI API
```

---

## 5. 핵심 설계 포인트

## 거래 정합성

계좌이체는 하나의 트랜잭션으로 처리합니다.

```text
A 계좌 잔액 차감
B 계좌 잔액 증가
A 계좌 거래내역 생성
B 계좌 거래내역 생성
FDS 로그 생성
```

중간에 하나라도 실패하면 전체 작업을 rollback합니다.

---

## 동시성 제어

동일 계좌에 동시에 여러 요청이 들어와도 잔고가 꼬이지 않도록 비관적 락을 적용합니다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select a from Account a where a.id = :id")
Optional<Account> findByIdForUpdate(@Param("id") Long id);
```

---

## 데드락 방지

계좌이체는 두 계좌를 동시에 잠글 수 있으므로 항상 계좌 ID가 작은 계좌부터 lock을 획득합니다.

```text
fromAccountId < toAccountId:
  fromAccount lock
  toAccount lock

fromAccountId > toAccountId:
  toAccount lock
  fromAccount lock
```

---

## 거래내역 감사 추적

거래내역에는 거래 직후 잔액인 `balance_after`를 저장합니다.

```text
Account.balance = 현재 상태
TransactionHistory = 상태 변경의 근거
TransactionHistory.balance_after = 거래 직후 잔액
```

---

## FDS 준실시간 탐지

거래 요청 시점에 Risk Score를 계산합니다.

```text
0 ~ 39   : 정상 거래
40 ~ 79  : 추가 인증 필요
80 ~ 100 : 거래 차단
```

짧은 시간 반복 송금은 Redis TTL 카운터로 탐지합니다.

```text
fds:transfer-count:{memberId}
TTL 60초
```

---

## 실시간 시세 처리

```text
한국투자증권 WebSocket
→ Spring Boot WebSocket Client
→ Redis Pub/Sub
→ Spring WebSocket/STOMP
→ React Client
```

---

## 모의투자 엔진

실제 주문 API를 사용하지 않고, 실제 시장 데이터만 활용하여 주문/체결/예수금/보유수량/평균단가/수익률 계산 로직을 직접 구현합니다.

---

## 6. 도메인 설계

## Member

```text
id
email
password
name
role
created_at
updated_at
```

---

## Account

```text
id
member_id
account_number
balance
account_type
status
created_at
updated_at
```

---

## TransactionHistory

```text
id
member_id
account_id
transaction_type
amount
balance_after
target_account_number
description
created_at
```

---

## FdsLog

```text
id
member_id
account_id
transaction_id
risk_score
risk_level
reason
request_ip
device_id
created_at
```

---

## Stock

```text
id
stock_code
stock_name
market
sector
created_at
```

---

## Watchlist

```text
id
member_id
stock_code
created_at
```

---

## VirtualInvestmentAccount

```text
id
member_id
cash_balance
initial_cash
created_at
updated_at
```

---

## StockOrder

```text
id
member_id
stock_code
order_type
order_price
quantity
order_status
created_at
```

---

## StockExecution

```text
id
order_id
execution_price
executed_quantity
executed_at
```

---

## StockHolding

```text
id
member_id
stock_code
quantity
avg_price
total_buy_amount
updated_at
```

---

## StockNews

```text
id
stock_code
keyword
title
original_link
naver_link
description
summary
sentiment
sentiment_score
published_at
created_at
```

---

## AiReport

```text
id
stock_code
summary
positive_ratio
neutral_ratio
negative_ratio
keywords
risk_factors
created_at
```

---

## PortfolioSnapshot

```text
id
member_id
total_asset
cash_balance
stock_value
profit_amount
profit_rate
snapshot_date
```

---

## 7. API 명세

## Auth

```http
POST /api/auth/signup
POST /api/auth/login
POST /api/auth/logout
POST /api/auth/reissue
GET  /api/members/me
```

---

## Account

```http
POST /api/accounts
GET  /api/accounts
GET  /api/accounts/{accountId}
POST /api/accounts/{accountId}/deposit
POST /api/accounts/{accountId}/withdraw
POST /api/transfers
GET  /api/accounts/{accountId}/transactions
```

---

## FDS

```http
GET /api/fds/logs
GET /api/fds/logs/{logId}
```

---

## Stock

```http
GET /api/stocks/search?keyword=삼성전자
GET /api/stocks/{stockCode}
GET /api/stocks/{stockCode}/price
GET /api/stocks/{stockCode}/candles?period=DAILY
GET /api/stocks/{stockCode}/news
GET /api/stocks/{stockCode}/report
```

---

## Watchlist

```http
POST   /api/watchlists/{stockCode}
DELETE /api/watchlists/{stockCode}
GET    /api/watchlists
```

---

## Order

```http
POST /api/orders/buy
POST /api/orders/sell
GET  /api/orders
GET  /api/orders/{orderId}
```

---

## Portfolio

```http
GET /api/portfolio
GET /api/portfolio/snapshots
```

---

## WebSocket

```text
/ws/stocks
/topic/stocks/{stockCode}
```

---

## 8. Redis 설계

## 인증

```text
auth:refresh:{memberId}
- Refresh Token 저장

auth:blacklist:{accessToken}
- 로그아웃된 Access Token 저장
- Access Token 만료 시간까지 TTL 설정
```

---

## 주식

```text
stock:price:{stockCode}
- 현재가 캐싱
- TTL 5~10초

stock:news:{stockCode}
- 뉴스 검색 결과 캐싱
- TTL 30분

stock:realtime:{stockCode}
- 실시간 시세 Pub/Sub
```

---

## FDS

```text
fds:transfer-count:{memberId}
- 1분 내 송금 횟수
- TTL 60초

fds:daily-amount:{memberId}:{yyyyMMdd}
- 일일 송금 누적 금액
- TTL 1~2일
```

---

## 9. Batch 설계

## 장 마감 후 Batch

```text
관심종목 일별 시세 저장
포트폴리오 스냅샷 저장
일별 수익률 계산
```

---

## 새벽 Batch

```text
종목별 뉴스 수집
뉴스 요약
감성 분석
AI 리포트 생성
인기 종목 랭킹 생성
```

---

## Batch Job

```text
DailyPortfolioSnapshotJob
- 보유 종목 조회
- 현재가 조회
- 평가금액 계산
- 수익률 계산
- snapshot 저장

DailyNewsCollectJob
- 관심종목 조회
- 뉴스 검색 API 호출
- 중복 제거
- DB 저장

DailyAiReportJob
- 최근 뉴스 조회
- 요약
- 감성 분석
- 리포트 저장
```

---

## 10. 테스트 전략

## 단위 테스트

```text
FDS Rule Engine 테스트
평균단가 계산 테스트
수익률 계산 테스트
잔고 검증 테스트
거래 금액 검증 테스트
```

---

## 통합 테스트

```text
회원가입 / 로그인 테스트
계좌 생성 테스트
입금 테스트
출금 테스트
계좌이체 성공 테스트
잔고 부족 이체 실패 테스트
이체 중 예외 발생 시 rollback 테스트
거래내역 생성 확인 테스트
FDS 로그 생성 확인 테스트
모의투자 매수 / 매도 테스트
포트폴리오 수익률 계산 테스트
```

---

## 동시성 테스트

```text
동시에 100개의 출금 요청
동시에 100개의 송금 요청
동시에 같은 종목 매도 요청
```

예시 시나리오:

```text
계좌 잔고 100,000원
10,000원 출금 요청 20개 동시 실행

기대 결과:
성공 10개
실패 10개
최종 잔고 0원
거래내역 10개
```

---

## Testcontainers

```text
MySQL Testcontainer
Redis Testcontainer
```

---

## 11. 인프라 및 배포

## 로컬 개발 환경

```text
Docker Compose
- Spring Boot
- MySQL
- Redis
```

---

## 운영 환경

```text
AWS EC2
- Nginx
- Spring Boot Docker Container

AWS RDS
- MySQL

Redis
- EC2 Docker Redis
- 추후 ElastiCache 확장 가능
```

---

## Nginx 구조

```text
Client
  |
  v
Nginx : 80 / 443
  |
  v
Spring Boot : 8080
```

---

## CI/CD

```text
GitHub main push
→ GitHub Actions 실행
→ 테스트
→ 빌드
→ Docker image 생성
→ EC2 접속
→ 기존 컨테이너 종료
→ 새 컨테이너 실행
→ health check
```

---

## 운영 로그

```text
docker logs
Nginx access log
Nginx error log
Spring Boot application log
Actuator health check
```

---

## 12. 실행 방법

## Repository Clone

```bash
git clone https://github.com/{username}/finmate.git
cd finmate
```

---

## 환경 변수 설정

```bash
cp .env.example .env
```

`.env` 예시:

```env
MYSQL_DATABASE=finmate
MYSQL_USER=finmate
MYSQL_PASSWORD=finmate-password
MYSQL_ROOT_PASSWORD=root-password

REDIS_HOST=redis
REDIS_PORT=6379

JWT_SECRET=your-jwt-secret
JWT_ACCESS_TOKEN_EXPIRE=1800000
JWT_REFRESH_TOKEN_EXPIRE=1209600000

KIS_APP_KEY=your-kis-app-key
KIS_APP_SECRET=your-kis-app-secret

NAVER_CLIENT_ID=your-naver-client-id
NAVER_CLIENT_SECRET=your-naver-client-secret

OPENAI_API_KEY=your-openai-api-key
```

---

## Docker Compose 실행

```bash
docker-compose up -d
```

---

## Spring Boot 실행

```bash
./gradlew bootRun
```

---

## 테스트 실행

```bash
./gradlew test
```

---

## 빌드

```bash
./gradlew clean build
```

---

## 13. 프로젝트 구조

```text
com.finmate

domain
  member
    controller
    service
    repository
    entity
    dto

  auth
    controller
    service
    jwt
    dto

  account
    controller
    service
    repository
    entity
    dto

  transaction
    controller
    service
    repository
    entity
    dto

  fds
    service
    rule
    repository
    entity
    dto

  stock
    controller
    service
    repository
    entity
    dto

  watchlist
    controller
    service
    repository
    entity
    dto

  order
    controller
    service
    repository
    entity
    dto

  portfolio
    controller
    service
    repository
    entity
    dto

  news
    controller
    service
    repository
    entity
    dto

  ai
    controller
    service
    repository
    entity
    dto

global
  config
  security
  exception
  redis
  external
  batch
  websocket
  common
```

---

## 14. 개발 로드맵

## Phase 1. 금융 핵심 기능

```text
회원가입 / 로그인
JWT 인증
계좌 생성
입금
출금
계좌이체
거래내역
@Transactional 적용
비관적 락 적용
동시성 테스트
FDS Rule Engine 1차 구현
```

---

## Phase 2. 증권 기본 기능

```text
한국투자증권 API 연동
종목 검색
현재가 조회
일봉 / 주봉 / 월봉 차트
관심종목
가상 투자 계좌
모의 매수
모의 매도
보유 종목 조회
포트폴리오 수익률 계산
```

---

## Phase 3. 뉴스 / AI 기능

```text
네이버 뉴스 API 연동
종목별 뉴스 수집
뉴스 중복 제거
뉴스 요약
호재 / 악재 / 중립 감성 분석
AI 투자 리포트 생성
```

---

## Phase 4. 실시간 / 인프라 고도화

```text
한국투자증권 WebSocket 연동
Spring WebSocket / STOMP
Redis Pub/Sub
Spring Batch
Docker Compose
AWS EC2 / RDS 배포
GitHub Actions CI/CD
Nginx
HTTPS
```

---

## 15. 주의사항

```text
본 프로젝트는 학습 및 포트폴리오 목적의 프로젝트입니다.
실제 금융 거래를 수행하지 않습니다.
실제 주식 주문을 실행하지 않습니다.
투자 추천이나 투자 자문을 목적으로 하지 않습니다.
모의투자는 가상머니 기반으로만 동작합니다.
AI 분석 결과는 투자 판단 보조 정보로만 사용됩니다.
```

---

## 16. 최종 목표

FinMate는 금융권 IT 취업을 목표로 한 백엔드 중심 통합 금융 플랫폼입니다.

은행 도메인에서는 계좌, 입출금, 송금, 거래내역을 구현하고, 트랜잭션과 락을 통해 잔고 정합성과 동시성 문제를 해결합니다.

FDS 도메인에서는 거래 요청 시점에 Risk Score를 계산하여 반복 송금, 고액 송금, 비정상 패턴을 준실시간으로 탐지합니다.

증권 도메인에서는 한국투자증권 API를 이용해 실제 시세와 차트 데이터를 조회하고, 실제 주문 대신 가상머니 기반 모의투자 엔진을 직접 구현합니다.

뉴스/AI 도메인에서는 종목별 뉴스를 수집하고, 요약·감성분석·투자 리스크 요약 리포트를 제공합니다.

인프라 측면에서는 Redis, Spring Batch, WebSocket, Docker, AWS EC2/RDS, Nginx, HTTPS, GitHub Actions CI/CD를 적용하여 실제 배포 가능한 실무형 백엔드 시스템으로 완성하는 것을 목표로 합니다.
