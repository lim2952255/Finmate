# FinMate GitHub OIDC + AWS Systems Manager CI/CD

## 1. 배포 흐름

```text
main push / manual run
  -> frontend lint·build + backend test
  -> GitHub OIDC로 FinMateGitHubCicdRole 임시 세션 발급
  -> backend/frontend 이미지를 commit SHA 태그로 ECR에 push
  -> SSM Run Command로 EC2에 compose와 배포 스크립트 전달
  -> EC2 instance role로 ECR login·pull
  -> docker compose up
  -> nginx 컨테이너에서 backend /api/session health check
```

GitHub에는 AWS access key나 애플리케이션 비밀값을 저장하지 않는다. 운영 `.env`는 EC2의
`/opt/finmate/.env`에만 두고, workflow에는 비밀이 아닌 리소스 식별자를 GitHub Environment variable로 둔다.

## 2. GitHub OIDC role 신뢰 정책

IAM의 `FinMateGitHubCicdRole` 신뢰 관계가 `production` GitHub Environment만 허용하도록 설정한다.
`<AWS_ACCOUNT_ID>`만 실제 계정 ID로 바꾼다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<AWS_ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:lim2952255/Finmate:environment:production"
        }
      }
    }
  ]
}
```

GitHub 저장소가 2026년 7월 15일 이후 생성됐거나 새 organization/repository ID claim 형식을 명시적으로
활성화했다면 `sub`에 ID suffix가 붙을 수 있다. 이 저장소처럼 그 이전에 생성된 저장소는 기본적으로 위 형식을
사용한다. OIDC provider URL은 `https://token.actions.githubusercontent.com`, audience는 `sts.amazonaws.com`이다.

## 3. GitHub OIDC role 권한 정책

아래 값들을 실제 region, 계정 ID, ECR repository 이름, EC2 instance ID로 바꾼다. GitHub role은 두 ECR
repository에 push하고 지정한 EC2 한 대에 AWS 제공 `AWS-RunShellScript` 문서만 실행할 수 있다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "GetEcrAuthorizationToken",
      "Effect": "Allow",
      "Action": "ecr:GetAuthorizationToken",
      "Resource": "*"
    },
    {
      "Sid": "PushFinMateImages",
      "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability",
        "ecr:BatchGetImage",
        "ecr:CompleteLayerUpload",
        "ecr:InitiateLayerUpload",
        "ecr:PutImage",
        "ecr:UploadLayerPart"
      ],
      "Resource": [
        "arn:aws:ecr:<AWS_REGION>:<AWS_ACCOUNT_ID>:repository/finmate-backend",
        "arn:aws:ecr:<AWS_REGION>:<AWS_ACCOUNT_ID>:repository/finmate-frontend"
      ]
    },
    {
      "Sid": "RunDeploymentOnFinMateInstance",
      "Effect": "Allow",
      "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ssm:<AWS_REGION>::document/AWS-RunShellScript",
        "arn:aws:ec2:<AWS_REGION>:<AWS_ACCOUNT_ID>:instance/<EC2_INSTANCE_ID>"
      ]
    },
    {
      "Sid": "ReadDeploymentCommandResult",
      "Effect": "Allow",
      "Action": "ssm:GetCommandInvocation",
      "Resource": "*"
    }
  ]
}
```

두 ECR repository는 workflow 첫 실행 전에 생성해야 한다. tag는 `sha-<40자리 commit SHA>` 형식이며 `latest`를
사용하지 않는다. ECR의 tag immutability와 scan-on-push를 활성화하는 것을 권장한다.

```bash
aws ecr create-repository \
  --region <AWS_REGION> \
  --repository-name finmate-backend \
  --image-tag-mutability IMMUTABLE \
  --image-scanning-configuration scanOnPush=true

aws ecr create-repository \
  --region <AWS_REGION> \
  --repository-name finmate-frontend \
  --image-tag-mutability IMMUTABLE \
  --image-scanning-configuration scanOnPush=true
