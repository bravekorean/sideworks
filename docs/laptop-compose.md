# 노트북 Docker Compose 배포

Docker Desktop의 Linux 엔진과 Compose를 실행합니다. 아래 명령은 저장소 루트에서 PowerShell 7로 실행합니다. 자동 검증에는 Node 24가 필요합니다.

## 로컬 설정과 실행

처음 한 번 아래 명령으로 `.env.example`을 복사하고 로컬 비밀값을 생성합니다. `.env`는 Git에서 제외됩니다. 파일이 이미 있으면 해당 파일의 설정을 사용합니다.

```powershell
if (!(Test-Path .env)) {
    $settings = Get-Content .env.example | ForEach-Object {
        if ($_ -match '^(MYSQL_PASSWORD|MYSQL_ROOT_PASSWORD|JWT_SECRET|SIDEWORKS_DEMO_PASSWORD)=$') {
            $_ + [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
        } else {
            $_
        }
    }
    Set-Content -LiteralPath .env -Value $settings -Encoding ascii
}
docker compose up -d --build --wait
docker compose ps
```

브라우저에서 `http://localhost:8088`을 엽니다. 포트가 이미 사용 중이면 `.env`의 `SIDEWORKS_HTTP_PORT`를 바꿉니다. Compose는 Nginx만 `127.0.0.1`에 공개합니다. Nginx는 `/api`를 Spring Boot로 전달하고 `/api/notifications/stream`은 응답 버퍼링을 끕니다. MySQL과 Spring Boot는 Compose 내부 네트워크에서 통신합니다.

