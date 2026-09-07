# SideWorks 주요 트러블슈팅

마지막 갱신: 2026-09-07

이 문서는 개발 중 발견한 문제를 증상, 원인, 해결과 검증 순서로 정리한다. 실제 비밀번호, 토큰, DB 접속정보와 사용자 개인정보는 포함하지 않는다.

AWS 배포 과정에서 발생한 SSH 키 권한, 파일 경로, systemd 환경 파일, Nginx Reverse Proxy와 애플리케이션 오류 진단은 [AWS 배포 및 트러블슈팅](deployment.md)에 별도로 정리했다.

## 1. SpringDoc 도입 후 Commons Lang 취약점 경고

### 문제

Swagger/OpenAPI를 위해 springdoc-openapi를 추가한 뒤 `CVE-2025-48924` 경고가 발생했다.

### 원인

springdoc은 안전한 `commons-lang3` 버전을 요청했지만 Spring Boot의 의존성 관리 규칙이 더 낮은 버전을 최종 선택하고 있었다.

```text
springdoc-openapi
→ swagger-core-jakarta
→ commons-lang3 3.20.0 요청
→ Spring Boot 의존성 관리
→ commons-lang3 3.17.0 선택
```

### 해결

Gradle이 실제로 선택한 런타임 버전을 먼저 확인했다.

```powershell
.\gradlew.bat dependencyInsight --dependency commons-lang3 --configuration runtimeClasspath
```

Spring Boot의 전체 의존성 관리를 해제하지 않고 해당 관리 속성만 안전한 버전으로 재정의했다.

```groovy
ext['commons-lang3.version'] = '3.20.0'
```

### 검증과 학습

`dependencyInsight`에서 `commons-lang3:3.20.0`이 최종 선택된 것을 확인하고 전체 Gradle 테스트를 통과시켰다. 선언된 버전과 실제 런타임 버전은 BOM과 전이 의존성 때문에 다를 수 있으므로 빌드 성공 여부만으로 취약점 해결을 판단하면 안 된다는 점을 배웠다.

## 2. QueryDSL 의존성 경고와 유지되는 Fork 선택

### 문제

기존 QueryDSL 의존성에 대한 보안 및 유지보수 경고가 발생했다.

### 해결

Jakarta 환경과 Java 21을 지원하며 유지보수가 이어지는 OpenFeign QueryDSL Fork로 교체했다. JPA 및 APT 의존성을 같은 계열과 버전으로 맞추고 Q Class 생성과 컴파일을 다시 확인했다.

### 학습

라이브러리 교체는 좌표만 바꾸는 작업이 아니다. 런타임 모듈, Annotation Processor, Jakarta classifier와 생성 코드까지 함께 검증해야 한다.

## 3. Access Token 만료 후 여러 API가 동시에 실패

### 문제

대시보드 진입 시 여러 API가 동시에 만료된 Access Token을 사용하면 다수의 인증 실패가 발생했다. 각 요청이 개별적으로 토큰을 재발급하면 중복 호출과 경쟁 상태가 생길 수 있었다.

### 해결

Axios 응답 인터셉터에서 만료 응답을 감지하고 Refresh Token Cookie로 Access Token을 재발급했다. 진행 중인 재발급 작업을 `refreshPromise`로 공유하여 동시에 실패한 요청들이 하나의 재발급 결과를 기다리도록 만들었다.

```text
여러 API의 토큰 만료 응답
→ 최초 요청만 토큰 재발급
→ 나머지 요청은 동일 Promise 대기
→ 새 Access Token 저장
→ 각 원래 요청 재시도
```

### 검증과 학습

Access Token의 유효시간을 짧게 설정한 뒤 브라우저 Network 탭에서 `/api/auth/refresh`의 성공과 원래 요청 재시도를 확인했다. 인터셉터는 단순 중복 제거뿐 아니라 비동기 요청 사이의 동시성을 조정하는 역할도 할 수 있다.

## 4. 상신 취소 문서가 결재 대기함에 남는 문제

