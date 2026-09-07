# SideWorks 아키텍처

마지막 갱신: 2026-09-07

## 1. 프로젝트 목적

SideWorks는 학습 및 취업 포트폴리오를 목적으로 개발하는 그룹웨어 MVP이다.

V1은 사용자·부서·직급 관리, JWT 인증, 기본 전자결재, React 화면 연동과 배포까지 완성하는 것을 목표로 한다. 복잡한 자동 결재선, 세부 권한, 실시간 알림과 협업 채팅은 V2에서 실제 요구에 맞춰 확장한다.

## 2. 기술 스택

### Backend

* Java 21
* Spring Boot 3.5.x
* Spring Web MVC
* Spring Security
* JWT(JJWT)
* Spring Data JPA
* QueryDSL
* MySQL 8.0
* Gradle
* springdoc-openapi 2.9

### Frontend

* React 19
* Vite
* MUI

## 3. 전체 요청 흐름

```text
React / Postman
    ↓ HTTP 요청
Spring Security Filter Chain
    ↓ JwtAuthenticationFilter
Controller
    ↓ DTO 변환 및 요청 전달
Service
    ↓ 트랜잭션 및 비즈니스 흐름 조정
Validator / Factory / Entity
    ↓ 검증, 객체 생성, 상태 변경
Repository / QueryDSL
    ↓
MySQL
```

Controller는 HTTP 요청과 응답을 담당하고, Service는 트랜잭션과 유스케이스 흐름을 조정한다. Entity는 자신의 상태 변경 규칙을 가지며, Repository는 영속성과 조회를 담당한다.

복잡도가 충분하지 않은 기능에는 Validator나 Factory를 기계적으로 추가하지 않는다. 현재는 전자결재 상신처럼 검증과 생성 책임이 집중된 기능에만 분리 구조를 적용한다.

## 4. 주요 패키지 책임

```text
com.example.sideworks
├─ auth        로그인, JWT 발급 및 인증 필터
├─ user        사용자와 관리자용 사용자 관리
├─ department  계층형 부서와 부서장 관리
├─ position    직급과 직급 순서 관리
├─ approval    문서, 결재선, 참조자, 결재 이력
├─ common      공통 Entity와 예외 응답
└─ config      Security, JPA Auditing, QueryDSL 설정
```

`config`에는 Swagger/OpenAPI의 문서 정보와 JWT 보안 스키마를 정의하는 `OpenApiConfig`도 포함한다. 실제 접근 제어는 OpenAPI 어노테이션이 아니라 `SecurityConfig`와 JWT 필터가 담당한다.

## 5. 사용자와 조직 설계

### User

`User`는 `Department`, `Position`과 LAZY 다대일 관계를 가진다.

계정 생성 시 부서와 직급이 아직 결정되지 않을 수 있으므로 두 관계는 nullable이다. 관리자는 미배정 사용자를 조회한 뒤 부서와 직급을 독립적으로 배정하거나 변경할 수 있다.

신규 사번은 `JobFamily`과 `hireDate`를 입력받아 `EmployeeNumberGenerator`가 발급한다. `EmployeeNumberSequenceRepository`는 직렬·입사 연도별 카운터를 MySQL Upsert로 원자 증가시킨다. 사번은 사용자의 영속적인 업무 식별자이므로 생성 이후 기본정보 수정 대상에서 제외한다.

역할은 V1에서 다음 세 가지로 제한한다.

* `SUPER_ADMIN`: 시스템 총괄 관리자
* `ADMIN`: 관리자
* `USER`: 일반 사용자

사용자 상태는 `ACTIVE`, `INACTIVE`, `DELETED`를 사용한다.

### Department

`Department`는 자기 자신을 부모로 참조하는 계층형 구조이다. 동일한 부서명은 서로 다른 상위 부서 아래에서 사용할 수 있다.

부서는 조직 이력과 사용자 참조를 고려해 `ACTIVE`, `INACTIVE`, `DELETED` 상태를 이용한 Soft Delete를 적용한다. 부서장 변경 시 순환 참조와 부적절한 계층 변경을 Service에서 검증한다.

### Position

`Position`은 `positionOrder`로 직급 순서를 표현한다. 직급명과 순서는 고유해야 하며, 사용자가 참조하고 있으면 삭제하지 않는다.

