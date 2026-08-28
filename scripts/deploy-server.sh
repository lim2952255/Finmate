#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <aws-region> <backend-image> <frontend-image>" >&2
  exit 64
fi

aws_region="$1"
backend_image="$2"
frontend_image="$3"
deploy_directory="/opt/finmate"
compose_file="${deploy_directory}/docker-compose.server.yml"
environment_file="${deploy_directory}/.env"

for command in aws docker; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "Required command is not installed: ${command}" >&2
    exit 69
  fi
done

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required." >&2
  exit 69
fi

if [ ! -f "${compose_file}" ]; then
  echo "Compose file does not exist: ${compose_file}" >&2
  exit 66
fi

if [ ! -f "${environment_file}" ]; then
  echo "Create ${environment_file} with the production secrets before the first deployment." >&2
  exit 66
fi

if [[ "${backend_image}" != *.dkr.ecr.*.amazonaws.com/*:* ]] ||
  [[ "${frontend_image}" != *.dkr.ecr.*.amazonaws.com/*:* ]]; then
  echo "Both image arguments must be tagged private ECR image URIs." >&2
  exit 65
fi

upsert_environment_value() {
  local key="$1"
  local value="$2"
  local temporary_file

  temporary_file="$(mktemp "${deploy_directory}/.env.XXXXXX")"
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
  chmod --reference="${environment_file}" "${temporary_file}"
  chown --reference="${environment_file}" "${temporary_file}"
  mv "${temporary_file}" "${environment_file}"
}

registry="${backend_image%%/*}"
printf 'Authenticating EC2 instance role to ECR registry %s\n' "${registry}"
aws ecr get-login-password --region "${aws_region}" |
  docker login --username AWS --password-stdin "${registry}"

cp --preserve=mode,ownership "${environment_file}" "${environment_file}.before-deploy"
upsert_environment_value ECR_BACKEND_IMAGE "${backend_image}"
upsert_environment_value ECR_FRONTEND_IMAGE "${frontend_image}"

cd "${deploy_directory}"
docker compose --env-file "${environment_file}" --file "${compose_file}" config --quiet
docker compose --env-file "${environment_file}" --file "${compose_file}" pull backend nginx
docker compose --env-file "${environment_file}" --file "${compose_file}" up -d --remove-orphans

for attempt in $(seq 1 60); do
  if docker compose --env-file "${environment_file}" --file "${compose_file}" exec -T nginx \
    wget -q -O /dev/null http://backend:8080/api/session; then
    docker compose --env-file "${environment_file}" --file "${compose_file}" ps
    echo "FinMate deployment is healthy."
    exit 0
  fi

  if [ "${attempt}" -eq 60 ]; then
    break
  fi

  sleep 5
done

docker compose --env-file "${environment_file}" --file "${compose_file}" ps
docker compose --env-file "${environment_file}" --file "${compose_file}" logs --tail 100 backend nginx >&2
echo "Deployment health check failed. Previous image values are in ${environment_file}.before-deploy." >&2
exit 1