```

## 4. EC2 instance role

GitHub OIDC role과 EC2 role은 서로 다른 역할이다. EC2에는 instance profile을 연결하고 다음 권한을 부여한다.

- AWS 관리형 정책 `AmazonSSMManagedInstanceCore`
- 아래 ECR pull 전용 인라인 정책

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "ecr:GetAuthorizationToken",
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability",
        "ecr:BatchGetImage",
        "ecr:GetDownloadUrlForLayer"
      ],
      "Resource": [
        "arn:aws:ecr:<AWS_REGION>:<AWS_ACCOUNT_ID>:repository/finmate-backend",
        "arn:aws:ecr:<AWS_REGION>:<AWS_ACCOUNT_ID>:repository/finmate-frontend"
      ]
    }
  ]
}
```

EC2가 Systems Manager의 **Managed nodes**에 `Online`으로 보여야 한다. private subnet이면 NAT gateway를
사용하거나 SSM, SSMMessages, EC2Messages 및 ECR API/DKR와 S3용 VPC endpoint를 구성해야 한다.

## 5. EC2 최초 1회 준비

SSM Agent, AWS CLI v2, Docker Engine, Docker Compose v2를 설치하고 Docker 서비스를 활성화한다. AMI마다 설치
명령이 다르므로 설치 후 아래 조건을 먼저 확인한다.

```bash
aws --version
docker --version
docker compose version
sudo systemctl enable --now docker
```

운영 디렉터리와 `.env`를 만든다. `.env`에는 현재 개발 가이드의 운영 환경변수를 입력하되
`ECR_BACKEND_IMAGE`, `ECR_FRONTEND_IMAGE`는 workflow가 첫 배포 때 추가하므로 미리 넣지 않아도 된다.

```bash
sudo install -d -m 0755 /opt/finmate
sudo touch /opt/finmate/.env
sudo chmod 0600 /opt/finmate/.env
sudoedit /opt/finmate/.env
```

MySQL과 Redis를 이 Compose에서 운영하므로 기존 볼륨이 있다면 삭제하지 않는다. 인증서 기반 HTTPS를 사용하는
현재 Nginx 설정에는 EC2 호스트의 `/etc/letsencrypt/live/finmate-project.com/` 인증서가 먼저 있어야 한다.

## 6. GitHub Environment 설정

GitHub 저장소의 **Settings → Environments → New environment**에서 `production`을 만들고 deployment branch를
`main`으로 제한한다. 다음 Environment variables를 등록한다.

| 이름 | 값 예시 |
| --- | --- |
| `AWS_ACCOUNT_ID` | AWS 계정의 12자리 ID |
| `AWS_REGION` | `ap-northeast-2` |
| `AWS_ROLE_ARN` | `arn:aws:iam::<AWS_ACCOUNT_ID>:role/FinMateGitHubCicdRole` |
| `EC2_INSTANCE_ID` | `i-...` |
| `ECR_BACKEND_REPOSITORY` | `finmate-backend` |
| `ECR_FRONTEND_REPOSITORY` | `finmate-frontend` |

이 값들은 secret이 아니다. AWS access key/secret key를 GitHub Secrets에 만들 필요가 없다.

## 7. 실행과 확인

`main` push 또는 Actions의 수동 실행에서 test job이 성공하면 deploy job이 수행된다. 배포 확인 명령은 SSM
Session Manager에서 실행할 수 있다.

```bash
cd /opt/finmate
sudo docker compose --env-file .env -f docker-compose.server.yml ps
sudo docker compose --env-file .env -f docker-compose.server.yml logs --tail 100 backend nginx
```

배포 전 이미지 값은 `/opt/finmate/.env.before-deploy`에 보존된다. 실패 시 자동 rollback하지 않는다. 현재
애플리케이션이 `spring.jpa.hibernate.ddl-auto=update`를 사용하므로 코드 이미지만 자동으로 되돌리면 이미 변경된
DB schema와 불일치할 수 있기 때문이다. rollback은 DB 호환성을 확인한 뒤 이전 SHA tag를 명시해 수행한다.
