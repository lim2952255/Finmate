#!/usr/bin/env bash

# EC2의 SSM Agent가 실행하는 FinMate 운영 배포 스크립트다.
# GitHub Actions가 이 repository의 파일 내용을 SSM Run Command에 담아 보내면,
# EC2의 SSM Agent가 /opt/finmate/deploy-server.sh로 설치한 뒤 실행한다.
#
# 이 스크립트가 실행될 때까지의 흐름:
# 1. GitHub Actions가 commit SHA를 tag로 붙인 Backend/Frontend image를 ECR에 push한다.
# 2. GitHub의 IAM Role이 AWS Systems Manager에 EC2 원격 명령을 등록한다.
# 3. 대상 EC2의 SSM Agent가 그 명령을 받아 이 파일과 Compose 파일을 설치한다.
# 4. 이 스크립트는 EC2 Instance Role로 ECR에 로그인해 두 image를 pull한다.
# 5. 기존 MySQL/Redis volume과 운영 .env를 유지하며 컨테이너를 교체한다.
# 6. Nginx 컨테이너에서 Backend로 요청해 배포 성공 여부를 검사한다.
#
# 역할 구분:
# - AWS Systems Manager: AWS가 운영하는 계정·region 범위의 관리형 서비스
# - SSM Agent: 각 EC2 안에서 명령을 수신하고 실행하는 프로그램
# - 이 스크립트: SSM Agent가 EC2의 로컬 shell에서 실행하는 실제 배포 절차
#
# 이 파일은 운영 비밀값을 받거나 만들지 않는다. /opt/finmate/.env는 EC2에 미리
# 준비되어 있어야 하며, 배포할 두 image URI만 그 파일에 반영한다.

# -e: 명령이 실패하면 즉시 종료
# -u: 선언하지 않은 변수를 사용하면 종료
# pipefail: pipe 중간 명령이 실패해도 전체 명령을 실패 처리
set -euo pipefail

# 인자는 SSM의 remote command 마지막 줄에서 다음 순서로 전달된다.
#   1: AWS region (예: ap-northeast-2)
#   2: Backend ECR image URI (registry/repository:sha-<commit>)
#   3: Frontend ECR image URI (registry/repository:sha-<commit>)
# 인자 수가 다르면 잘못된 배포 요청이므로 사용법을 stderr에 출력하고 종료한다.
if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <aws-region> <backend-image> <frontend-image>" >&2
  exit 64
fi

aws_region="$1"
backend_image="$2"
frontend_image="$3"

# EC2 안에서 사용하는 고정 경로다.
# GitHub Actions가 compose_file과 이 script를 설치하고, environment_file은 운영자가
# 최초 배포 전에 별도로 준비한다.
deploy_directory="/opt/finmate"
compose_file="${deploy_directory}/docker-compose.server.yml"
environment_file="${deploy_directory}/.env"

# 실제 상태를 바꾸기 전에 EC2에 필수 명령이 설치되어 있는지 먼저 확인한다.
# `command -v`는 PATH에서 실행 파일을 찾고, 출력은 필요 없으므로 /dev/null로 버린다.
for command in aws docker; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command is not installed: ${command}" >&2
    exit 69
  fi
done

# 이 script는 독립 명령인 docker-compose v1이 아니라 Docker CLI plugin인
# `docker compose` v2 문법을 사용하므로 별도로 확인한다.
if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required." >&2
  exit 69
fi

# Compose 파일은 같은 SSM remote command의 앞 단계에서 설치되어야 한다.
# 없다는 것은 파일 전달/설치가 실패했다는 뜻이므로 container를 건드리기 전에 멈춘다.
if [ ! -f "${compose_file}" ]; then
  echo "Compose file does not exist: ${compose_file}" >&2
  exit 66
fi

# .env에는 DB password, OAuth, KIS 설정 같은 운영 비밀값이 있을 수 있다.
# 그래서 GitHub Actions payload에 넣지 않고 EC2에 원래 있던 파일만 사용한다.
# 최초 배포 전에는 운영자가 안전한 경로로 이 파일을 한 번 준비해야 한다.
if [ ! -f "${environment_file}" ]; then
  echo "Create ${environment_file} with the production secrets before the first deployment." >&2
  exit 66
