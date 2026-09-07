# SideWorks V1 AWS 배포 기록

마지막 갱신: 2026-09-07

## 1. 문서 목적

이 문서는 SideWorks V1을 AWS에 수동 배포하면서 결정한 구조, 실제 배포 절차, 검증 결과와 트러블슈팅 과정을 기록한다.

배포의 목적은 장기 운영이 아니라 다음 항목을 직접 검증하는 것이었다.

* 로컬 MySQL 데이터를 Amazon RDS로 이전
* React 정적 파일을 Nginx로 제공
* Nginx가 `/api` 요청을 Spring Boot로 전달
* Spring Boot를 systemd 서비스로 운영
* 로컬과 운영 설정 및 자격증명 분리
* 로그인부터 전자결재 완료까지 전체 흐름 검증

검증을 마친 뒤 지속적인 리소스 사용을 방지하기 위해 EC2와 RDS를 삭제했다. 따라서 이 문서는 현재 실행 중인 운영 환경을 설명하는 문서가 아니라, 재현 가능한 V1 배포 실습 기록이다.

## 2. 최종 배포 구조

```text
사용자 브라우저
    │ HTTP :80
    ▼
Amazon EC2
    ├─ Nginx
    │   ├─ /       → React 정적 파일
    │   └─ /api/** → 127.0.0.1:8080으로 Reverse Proxy
    │
    └─ Spring Boot 3.5 / Java 21
        ├─ systemd 서비스
        ├─ JWT 인증 및 인가
        ├─ JPA / QueryDSL
        └─ TLS 연결
              ▼
        Amazon RDS for MySQL
        ├─ Private access
        └─ EC2 보안 그룹에서만 3306 접근 허용
```

Nginx와 Spring Boot를 같은 EC2에서 실행했다. Spring Boot는 `127.0.0.1:8080`에만 바인딩하여 외부에서 애플리케이션 포트로 직접 접근할 수 없게 하고, 외부 요청은 Nginx의 80번 포트만 통과하도록 구성했다.

프론트엔드와 API를 동일 Origin으로 제공하면 브라우저가 `http://<EC2_PUBLIC_HOST>/api/**`로 요청한다. 이 구조는 프론트엔드와 API를 서로 다른 호스트에 둘 때보다 CORS와 Cookie 정책이 단순하며, 단일 서버 MVP에 적합하다.

## 3. 실습 환경

### EC2

* Region: Asia Pacific (Seoul)
* OS: Amazon Linux 2023
* Instance class: `t3.small`
* Storage: 20 GiB EBS
* Runtime: Amazon Corretto 21
* Web server: Nginx
* Backend process management: systemd

### RDS

* Engine: MySQL Community 8.4.6
* Instance class: `db.t4g.micro`
* Storage: 20 GiB
* Public access: disabled
* Character set: `utf8mb4`
* Collation: `utf8mb4_0900_ai_ci`
* Time zone: `Asia/Seoul`
* Transport encryption: TLS

RDS는 EC2와 같은 VPC에 배치했다. DB 보안 그룹은 인터넷 전체나 특정 공인 IP가 아니라 EC2에 연결된 보안 그룹을 Source로 사용했다. 애플리케이션 DB 계정에는 대상 스키마의 `SELECT`, `INSERT`, `UPDATE`, `DELETE`만 허용하고 TLS 연결을 요구했다.

## 4. 로컬과 운영 설정 분리

### 문제

기존 프론트엔드는 API 주소를 `http://localhost:8080/api`로 고정하고 있었다. 그대로 빌드하면 배포 후 사용자의 브라우저가 서버가 아닌 사용자 PC의 8080 포트로 요청한다.

또한 로컬 DB 자격증명과 JWT Secret이 들어 있는 `application-local.properties`가 운영 JAR에 포함되면, 운영 프로필을 사용하더라도 압축 파일을 열어 로컬 비밀값을 확인할 수 있다.

### 최종 구성

* `application.properties`
  * 환경 공통 설정 관리
  * 별도 지정이 없을 때 `local`을 기본 프로필로 사용
* `application-local.properties`
  * 로컬 DB와 로컬 JWT 설정 관리
  * Git 추적 대상에서 제외
* `application-prod.properties`
  * 실제 값 대신 환경변수 자리표만 관리
  * SQL 출력과 Swagger 비활성화
  * Spring Boot를 `127.0.0.1:8080`에 바인딩
* `build.gradle`
  * `bootJar`에서 `application-local.properties` 제외
* `frontend/src/api/apiClient.jsx`
  * API Base URL을 `/api`로 통일