## 6. 전자결재 설계

### 핵심 Entity

* `Approval`: 문서와 전체 진행 상태
* `ApprovalLine`: 문서에 확정된 결재자와 처리 순서
* `ApprovalCc`: 문서 참조자
* `ApprovalHistory`: 상신·승인·반려·취소 이력

결재 템플릿이나 현재 조직 정보가 변경되더라도 이미 상신된 문서의 결재선은 변하지 않아야 한다. 따라서 실제 결재 처리는 항상 상신 시점에 생성된 `ApprovalLine`을 기준으로 수행한다.

### 상태

문서 상태:

```text
DRAFT → IN_PROGRESS → APPROVED
                    ├→ REJECTED
                    └→ CANCELED
```

결재선 상태:

```text
WAITING → PENDING → APPROVED 또는 REJECTED
```

상신 시 첫 결재자는 `PENDING`, 이후 결재자는 `WAITING`으로 생성한다.

### 현재 구현된 상신 흐름

1. 작성자의 수정 가능한 `DRAFT` 문서를 조회한다.
2. 제목과 본문, 결재자 및 참조자 요청을 검증한다.
3. 활성 사용자와 결재 가능 역할을 검증한다.
4. 중복 참여자, 작성자 자기 지정, 결재자·참조자 중복을 차단한다.
5. `ApprovalSubmissionFactory`가 결재선, 참조자, 상신 이력을 생성한다.
6. 문서를 `IN_PROGRESS`로 전환하고 `submittedAt`을 기록한다.
7. 하나의 트랜잭션에서 관련 데이터를 저장한다.

`ApprovalService`는 흐름과 트랜잭션을 조정하고, `ApprovalSubmissionValidator`는 제출 규칙을 검증하며, `ApprovalSubmissionFactory`는 관련 Entity 생성을 담당한다.

## 7. 조회 전략

단순 CRUD는 Spring Data JPA와 메서드 이름 기반 쿼리를 사용한다. 결재함처럼 조건과 조인이 복잡한 조회는 QueryDSL Custom Repository를 사용한다.

현재 QueryDSL에는 다음 조회 기반이 준비되어 있다.

* 작성자의 임시저장 문서
* 작성자가 상신한 문서
* 사용자의 결재 대기 문서
* 사용자의 결재 처리 문서
* 사용자의 참조 문서
* 사용자가 참여한 문서의 최근 결재 활동

작성자 연관관계는 Join으로 함께 조회하여 목록 DTO 프로젝션 중 발생할 수 있는 N+1 문제를 줄인다. 목록 조회는 서버 페이지네이션을 적용하고 `createdAt`과 PK를 함께 정렬 기준으로 사용해 같은 시각의 데이터도 순서가 흔들리지 않게 한다.

### 최근 활동 조회

대시보드 최근 활동은 별도 활동 테이블을 만들지 않고 이미 상태 변경의 감사 기록으로 저장되는 `ApprovalHistory`를 읽기 모델로 사용한다.

```text
GET /api/approvals/activities?page=0&size=5
    ↓
로그인 사용자 조회
    ↓
ApprovalHistory + Approval + Actor 조회
    ↓
작성자 OR 결재선 참여자 OR 참조자 권한 확인
    ↓
createdAt DESC, approvalHistoryId DESC 페이지 응답
```

결재선과 참조자 테이블을 바깥 쿼리에 직접 조인하면 한 문서에 여러 참여자가 있을 때 이력 행이 중복될 수 있다. 이를 피하기 위해 참여 여부는 상관 `EXISTS` 서브쿼리로 검사한다. `SUPER_ADMIN`도 개인 대시보드에서는 조직 전체가 아니라 본인이 참여한 활동만 조회하며, 조직 전체 감사 이력은 별도 관리자 기능으로 분리한다.

현재 이력 구조는 승인 행동을 기록하지만 중간 승인과 최종 승인을 구분하는 별도 필드는 없다. V1 화면은 두 경우 모두 `결재를 승인했습니다`로 표현한다. 향후 최종 승인 이벤트를 별도로 표시해야 한다면 이력의 의미를 바꾸기보다 별도 action type 또는 이벤트 메타데이터 추가를 검토한다.

