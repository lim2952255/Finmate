# 뉴스 랭킹 오프라인 평가

운영 애플리케이션은 `NEWS_RANKING_STRATEGY`로 선택한 전략 하나만 실행한다. 이 디렉터리의
평가 도구는 운영 JAR과 분리된 Gradle `evaluation` source set에서 동일한 전략 구현 네 개를 실행한다.
평가 설정은 `gradle/evaluation.gradle`에 있으며 `-PwithEvaluation`을 지정할 때만 로드된다.

네 전략이 같은 정보를 바탕으로 비교되도록 키워드 기준 순서도 제목과 네이버 요약문을 함께 사용한다.
제목에서 발견한 키워드는 2점, 제목에는 없고 요약문에만 있는 키워드는 1점이며, 같은 키워드가 두 필드에
반복되어도 한 번만 점수화한다. TF-IDF와 임베딩 전략도 이 기준 순서에서 novelty 선별을 시작한다.

## 1. 평가 데이터 수집

프로젝트 루트의 기존 `.env`에 다음 값을 준비한다. 평가 Gradle task가 이 파일을 자동으로 읽으며,
터미널이나 CI에 같은 환경변수가 이미 설정돼 있으면 외부 값을 우선한다.

```env
NAVER_API_HUB_CLIENT_ID=...
NAVER_API_HUB_CLIENT_SECRET=...
```

기존 `.env`가 네이버 요청 헤더 형식인 `X-NCP-APIGW-API-KEY-ID:`와
`X-NCP-APIGW-API-KEY:`를 사용하고 있어도 평가 task가 위 표준 변수명으로 변환해 읽는다.

KOSPI, KOSDAQ, NASDAQ, S&P 500, 금리, 환율과 삼성전자, SK하이닉스, NAVER, 카카오,
KB금융, 현대차의 후보를 80개씩 고정 JSON으로 저장한다.

```bash
./gradlew -PwithEvaluation collectNewsEvaluationDataset
```

다른 출력 디렉터리를 사용하려면 다음처럼 실행한다.

```bash
./gradlew -PwithEvaluation collectNewsEvaluationDataset -PoutputDir=/absolute/path/to/datasets
```

## 2. 네 전략 및 LLM judge 평가

E5 모델을 먼저 준비한다.

```bash
./scripts/download-e5-small-model.sh
```

그다음 프로젝트 루트의 기존 `.env`에 OpenAI 평가 설정을 추가한다. 평가 task가 같은 방식으로
자동으로 읽으므로 별도의 `source .env` 명령은 필요하지 않다.

```env
OPENAI_API_KEY=...
NEWS_EVALUATION_JUDGE_MODEL=사용할-고정-모델-ID
```

수집된 모든 JSON을 평가한다.

기본 평가는 저장된 후보를 다시 수집하지 않고 다음 threshold sweep을 한 번에 실행한다.

```text
TF-IDF:          0.20, 0.30, 0.40, 0.50
한국어 TF-IDF:   0.20, 0.30, 0.40, 0.50
임베딩:          0.90, 0.92, 0.94, 0.96
```

목록을 바꾸려면 프로젝트 루트 `.env`에서 쉼표로 구분해 설정한다.

```env
NEWS_EVALUATION_TF_IDF_THRESHOLDS=0.20,0.30,0.40,0.50
NEWS_EVALUATION_KOREAN_TF_IDF_THRESHOLDS=0.20,0.30,0.40,0.50
NEWS_EVALUATION_EMBEDDING_THRESHOLDS=0.90,0.92,0.94,0.96
```

한 데이터셋의 모든 전략·임계값 결과를 먼저 만든 뒤 기사 합집합만 LLM judge에 한 번 전달한다.
결과 CSV의 `threshold` 열로 같은 전략의 임계값별 지표를 비교할 수 있다.

```bash
./gradlew -PwithEvaluation evaluateNewsRanking
```

특정 데이터셋 하나만 평가할 수도 있다.

```bash
./gradlew -PwithEvaluation evaluateNewsRanking -Pdataset=/absolute/path/to/dataset.json
```

평가기는 네 전략의 Top 10 합집합만 judge에 전달한다. judge는 기사별 관련도 0~2와 동일 사건
`eventId`를 Structured Outputs로 반환한다. 같은 모델과 프롬프트 버전의 라벨 파일은 재사용해
불필요한 API 비용을 막는다.

생성물은 다음 위치에 저장되며 Git에는 포함되지 않는다.

```text
datasets/raw/      네이버 API의 고정 후보 JSON
labels/generated/ OpenAI judge 라벨 JSON
results/          nDCG·중복률·고유 사건·처리 시간 CSV
```

LLM judge는 전문가 라벨을 완전히 대체하는 절대적인 정답이 아니다. 최종 전략을 선택하기 전에는
각 주제에서 일부 결과와 judge 사유를 표본 검토하고, 평가 실행마다 같은 모델 ID와 프롬프트 버전을
유지해야 한다.