### 문제

작성자가 상신을 취소해 문서가 `CANCELED` 상태가 되어도 결재자의 결재 대기함에 계속 표시됐다.

### 원인

대기함 조회가 결재선의 `PENDING` 상태만 검사했다. 상신 취소는 문서 전체의 상태 전이이므로 결재선 상태가 남아 있어도 처리 가능한 문서는 아니다.

### 해결

결재 대기 조회 조건에 문서 상태가 `IN_PROGRESS`인지 확인하는 조건을 함께 추가했다.

```text
ApprovalLine = PENDING
AND Approval = IN_PROGRESS
```

### 검증과 학습

상신 취소 후 결재자 화면에서 문서가 사라지는 것을 확인했다. 연관 Entity 하나의 상태만으로 업무 가능 여부를 판단하면 전체 Aggregate의 상태와 불일치할 수 있다는 점을 확인했다.

## 5. 계층형 부서가 잘못된 부모 아래에 표시되는 문제

### 문제

백엔드팀과 플랫폼개발팀의 실제 부모는 개발팀이지만 화면에서는 경영 부서 아래에 배치된 것처럼 보였다.

### 원인

프론트엔드가 API 배열의 순서를 유지한 채 들여쓰기 깊이만 계산했다. 부모와 자식이 배열에서 인접하도록 출력 순서를 다시 구성하지 않았다.

### 해결

부모 ID별로 자식을 그룹화하고 루트부터 pre-order 방식으로 순회해 화면용 배열을 생성했다. 같은 계층은 이름순으로 정렬하고 방문 집합으로 순환과 누락 상황을 방어했다.

### 학습

`parentId`와 `depth` 계산만으로 트리 UI가 완성되지는 않는다. 계층 구조를 사람이 읽을 수 있게 표시하려면 부모 다음에 자식이 이어지도록 별도의 순회와 정렬이 필요하다.

## 6. 사용자 수정 Dialog가 상세 Drawer 뒤에 표시되는 문제

### 문제

사용자 상세 화면에서 상태·역할·배정 변경 Dialog가 생성됐지만 상세 Drawer 뒤에 가려져 조작할 수 없었다.

### 원인과 해결

MUI Overlay 컴포넌트 사이의 레이어 순서가 충돌했다. Dialog가 Drawer보다 높은 계층에 표시되도록 렌더링 구조와 `z-index`를 조정했다.

### 학습

화면에 DOM이 생성됐다는 사실과 사용자가 실제로 조작할 수 있다는 사실은 다르다. Modal, Drawer, Popover처럼 Portal을 사용하는 UI는 focus와 stacking context까지 브라우저에서 확인해야 한다.

## 7. DataSource 설정 누락으로 애플리케이션 시작 실패

### 문제

DB URL과 Driver를 찾지 못해 Spring Boot 애플리케이션이 시작되지 않았다.

### 원인과 해결

DB 설정을 가진 profile이 활성화되지 않았거나 로컬 설정이 누락된 것이 원인이었다. 환경별 설정을 별도 properties 파일로 분리하고 로컬 설정 파일을 Git 추적 대상에서 제외했다.

### 학습

소스 코드와 환경 설정의 생명주기는 다르다. 저장소에는 설정의 구조와 필요한 환경변수 이름만 남기고 실제 자격증명은 실행 환경에서 주입해야 한다.

## 8. 공개 저장소 전환 전 Git 히스토리 점검

현재 작업 트리에서 파일을 삭제해도 과거 커밋에는 내용이 남을 수 있으므로, 추적 파일뿐 아니라 전체 Git 히스토리의 토큰·키·인증서 패턴도 검사했다. 과거에 추적된 설정 파일에는 비밀값이 없었고 현재 민감 설정 파일도 Git에서 제외된 것을 확인했다.

실제 자격증명이 커밋된 경우에는 현재 파일에서 삭제하는 것보다 해당 자격증명을 먼저 폐기·재발급하는 것이 우선이다.