Compose 프로젝트 `sideworks-demo`는 `sideworks_demo` 데이터베이스를 사용합니다. MySQL 컨테이너는 `mysql-data` 볼륨이 비어 있을 때 `database/bootstrap`의 스키마와 기준 데이터를 적용합니다. 그 후 Spring Boot는 `prod` 프로필과 `ddl-auto=validate`로 기동합니다. DB 준비 상태는 MySQL의 TCP 연결과 기본 문서 종류를 조회해 확인합니다. [Compose 서비스 준비 상태](https://docs.docker.com/compose/how-tos/startup-order/) · [MySQL 공식 이미지 초기화](https://hub.docker.com/_/mysql) · [Nginx 프록시 설정](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)

기존 마이그레이션의 첫 파일에는 `ALTER TABLE usertbl`이 있습니다. 빈 DB에 단독 실행하면 MySQL 오류 `1146`이 발생합니다. `10-schema.sql`은 `cb8749d`의 JPA 매핑에서 추출한 스키마에 기존 SQL의 인덱스와 검사 조건을 반영한 초기 스냅샷입니다. `20-reference-data.sql`은 결재 문서 종류와 기본 근무 정책을 추가합니다. 새 스키마 변경은 기존 DB용 마이그레이션과 빈 데모 DB용 초기 스냅샷에 각각 반영합니다. 사용 중인 DB에는 [수동 마이그레이션 순서](../database/migrations/README.md)를 따릅니다.

## 데모 계정과 검증

아래 스크립트는 `.env`의 `SIDEWORKS_DEMO_PASSWORD`를 읽고, Docker에서 BCrypt 해시를 생성해 데모 DB에 `demo-writer`, `demo-approver`, `demo-delegate`를 추가합니다. 세 계정은 같은 데모 비밀번호를 사용합니다. 이미 존재하는 계정의 비밀번호는 최초 값으로 유지됩니다.

```powershell
./deploy/seed-demo.ps1
node deploy/smoke.mjs
```

스모크 검증은 Nginx를 통해 세 계정으로 로그인하고, 원 결재자가 오늘 날짜로 대결을 설정한 뒤 작성자가 PNG를 첨부해 품의서를 상신합니다. 대결자가 승인하면 작성자의 SSE에서 완료 이벤트를 받고 결재 상태, 대결 이력, 알림 목록과 다운로드한 첨부파일 바이트를 확인합니다.

출력된 결재 ID를 기록하고 컨테이너를 재생성한 뒤 같은 문서를 조회합니다. 아래 예시에서는 ID가 `1`입니다.

```powershell
docker compose up -d --force-recreate --no-build --wait
node deploy/smoke.mjs 1
```

MySQL의 결재 이력·알림·첨부 메타데이터는 `mysql-data`에, 첨부 원본은 `attachments`에 보관됩니다. 실행을 멈추고 볼륨을 보존하려면 `docker compose down`을 사용합니다.

### 노트북 검증 결과

2026년 11월 28일 Docker Desktop Linux/amd64, Compose 5.7.0, MySQL 8.4.13에서 검증했습니다. 빈 DB에 기존 첫 마이그레이션만 적용했을 때 `1146`을 재현했습니다. 새 볼륨에서는 초기 SQL 두 개가 실행되고 Spring Boot가 스키마 검증을 통과했습니다. Nginx를 거친 자동 검증에서 결재 `1`의 대결 승인과 작성자 SSE 완료 알림을 확인했습니다. 컨테이너 3개를 재생성한 뒤 같은 결재 상태, 승인 이력, 알림과 첨부파일 바이트를 확인했습니다.

## GHCR 이미지 실행

`v1.0.0`이나 `v1.0.0-rc.1` 형식의 태그를 GitHub에 push하면 GitHub 호스팅 러너가 `linux/amd64` 백엔드·프런트엔드 이미지를 빌드하고 같은 태그로 GHCR에 발행합니다. Actions는 저장소의 기본 `GITHUB_TOKEN`을 사용합니다. 비공개 패키지는 `read:packages` 권한이 있는 classic PAT로 노트북 Docker를 로그인한 뒤 내려받습니다. [GitHub Container Registry 인증](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)

실행하려는 태그의 체크아웃에서 다음 명령을 실행합니다. `SIDEWORKS_IMAGE_TAG`에는 실제로 발행된 태그를 지정합니다.

```powershell
$env:SIDEWORKS_IMAGE_TAG = 'v0.0.1-rc.laptop.1'
docker compose -f compose.yml -f compose.ghcr.yml pull
docker compose -f compose.yml -f compose.ghcr.yml up -d --no-build --pull never --wait
docker compose -f compose.yml -f compose.ghcr.yml images
```

두 Compose 파일을 함께 사용하면 기존 데모 DB·첨부파일 볼륨에 연결됩니다. 이전 로컬 검증과 동일한 결재가 있는지 확인하려면 `node deploy/smoke.mjs 1`을 실행합니다. 새 볼륨에서 실행하는 경우 먼저 데모 계정을 추가한 뒤 `node deploy/smoke.mjs`로 새 결재를 생성합니다.

### 발행 이미지 검증 결과

`v0.0.1-rc.laptop.1`의 두 이미지를 계정 로그인 없이 내려받고, Compose에서 실행 중인 이미지 ID가 다운로드한 이미지와 일치하는지 확인했습니다. 세 서비스가 `healthy` 상태였고, 결재 `1`의 데이터와 첨부파일을 다시 읽었습니다. 같은 이미지에서 결재 `2`를 새로 만들어 대결 승인과 SSE 완료 알림까지 재검증했습니다. [GitHub Actions 발행 실행](https://github.com/bravekorean/sideworks/actions/runs/46146860217)

| 이미지 | GHCR 다이제스트 |
| --- | --- |
| `ghcr.io/bravekorean/sideworks-backend` | `sha256:161a6f1e7bd15ed311663c99c5f8b9c11ce36977af863d427b04879b7b987292` |
| `ghcr.io/bravekorean/sideworks-frontend` | `sha256:70bf0382f153c4707ee72f2ae96310975f2d05a499f9777c41cb6b3e99cfcaac` |