### 통합 검색

통합 검색은 하나의 키워드를 다음 조건에 적용한다.

* 결재 문서 제목
* 작성자의 이름, 로그인 ID, 사번
* 결재자의 이름, 로그인 ID, 사번
* 참조자의 이름, 로그인 ID, 사번

```text
GET /api/approvals/search?keyword={검색어}&page=0&size=20
```

일반 사용자는 작성자·결재자·참조자로 참여한 문서와 본인의 임시저장 문서만 검색한다. `SUPER_ADMIN`은 운영 권한에 따라 전체 문서를 검색한다. 검색 결과 링크를 알고 있더라도 상세 조회 API가 권한을 다시 검증하므로, 검색 UI가 인가를 대신하지 않는다.

결재자와 참조자는 다대일 목록이므로 바깥 쿼리에 직접 조인하지 않고 `EXISTS` 서브쿼리로 검색한다. 이 방식은 한 문서에 검색어와 일치하는 사용자가 여러 명 있어도 검색 결과가 중복되는 것을 방지한다. 검색 페이지 번호는 프론트 URL 쿼리 파라미터에 저장하여 뒤로가기와 검색 결과 링크 공유를 지원한다.

## 8. 인증 및 인가 흐름

1. 사용자가 로그인 ID와 비밀번호를 전송한다.
2. `AuthService`가 사용자를 조회하고 BCrypt로 비밀번호를 검증한다.
3. 성공하면 Access Token과 Refresh Token을 발급한다.
4. Access Token은 응답 본문으로, Refresh Token은 HttpOnly Cookie로 전달한다.
5. 이후 요청은 `Authorization: Bearer {accessToken}` 헤더를 사용한다.
6. `JwtAuthenticationFilter`가 토큰을 검증하고 인증 객체를 `SecurityContext`에 등록한다.
7. `/api/admin/**`는 `ADMIN` 또는 `SUPER_ADMIN`만 접근할 수 있다.
8. 그 외 API는 기본적으로 인증이 필요하다.

Access Token이 만료되면 프론트엔드의 Axios 응답 인터셉터가 Refresh Token Cookie로 재발급 API를 호출한다. 동시에 여러 요청이 실패해도 하나의 재발급 요청을 공유하고, 성공하면 새 Access Token으로 원래 요청을 다시 실행한다.

서버 세션은 사용하지 않으며 Security 설정은 `STATELESS`이다. JWT Secret, DB 계정, 허용 Origin 같은 환경별 값은 로컬 설정으로 분리하고 저장소에 커밋하지 않는다.

## 9. 공통 정책

* 생성·수정 시간은 JPA Auditing 기반 공통 Entity로 관리한다.
* 예외는 `BusinessException`, `ErrorCode`, `GlobalExceptionHandler`를 통해 공통 형식으로 반환한다.
* DB 제약조건과 애플리케이션 검증을 함께 사용한다.
* 삭제 정책은 데이터 생명주기에 따라 Soft Delete, RESTRICT, SET NULL, CASCADE를 구분한다.
* 기능 완성 전 조기 최적화를 피하고, 실행 쿼리와 사용 패턴을 확인한 뒤 인덱스를 결정한다.

## 10. 주요 설계 결정

* MSA 대신 단일 Spring Boot 애플리케이션을 사용한다.
* Kafka와 Redis는 기술 시연 목적으로 억지로 도입하지 않는다.
* V2 실시간 알림은 단방향 요구에 맞춰 SSE를 우선 검토한다.
* V2 협업 채팅은 양방향 통신이 필요한 경우 WebSocket을 사용한다.
* JPA는 CRUD 생산성에, QueryDSL은 복잡한 조회의 타입 안정성에 사용한다.
* 세부 권한은 V1의 세 역할로 시작하고 실제 요구가 생기면 V2에서 사용자별 권한으로 확장한다.

## 11. 상세 문서

* [클래스 구조와 요청 흐름](class-structure.md)
* [DB 구조와 설계 결정](database-design.md)
* [JWT 인증과 전자결재 흐름](approval-security-flow.md)
* [개발 로드맵](roadmap.md)
* [주요 트러블슈팅](troubleshooting.md)
* [AI 협업 방식](ai-collaboration.md)
