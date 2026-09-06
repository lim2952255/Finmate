#!/usr/bin/env bash
# KR-FinBert-SC를 고정 리비전에서 받아 ONNX INT8 모델과 토크나이저로 준비한다.
# 모델 저장소에 명시적 라이선스가 없으므로 운영 배포 전 사용 조건을 별도로 확인해야 한다.
#
# 원본 저장소는 PyTorch 모델을 제공하지만 Spring 애플리케이션은 ONNX Runtime으로 추론한다.
# 따라서 이 스크립트는 다음 준비 작업을 한 번 수행한다.
#
# Hugging Face PyTorch 모델 다운로드
#   -> Optimum으로 ONNX 변환
#   -> ONNX 가중치를 INT8로 양자화
#   -> Java 실행에 필요한 model.onnx와 토크나이저 파일만 최종 디렉터리에 복사
#
# 모델 변환은 개발 PC나 EC2의 모델 준비 단계에서만 실행한다. 애플리케이션이 뉴스를 조회할 때마다
# Python을 실행하는 것이 아니며, Spring은 완성된 model.onnx를 Java ONNX Runtime으로 직접 읽는다.

# 셸 스크립트의 중간 실패를 숨기지 않는다.
# -e: 명령이 실패하면 즉시 종료한다.
# -u: 선언되지 않은 변수를 사용하면 오류로 처리한다.
# pipefail: 파이프 중간 명령의 실패도 전체 실패로 처리한다.
set -euo pipefail

# 다운로드할 모델의 Hugging Face 저장소 이름이다.
MODEL_ID="snunlp/KR-FinBert-SC"
# 특정 commit을 고정해 실행 시점에 따라 모델과 라벨 순서가 달라지는 것을 방지한다.
MODEL_REVISION="f8586286cc3161fb648e9fee09a456069fd846d0"
# 사용 예: ./scripts/download-kr-finbert-sentiment-model.sh /opt/finmate/models/kr-finbert-sentiment
# 첫 번째 인자가 없으면 프로젝트의 models/kr-finbert-sentiment를 사용한다.
# 실제 변환 및 양자화된 ONNX 모델과 토크나이저를 저장할 디렉터리를 지정한다.
MODEL_DIRECTORY="${1:-models/kr-finbert-sentiment}"
# 원본 모델, Python 가상환경, 변환 중간 결과는 최종 산출물이 아니므로 임시 디렉터리에 둔다.
WORK_DIRECTORY="$(mktemp -d)"

# 변환 도중 실패하더라도 수백 MB의 원본 모델과 임시 Python 환경이 남지 않게 정리한다. 즉 임시 디렉터리를 정리하여 자원이 남지 않도록 보장한다.
cleanup() {
  # 변수는 mktemp가 만든 정확한 경로만 가리키며, 스크립트 종료 시 임시 작업물 전체를 제거한다.
  rm -rf "${WORK_DIRECTORY}"
}
# EXIT trap은 정상 종료와 오류 종료 모두에서 cleanup 함수를 실행한다.
trap cleanup EXIT # 정상종료와 오류 종료 모두에서 해당 cleanup을 호출하도록 지정한다.

# 시스템 Python 환경을 오염시키지 않도록 이번 변환에만 사용하는 독립 가상환경을 만든다.
# Optimum의 ONNX export 기능과 ONNX Runtime의 양자화 도구가 Python 패키지/API 형태로 제공되기 때문에, 모델 준비 단계에서 Python 실행 환경이 필요하다.
# 해당 Python 가상환경은 모델을 Python 환경에서 실행하기 위한 용도가 아니라, Pytorch 기반의 모델을 Optimum을 활용하여 ONNX 모델로 변환하기 위해 임시로 활용한다.
python3 -m venv "${WORK_DIRECTORY}/venv"
# 이후 모든 pip와 Python 명령은 방금 만든 가상환경 안의 실행 파일을 명시적으로 사용한다.
"${WORK_DIRECTORY}/venv/bin/python" -m pip install --quiet --upgrade pip
# optimum: Hugging Face 모델을 ONNX로 내보내는 CLI를 제공한다.
# onnx: 변환된 ONNX 그래프를 표현하고 검사하는 라이브러리다.
# onnxruntime: 변환 결과를 처리하고 동적 양자화를 수행한다.
# 패키지는 임시 venv에만 설치되고 cleanup 때 함께 제거된다.
"${WORK_DIRECTORY}/venv/bin/python" -m pip install --quiet 'optimum[onnx]' onnx onnxruntime