* `frontend/vite.config.js`
  * 로컬 개발 중 `/api` 요청을 `localhost:8080`으로 Proxy

```text
로컬 실행
React dev server :5173
    └─ /api → Vite proxy → Spring Boot :8080 → Local MySQL

운영 실행
Browser
    └─ /api → Nginx :80 → Spring Boot 127.0.0.1:8080 → RDS
```

이 구조에서는 로컬로 돌아갈 때 소스 코드를 수정할 필요가 없다. 운영에서는 systemd가 `SPRING_PROFILES_ACTIVE=prod`를 지정한다.

## 5. 빌드와 배포 절차

아래 명령의 호스트, 키 파일과 자격증명은 예시 자리표이다. 실제 값은 저장소나 문서에 기록하지 않는다.

### 5.1 Backend 빌드

```powershell
.\gradlew.bat clean test bootJar
```

테스트와 JAR 빌드를 함께 실행하여 컴파일 성공만 확인하는 것보다 회귀 위험을 줄였다. 생성된 JAR 내부에는 다음 두 설정만 존재하는지 별도로 확인했다.

```text
BOOT-INF/classes/application.properties
BOOT-INF/classes/application-prod.properties
```

`application-local.properties`가 없는 것을 확인하여 로컬 DB 자격증명과 JWT Secret이 운영 산출물에 포함되지 않았음을 검증했다.

### 5.2 Frontend 빌드

```powershell
cd frontend
npm run build
```

빌드 후 `frontend/dist` 내부 산출물에 `localhost:8080` 문자열이 남지 않았는지 확인했다.

### 5.3 산출물 전송

```powershell
scp -i "<SSH_PRIVATE_KEY>" `
  "build/libs/<APPLICATION_JAR>" `
  ec2-user@<EC2_PUBLIC_HOST>:/home/ec2-user/sideworks.jar

scp -i "<SSH_PRIVATE_KEY>" -r `
  "frontend/dist" `
  ec2-user@<EC2_PUBLIC_HOST>:/home/ec2-user/
```

Backend JAR은 `/opt/sideworks/sideworks.jar`로 이동하고, React 산출물은 Nginx 기본 정적 파일 경로에 복사했다.

### 5.4 운영 환경변수

운영 자격증명은 `/etc/sideworks/sideworks.env`에 저장하고 소유자와 권한을 제한했다.

```text
SPRING_PROFILES_ACTIVE
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
APP_CORS_ALLOWED_ORIGINS
APP_COOKIE_SECURE
```

실제 값은 이 문서에 기록하지 않는다. 파일 권한은 다음과 같이 설정했다.

```bash
sudo chown root:root /etc/sideworks/sideworks.env
sudo chmod 600 /etc/sideworks/sideworks.env
```

운영 JWT Secret은 로컬 값과 공유하지 않고 별도로 생성했다. 운영 Secret을 변경하면 이전에 발급한 토큰은 서명 검증에 실패하므로 모든 사용자가 다시 로그인해야 한다.

### 5.5 systemd 서비스

Spring Boot는 터미널 세션과 독립적으로 실행되고 EC2 재시작 후 자동으로 시작되도록 systemd에 등록했다.

```ini
[Unit]
Description=SideWorks Spring Boot Application
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=ec2-user
Group=ec2-user
WorkingDirectory=/opt/sideworks
EnvironmentFile=/etc/sideworks/sideworks.env
ExecStart=/usr/bin/java -jar /opt/sideworks/sideworks.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now sideworks
sudo systemctl status sideworks --no-pager
```

### 5.6 Nginx Reverse Proxy

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;

    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