fi

# 예상하지 않은 public registry나 tag 없는 image가 실행되지 않도록 두 인자가
# private ECR의 `registry/repository:tag` 모양인지 검사한다.
# 이 검사는 문자열 형식 검증이며 image의 존재 여부와 진위는 뒤의 ECR login/pull이 확인한다.
if [[ "${backend_image}" != *.dkr.ecr.*.amazonaws.com/*:* ]] ||
  [[ "${frontend_image}" != *.dkr.ecr.*.amazonaws.com/*:* ]]; then
  echo "Both image arguments must be tagged private ECR image URIs." >&2
  exit 65
fi

# .env에서 지정한 KEY=value 한 항목만 갱신하는 함수다.
# - key가 한 번 나오면 새 값으로 교체한다.
# - 같은 key가 중복되어 있으면 첫 항목만 남기고 나머지는 제거한다.
# - key가 없으면 파일 마지막에 추가한다.
# - 다른 key, 빈 줄, 주석은 그대로 출력한다.
#
# 원본 파일을 직접 한 줄씩 수정하지 않고 같은 디렉터리에 임시 파일을 완성한 뒤
# mv한다. 따라서 작성 도중 명령이 실패해도 반쯤 작성된 내용을 원본으로 쓰지 않는다.
upsert_environment_value() {
  local key="$1"
  local value="$2"
  local temporary_file

  # XXXXXX 부분은 mktemp가 무작위 문자로 바꿔 충돌하지 않는 새 파일을 만든다.
  temporary_file="$(mktemp "${deploy_directory}/.env.XXXXXX")"

  # awk에는 수정할 key/value를 변수로 넘긴다. `index($0, key "=") == 1`은
  # 현재 줄이 정확히 "해당키="로 시작할 때만 일치하므로 비슷한 이름의 key는 건드리지 않는다.
  awk -v key="${key}" -v value="${value}" '
    BEGIN { found = 0 }
    index($0, key "=") == 1 {
      if (!found) {
        print key "=" value
        found = 1
      }
      next
    }
    { print }
    END {
      if (!found) {
        print key "=" value
      }
    }
  ' "${environment_file}" > "${temporary_file}"
  # 새 임시 파일의 mode와 소유자/그룹을 기존 .env와 같게 맞춘다.
  # 비밀 파일의 접근 권한이 mktemp/mv 과정에서 의도치 않게 바뀌지 않게 하기 위함이다.
  chmod --reference="${environment_file}" "${temporary_file}"
  chown --reference="${environment_file}" "${temporary_file}"

  # 같은 디렉터리 안에서 완성된 파일을 원래 경로로 교체한다.
  mv "${temporary_file}" "${environment_file}"
}

# 여기부터 AWS CLI가 사용하는 자격증명은 GitHub Role이 아니다.
# 이 명령은 EC2 안에서 실행되므로 Instance Metadata Service를 통해 EC2 Instance Role의
# 임시 자격증명을 얻는다. Instance Role에는 대상 ECR image를 pull할 권한이 있어야 한다.
#
# `${backend_image%%/*}`는 첫 `/`부터 뒤를 제거해
# `123456789012.dkr.ecr.ap-northeast-2.amazonaws.com` registry 주소만 남긴다.
# Backend와 Frontend repository가 같은 registry에 있다는 배포 구성을 전제로 한 번 로그인한다.
registry="${backend_image%%/*}"
printf 'Authenticating EC2 instance role to ECR registry %s\n' "${registry}"
aws ecr get-login-password --region "${aws_region}" |
  docker login --username AWS --password-stdin "${registry}"

# 현재 .env를 `.env.before-deploy`에 백업한다. 매 배포마다 이전 백업을 덮어쓰므로
# 이 파일은 "바로 직전 배포 시점"의 값 한 벌만 보관한다.
# 그 다음 ECR_BACKEND_IMAGE와 ECR_FRONTEND_IMAGE만 새 URI로 갱신한다.
# URI의 `sha-<commit>` tag는 어느 Git commit으로 만든 image인지 추적하기 위한 값이며,
# Docker image 자체의 content digest인 `sha256:...`와는 다른 개념이다.
# MySQL, Redis, KIS, OAuth 등 다른 운영 값은 upsert 함수가 그대로 보존한다.
cp --preserve=mode,ownership "${environment_file}" "${environment_file}.before-deploy"
upsert_environment_value ECR_BACKEND_IMAGE "${backend_image}"
upsert_environment_value ECR_FRONTEND_IMAGE "${frontend_image}"

cd "${deploy_directory}"

# `config --quiet`은 .env 치환, 필수 변수, Compose 문법을 검증만 하고 실행하지 않는다.
# 검증에 성공하면 backend와 nginx 서비스의 새 image를 ECR에서 내려받는다.
# 이 pull 과정에는 앞에서 만든 Docker의 ECR login 정보가 사용된다.
docker compose --env-file "${environment_file}" --file "${compose_file}" config --quiet
docker compose --env-file "${environment_file}" --file "${compose_file}" pull backend nginx

# `up -d`는 선언된 전체 stack을 백그라운드로 원하는 상태에 맞춘다.
# image URI가 바뀐 backend/nginx container는 새 image로 생성·교체되지만,
# Compose의 named volume에 저장된 MySQL/Redis 데이터는 container 교체와 별개로 유지된다.
# `--remove-orphans`는 현재 Compose 파일에서 사라진 이전 service container를 정리한다.
docker compose --env-file "${environment_file}" --file "${compose_file}" up -d --remove-orphans

# 새 container가 시작됐다고 application이 즉시 준비되는 것은 아니므로 health check를 반복한다.
# nginx container 안에서 `http://backend:8080/api/session`을 호출한다. 여기서 backend는
# 외부 DNS가 아니라 Compose network가 service 이름을 내부 IP로 해석한 결과다.
# `exec -T`의 -T는 SSM 같은 비대화형 환경에서 불필요한 TTY 할당을 끈다.
# wget은 응답 본문을 /dev/null로 버리며 HTTP 요청이 성공했는지만 exit code로 알려 준다.
# 첫 요청을 즉시 시도하고, 실패하면 5초씩 쉬면서 최대 60번 검사한다(약 5분 한도).
for attempt in $(seq 1 60); do
  if docker compose --env-file "${environment_file}" --file "${compose_file}" exec -T nginx \
    wget -q -O /dev/null http://backend:8080/api/session; then
    # 성공 시 GitHub/SSM 기록에 현재 container 상태를 남기고 exit 0으로 끝낸다.
    docker compose --env-file "${environment_file}" --file "${compose_file}" ps
    echo "FinMate deployment is healthy."
    exit 0
  fi

  if [ "${attempt}" -eq 60 ]; then
    break
  fi

  sleep 5
done

# 제한 시간 안에 성공 응답이 없으면 현재 container 상태와 짧은 안내만 출력한다.
# 애플리케이션 로그에는 운영 설정이나 외부 API 응답 같은 민감정보가 있을 수 있으므로
# 이 script가 `docker compose logs`를 출력해 SSM/GitHub Actions까지 전달하지 않는다.
# 상세 원인은 권한 있는 운영자가 EC2에서 backend/nginx log를 직접 확인한다.
#
# `.env.before-deploy`에는 이전 image URI가 남지만 여기서는 자동 rollback하지 않는다.
# 새 코드가 DB schema를 이미 변경했을 수 있어 이전 image를 기계적으로 재실행하는 것이
# 오히려 장애를 키울 수 있기 때문이다. 실패는 exit 1로 SSM과 GitHub Actions에 전파한다.
docker compose --env-file "${environment_file}" --file "${compose_file}" ps

echo "Deployment health check failed." >&2
echo "Inspect backend/nginx logs directly on EC2." >&2
echo "Previous image values are in ${environment_file}.before-deploy." >&2

exit 1
