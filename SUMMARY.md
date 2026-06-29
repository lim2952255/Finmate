# FinMate Summary Index

마지막 정리일: 2026-06-29

FinMate 구현 내용을 주제별로 나누어 정리한다. 앞으로 기능을 추가할 때는 해당 도메인의 summary 파일을 갱신한다.

## 문서 목록

- [Login Summary](summary/login-summary.md)
- [일반 계좌 Summary](summary/normal-account-summary.md)
- [증권 계좌 Summary](summary/investment-account-summary.md)

## 현재 큰 흐름

- 로그인/회원가입은 세션 기반 인증과 인터셉터 중심으로 구성되어 있다.
- 일반 계좌는 계좌 개설, 대표계좌, 계좌이체, 거래내역, 이체한도, 동시성 제어까지 기본 뼈대가 잡혀 있다.
- 증권 계좌는 계좌 개설, 목록, 대표 증권계좌, 홈 요약까지 구현했고, 투자금 이체/주문/체결/보유 종목은 앞으로 확장할 예정이다.
- 일반 계좌와 증권 계좌의 계좌번호 중복은 `AccountNumberRegistry`로 공통 관리한다.