location / {
    try_files $uri $uri/ /index.html;
}
```

`proxy_pass` 뒤에 `/`를 추가하지 않았다. 이 구성에서는 원래 요청의 `/api` 경로가 Spring Boot까지 보존된다. `try_files`는 React Router 주소에서 브라우저를 새로고침했을 때 정적 파일 404가 발생하지 않도록 `index.html`로 Fallback한다.

```bash
sudo nginx -t
sudo systemctl enable nginx
sudo systemctl reload nginx
```

## 6. DB 이전과 검증

로컬 MySQL 스키마와 데이터를 MySQL Workbench의 Self-Contained SQL 파일로 Export한 뒤 EC2를 경유하여 RDS의 대상 스키마로 Import했다.

덤프 파일에는 사용자 데이터와 비밀번호 해시 등 민감할 수 있는 정보가 포함되므로 공개 저장소에 추가하지 않았고, 배포 검증 후 서버에 남은 사본을 제거 대상으로 분류했다.

Import 후 다음 항목을 SQL로 확인했다.

* RDS MySQL 버전
* 대상 스키마와 테이블 생성 여부
* 사용자 데이터 건수
* 서버 Character set과 Collation
* Global 및 Session time zone
* 현재 연결의 TLS Cipher

```sql
SELECT VERSION();
SELECT @@character_set_server, @@collation_server;
SELECT @@global.time_zone, @@session.time_zone, NOW(), UTC_TIMESTAMP();
SHOW SESSION STATUS LIKE 'Ssl_cipher';
```

`Ssl_cipher`에 실제 Cipher Suite가 표시되는 것을 확인하여 단순히 TLS 옵션을 지정한 것에 그치지 않고 현재 세션이 암호화됐음을 검증했다.

## 7. 전체 기능 검증

배포 후 다음 업무 흐름을 테스트 계정과 테스트 문서로 실행했다.

1. 로그인
2. 대시보드 진입
3. 전자결재 문서 작성 및 상신
4. 결재자 계정의 결재 대기함에서 문서 확인
5. 승인 의견 입력 및 승인
6. 작성자와 결재자의 처리함에서 완료 상태 확인
7. 결재선과 처리 이력 확인
8. 참조자 계정에서 참조 문서 조회
9. 브라우저 새로고침 후 인증 상태와 데이터 유지 확인
10. 로그아웃 후 보호된 화면 접근 차단 확인

이 검증으로 정적 화면뿐 아니라 다음 전체 경로가 실제로 동작함을 확인했다.

```text
React UI
→ Nginx Reverse Proxy
→ Spring Security / JWT
→ Controller / Service
→ JPA / QueryDSL
→ RDS MySQL
```

기능 화면은 별도 로컬 산출물로 보관했다. 화면에 테스트 사용자 이름이 표시되므로 원본 이미지를 공개 저장소에 자동 업로드하지 않는다. 포트폴리오 공개 시 테스트 데이터만 사용하고 계정 ID, IP, DNS, DB Endpoint, 사용자 개인정보, Token과 Cookie를 가린다.

## 8. 배포 트러블슈팅

### 8.1 Amazon Linux 패키지 이름 불일치

#### 증상

예상한 Java 21 Corretto 패키지 이름으로 `dnf install`을 실행했지만 패키지를 찾지 못했다.

#### 원인과 해결

배포판과 활성 Repository에 따라 설치 가능한 패키지 이름이 다를 수 있다. 기억한 패키지 이름을 반복 사용하지 않고 `dnf search`와 Repository 정보를 확인한 뒤 실제 제공되는 Java 21 Corretto 패키지를 설치했다.

#### 학습

운영체제 이름이 같더라도 버전과 Repository 상태에 따라 패키지 이름과 제공 범위가 달라질 수 있다. 설치 문서는 OS와 버전을 함께 기록해야 한다.

### 8.2 MySQL Client TLS 옵션 차이

#### 증상

EC2의 MySQL Client가 안내받은 `--ssl-mode=VERIFY_IDENTITY` 옵션을 인식하지 못했다.

#### 원인과 해결

MySQL 호환 Client라도 배포판과 Client 종류에 따라 지원하는 옵션이 다를 수 있다. Client가 지원하는 연결 방식으로 접속한 뒤 `SHOW SESSION STATUS LIKE 'Ssl_cipher'`를 실행하여 TLS 적용 여부를 결과로 검증했다.

#### 학습

설정 문자열만 보고 보안 적용을 판단하면 안 된다. 실제 연결 세션의 상태를 확인해야 한다.

### 8.3 RDS 접속 권한 거부

#### 증상

EC2에서 RDS로 연결할 때 `Access denied`가 발생했다.

#### 원인과 해결

보안 그룹이나 네트워크 단절이 아니라 DB 사용자 자격증명 불일치였다. 관리 계정으로 자격증명을 재설정하고 애플리케이션 전용 계정으로 다시 접속하여 해결했다.

#### 학습

`Connection timed out`과 `Access denied`는 원인 계층이 다르다. 전자는 네트워크와 보안 그룹을, 후자는 사용자·Host·비밀번호·권한을 우선 확인한다.

### 8.4 파라미터 그룹 설명 입력 오류

#### 증상

RDS 파라미터 그룹 생성 시 Description에 허용되지 않는 문자가 포함됐다는 오류가 발생했다.

#### 원인과 해결

영문 ASCII 중심의 설명으로 변경해 파라미터 그룹을 생성했다. `time_zone`을 `Asia/Seoul`로 설정하고 DB 인스턴스에 연결한 뒤 재부팅했다.

#### 검증

```sql
SELECT @@global.time_zone, @@session.time_zone, NOW(), UTC_TIMESTAMP();
```

Global과 Session time zone이 모두 `Asia/Seoul`이고 `NOW()`와 UTC 사이에 9시간 차이가 나는 것을 확인했다.

### 8.5 Markdown SQL Escape 문자로 인한 문법 오류

#### 증상

시간대 확인 SQL을 붙여넣었을 때 별칭 근처에서 MySQL 1064 문법 오류가 발생했다.

#### 원인과 해결

Markdown에서 사용한 Escape 문자 `\`가 SQL에 함께 복사됐다. Escape 문자를 제거하고 안전한 별칭으로 변경하여 실행했다.

#### 학습

문서에 표시된 코드와 터미널에 전달되는 실제 문자열이 같다는 보장은 없다. 명령을 붙여넣을 때 Markdown Escape와 스마트 따옴표를 확인해야 한다.

### 8.6 Windows OpenSSH에서 PEM 권한 거부

#### 증상

Windows `scp`가 개인 키 파일의 권한이 너무 넓다며 키를 무시했고, SSH 인증이 실패했다.

#### 원인과 해결

개인 키 ACL을 현재 사용자만 읽을 수 있도록 제한한 뒤 다시 전송했다. PuTTY를 사용할 때는 PEM을 PPK로 변환하여 동일 키 페어로 접속했다.

#### 학습

개인 키는 파일 내용뿐 아니라 파일 시스템 권한도 보안 정책의 일부이다. 경로 문제와 인증 문제를 구분하려면 먼저 키 파일이 실제 경로에 존재하는지 확인하고 다음으로 ACL을 확인한다.

### 8.7 업로드 파일 경로 혼동

#### 증상

JAR을 `/home/ec2-user/sideworks.jar`에서 `/opt/sideworks/sideworks.jar`로 이동하려 했지만 원본 파일을 찾지 못했다.

#### 원인과 해결

파일 전송과 이동 명령의 실행 순서가 섞였거나 파일이 이미 이동된 상태였다. `ls -lh`로 출발지와 목적지를 각각 확인하여 목적지에 JAR이 존재하는 것을 검증했다.

#### 학습

파일 작업 오류에서 명령을 반복하기 전에 현재 상태를 확인해야 한다. `No such file`은 작업 실패뿐 아니라 이전 이동이 이미 성공했다는 의미일 수도 있다.

### 8.8 Nginx 연결 거부

#### 증상

정적 파일을 복사한 뒤 브라우저에서 `ERR_CONNECTION_REFUSED`가 발생했다.

#### 원인과 해결

80번 포트에서 Nginx가 실행 중인지 확인했다. 설정 문법을 검사한 뒤 서비스를 재시작하고 부팅 자동 시작을 활성화했다.

```bash
sudo nginx -t
sudo systemctl restart nginx
sudo systemctl enable nginx
sudo ss -lntp | grep ':80'
```

#### 학습

브라우저의 연결 거부는 HTTP 404나 500과 다르다. 연결 거부는 애플리케이션 라우팅보다 먼저 Process, Listen Port와 보안 그룹 계층을 확인해야 한다.

### 8.9 로그인 API 404

#### 증상

React 로그인 화면은 열렸지만 로그인 요청이 404를 반환했다.

#### 원인과 해결

Nginx가 `/api/auth/login`을 정적 파일 경로로 해석하고 있었다. `/api/` Location을 추가해 Spring Boot의 `127.0.0.1:8080`으로 전달했다.

#### 학습

정적 화면 표시 성공은 Backend 연결 성공을 의미하지 않는다. Nginx 정적 파일 제공과 API Reverse Proxy를 독립적으로 검증해야 한다.

### 8.10 POST 전용 URL의 GET 요청이 500으로 응답

#### 증상

Backend 상태 확인을 위해 `GET /api/auth/login`을 호출했을 때 예상한 405 대신 500 응답이 발생했다.

#### 원인

로그인 Endpoint는 정상적으로 POST만 지원했지만 `GlobalExceptionHandler`의 최상위 `Exception` Handler가 Spring MVC의 Method Not Allowed 예외까지 `INTERNAL_SERVER_ERROR`로 변환했다.

#### 현재 판단

systemd 상태와 `127.0.0.1:8080` Listen 여부, 실제 POST 로그인 성공으로 Backend 실행은 검증했다. 다만 잘못된 HTTP Method를 500으로 반환하는 동작은 관측 가능성과 API 의미를 떨어뜨리므로 별도 개선 항목으로 남긴다.

#### 개선 방향

`HttpRequestMethodNotSupportedException`을 405로 명시 처리하고, 광범위한 Exception Handler에서는 내부 예외를 서버 로그에 남기되 응답에 상세정보를 노출하지 않는다.

## 9. 보안 결정과 한계

### 적용한 항목

* RDS Public access 비활성화
* RDS 3306 접근 Source를 EC2 보안 그룹으로 제한
* 애플리케이션 전용 DB 계정과 최소 권한 사용
* DB 연결 TLS 적용 및 Session Cipher 확인
* 운영 JWT Secret을 로컬 Secret과 분리
* 운영 환경파일을 root 소유, 권한 600으로 제한
* 로컬 설정 파일을 Git과 운영 JAR에서 제외
* Spring Boot 8080 포트를 Loopback에만 바인딩
* Swagger를 운영 프로필에서 비활성화
* AWS 계정 식별자, 주소, Endpoint와 자격증명을 문서 및 화면에서 마스킹

### 실습 환경의 한계

이번 실습은 Domain과 TLS 인증서 없이 HTTP로 진행했다. 따라서 `APP_COOKIE_SECURE=false`를 사용했고 실제 사용자 자격증명이나 개인정보를 입력하지 않았다.

장기 공개 운영 환경에서는 다음 작업이 선행되어야 한다.

1. Domain과 HTTPS 인증서 적용
2. HTTP에서 HTTPS로 Redirect
3. `APP_COOKIE_SECURE=true` 적용
4. 보안 Header와 Cookie 정책 재검토
5. Secret Manager 또는 Parameter Store 도입 검토
6. 운영 로그에서 개인정보와 Token 마스킹

## 10. 운영 및 유지보수 관점

현재 구조는 개인 프로젝트 MVP에서 이해하고 운영하기 쉽다는 장점이 있다. Nginx, Spring Boot와 React 배포 위치가 하나의 EC2에 있어 장애 지점과 배포 절차를 추적하기 쉽고 비용도 비교적 단순하다.

반면 다음 한계가 있다.

* 단일 EC2 장애가 전체 서비스 장애로 이어진다.
* 수동 SCP 배포는 재현성과 변경 이력이 약하다.
* 새 JAR 교체 중 짧은 중단이 발생한다.
* 중앙 로그와 애플리케이션 메트릭이 없다.
* 고정 IP나 Domain이 없어 EC2 재시작 시 주소 변경에 대응해야 한다.

다음 단계에서는 트래픽 요구가 없는 상태에서 MSA나 Kubernetes를 도입하기보다, GitHub Actions 기반 빌드·배포 자동화, HTTPS, Health Check와 기본 모니터링을 우선 검토한다.

## 11. 재배포 체크리스트

### 배포 전

* 테스트용 데이터만 사용하는가
* 로컬 설정과 Secret 파일이 Git에서 제외됐는가
* 운영 JAR에 `application-local.properties`가 없는가
* RDS가 Private access인가
* RDS 보안 그룹 Source가 EC2 보안 그룹인가
* 운영 DB 계정이 최소 권한과 TLS 요구 조건을 가지는가

### 배포 후

* `sideworks.service`가 `active`인가
* Nginx가 `active`이고 현재 구성한 공개 포트에 Listen하는가
* Spring Boot가 `127.0.0.1:8080`에만 Listen하는가
* 로그인, Token 재발급과 로그아웃이 동작하는가
* 상신, 승인, 처리 이력과 참조자 조회가 동작하는가
* React Route 새로고침이 404를 만들지 않는가
* 민감정보가 로그와 공개 화면에 노출되지 않는가

### 실습 종료 후

* EC2와 RDS를 중지하거나 삭제했는가
* 미연결 EBS Volume이 남지 않았는가
* Elastic IP가 남지 않았는가
* 수동 Snapshot과 보존된 자동 Backup이 남지 않았는가
* 사용하지 않는 개인 키를 안전하게 폐기하거나 보관했는가

## 12. 최종 결과

SideWorks V1은 AWS 환경에서 로그인부터 전자결재 상신, 결재자 승인, 처리 이력과 참조자 조회까지 전체 흐름이 동작했다. 로컬과 운영 설정을 분리하고 Nginx Reverse Proxy, systemd, Private RDS와 TLS 연결을 직접 구성하여 애플리케이션 코드뿐 아니라 실행 환경과 네트워크 경계까지 검증했다.

배포 검증 및 화면 기록 후 EC2, RDS, EBS, Elastic IP와 Snapshot 잔존 여부를 확인하고 실습 리소스를 삭제했다.
