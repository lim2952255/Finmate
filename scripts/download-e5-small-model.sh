#!/usr/bin/env bash

# Spring에서 E5 ONNX 모델을 실행하기 전에 필요한 파일을 Hugging Face에서 내려받아
# 로컬 또는 EC2의 models 디렉터리에 준비하는 스크립트다.
#
# 이 모델은 뉴스 문장을 호재·악재로 분류하는 모델이 아니다. 문장을 의미를 나타내는 숫자 벡터로
# 변환하고, 두 벡터의 cosine similarity를 계산해 내용이 비슷한 뉴스를 제거하는 데 사용한다.
# Hugging Face 저장소가 이미 내보낸 ONNX 파일을 제공하므로 이 스크립트는 별도 변환 없이 다운로드만 한다.
#
# 실행 후 만들어지는 디렉터리 예시:
# models/multilingual-e5-small/
# ├── model.onnx                 실제 추론 모델
# ├── tokenizer.json             Java 토크나이저가 직접 읽는 통합 정의
# └── 그 밖의 설정 및 토크나이저 보조 파일

# 셸 스크립트를 안전하게 실행하기 위한 옵션.
# -e: 명령 하나가 실패하면 스크립트를 바로 종료한다.
# -u: 정의되지 않은 변수를 사용하면 오류를 발생시킨다.
# pipefail: 파이프라인 중간 명령어가 실패하면 전체 명령을 실패로 간주한다.
set -euo pipefail

# Hugging Face 모델 저장소도 Git처럼 변경 이력을 가진다. branch의 최신 파일을 그대로 받으면
# 나중에 제작자가 모델을 변경했을 때 개발 PC와 EC2가 서로 다른 결과를 낼 수 있다.
# 따라서 검증한 commit SHA를 고정해 언제 실행해도 같은 가중치와 토크나이저를 받는다.
MODEL_REVISION="614241f622f53c4eeff9890bdc4f31cfecc418b3"
# resolve/<revision>/onnx 경로는 해당 commit에 포함된 ONNX 배포 디렉터리를 가리킨다.
MODEL_BASE_URL="https://huggingface.co/intfloat/multilingual-e5-small/resolve/${MODEL_REVISION}/onnx"
# 사용 예: ./scripts/download-e5-small-model.sh /opt/finmate/models/multilingual-e5-small
# ${1:-기본값}은 첫 번째 명령줄 인자가 있으면 그 경로를 사용하고, 없으면 기본 경로를 사용한다.
# 로컬에서는 기본 경로를 쓰고 EC2에서는 /opt/finmate/models 아래의 영속 경로를 전달할 수 있다.
MODEL_DIRECTORY="${1:-models/multilingual-e5-small}"

# 여러 파일을 내려받기 전에 대상 디렉터리가 없으면 생성한다.
mkdir -p "${MODEL_DIRECTORY}"

# 지정한 파일 이름을 하나씩 순회하며 같은 고정 revision의 URL에서 대상 디렉터리로 저장한다.
# model.onnx: 학습된 연산 그래프와 가중치. Java의 OrtSession이 직접 로딩한다.
# config.json: 모델 구조, hidden size 등 모델 메타데이터.
# tokenizer.json: 문자열 분리부터 토큰 ID 변환까지 합쳐 놓은 정의. Java 코드가 직접 로딩한다.
# tokenizer_config.json: 최대 길이와 토크나이저 동작에 관한 부가 설정.
# special_tokens_map.json: 문장 시작·종료·패딩 같은 특수 토큰의 이름과 실제 문자열 매핑.
# sentencepiece.bpe.model: multilingual-e5-small의 SentencePiece/BPE 어휘와 분리 규칙 원본.
# 현재 Java 실행에는 model.onnx와 tokenizer.json이 핵심이고, 나머지는 모델 출처와 설정을
# 재현하거나 다른 Hugging Face 도구로 확인할 때 함께 사용할 수 있도록 보관한다.
for model_file in \
  model.onnx \
  config.json \
  tokenizer.json \
  tokenizer_config.json \
  special_tokens_map.json \
  sentencepiece.bpe.model; do
  # --fail: 404/500 같은 HTTP 오류를 성공으로 처리하지 않는다.
  # --location: Hugging Face의 redirect를 따라 실제 파일 위치로 이동한다.
  # --retry 3: 일시적인 네트워크 오류는 세 번까지 재시도한다.
  # 따옴표로 경로를 감싸 공백이 있는 사용자 지정 디렉터리도 하나의 인자로 처리한다.
  curl --fail --location --retry 3 \
    "${MODEL_BASE_URL}/${model_file}" \
    --output "${MODEL_DIRECTORY}/${model_file}"
done

# 모든 파일이 정상적으로 내려받아진 경우에만 set -e 규칙을 통과해 이 메시지가 출력된다.
echo "E5-small 모델을 ${MODEL_DIRECTORY}에 다운로드했습니다."