# Optimum CLI에 원격 모델 ID를 바로 넘기는 대신 고정 revision의 스냅샷을 먼저 내려받는다.
# 아래 `python -`는 별도 .py 파일 없이 heredoc의 Python 코드를 표준입력으로 실행한다.
# `-` 뒤의 세 값은 Python 코드에서 sys.argv[1:]로 전달받는다.
# 즉 파이썬 파일을 별도로 생성하는 것이 아니라 스크립트에서 바로 파이썬 코드를 실행하며, 이때 3가지 인자를 함께 전달한다.
# 해당 파이썬 코드는 허깅페이스에서 지정한 revision에 해당하는 모델과 토크나이저, 필요한 설정파일들을 임시 폴더에 저장하는 역할을 수행한다.
# 다만 현재까지는 순수 pytorch 모델을 다운로드 한 것이기 때문에 ONNX 모델로 변환하는 과정이 추가적으로 필요하다.
"${WORK_DIRECTORY}/venv/bin/python" - \
  "${MODEL_ID}" \
  "${MODEL_REVISION}" \
  "${WORK_DIRECTORY}/checkpoint" <<'PY'
import sys

from huggingface_hub import snapshot_download

model_id, revision, target_directory = sys.argv[1:]
# 전체 저장소가 아니라 ONNX 변환과 토큰화에 필요한 파일만 checkpoint 디렉터리에 받는다.
snapshot_download(
    repo_id=model_id,
    revision=revision,
    local_dir=target_directory,
    allow_patterns=[
        "config.json",
        "pytorch_model.bin",
        "tokenizer.json",
        "tokenizer_config.json",
        "special_tokens_map.json",
        "vocab.txt",
    ],
)
PY

# 임시 폴더에 저장해둔 Pytorch 기반 모델을 Optimum을 활용하여 ONNX 그래프로 변환한다.
# --task text-classification을 지정해야 출력이 문장별 분류 점수(logits)가 된다.
# --optimize O1은 기본적인 transformer 그래프 최적화를 적용한다.
# 결과는 아직 양자화하지 않은 exported/model.onnx와 관련 설정 파일이다.

# 이때 --task text-classification은 KR-finbert가 어떤 종류의 모델인지 Optimum에게 알려주는것이다.
# 이후 optimize 01을 통해 Pytorch 모델을 최적화된 FP32 ONNX 모델로 변환하고, 아직 양자화를 수행하지 않았기 때문에 임시폴더에 저장한다.
"${WORK_DIRECTORY}/venv/bin/optimum-cli" export onnx \
  --model "${WORK_DIRECTORY}/checkpoint" \
  --task text-classification \
  --optimize O1 \
  "${WORK_DIRECTORY}/exported"

# 최종 산출물 디렉터리는 사용자가 지정할 수 있으며 없으면 생성한다.
mkdir -p "${MODEL_DIRECTORY}"

# 두 번째 heredoc Python은 변환된 FP32 ONNX 모델을 최종 INT8 model.onnx로 만든다.
# source와 target을 분리해 양자화 도중 실패해도 불완전한 파일을 원본 위에 덮어쓰지 않는다.

# 이때 첫번째 인자로는 아직 양자화가 되지 않은 FP32 ONNX 모델을 전달하고, 두번째 인자로는 양자화가 완료된 INT8 ONNX 모델을 전달한다.
"${WORK_DIRECTORY}/venv/bin/python" - \
  "${WORK_DIRECTORY}/exported/model.onnx" \
  "${MODEL_DIRECTORY}/model.onnx" <<'PY'
import sys

from onnxruntime.quantization import QuantType, quantize_dynamic

source_model, target_model = sys.argv[1], sys.argv[2]
# 동적 양자화는 주로 모델 가중치를 32-bit 부동소수점에서 8-bit 정수로 줄인다.
# 일반적으로 파일 크기와 CPU 추론 부담을 낮출 수 있으며, 정확도 특성은 최종 모델로 검증해야 한다.
quantize_dynamic(source_model, target_model, weight_type=QuantType.QInt8)
PY

# Java 런타임에는 양자화된 모델 그래프와 동일 revision의 토크나이저 정의가 필요하다.
# tokenizer.json과 vocab.txt가 모델 학습 당시 토큰 ID 체계를 보존하므로 임의 버전과 섞으면 안 된다.
# config.json에는 negative/neutral/positive 라벨과 인덱스 대응 등 모델 메타데이터가 들어 있다.

# 임시 폴더에 저장해둔 토크나이저 관련 설정 파일들을 최종 모델 디렉터리로 복사한다.
for model_file in tokenizer.json tokenizer_config.json special_tokens_map.json vocab.txt config.json; do
  # 변환 도구가 exported 디렉터리에 함께 기록한 파일을 최종 모델 디렉터리로 복사한다.
  cp "${WORK_DIRECTORY}/exported/${model_file}" "${MODEL_DIRECTORY}/${model_file}"
done

# 이 메시지가 출력되면 다운로드, 변환, 양자화, 파일 복사가 모두 성공한 것이다.
echo "KR-FinBert-SC ONNX INT8 모델을 ${MODEL_DIRECTORY}에 준비했습니다."

# 작업이 모두 완료되어 최종 모델 디렉터리에 양자화된 ONNX 모델과 토크나이저 관련 파일들이 저장되면 cleanup이 호출되면서 임시폴더를 제거한다.
