# AGENTS.md

# 에이전트 지침

## 1. 언어

모든 답변은 한국어로 작성한다.

---

## 2. 학습 중심 지원

사용자의 CS, 인프라, 데이터베이스, 백엔드 개발 실력 향상에 도움이 되는 방향으로 답변한다.

단순히 정답만 제공하지 않는다.

항상 다음 내용을 함께 설명한다.

* 왜 그렇게 설계하는지
* 대안은 무엇인지
* 각 방식의 장단점
* 실무에서는 어떻게 사용하는지
* 성능 및 유지보수 관점

---

## 3. 파일 수정 정책

어떠한 경우에도 사용자 승인 없이 프로젝트를 변경하지 않는다.

다음 작업은 반드시 사용자 승인 후 진행한다.

* 파일 생성
* 파일 수정
* 파일 삭제
* 파일 이동
* 파일 이름 변경
* 패키지 구조 변경
* 리팩토링
* 설정 파일 변경
* 의존성 추가
* DB 스키마 변경
* application.yml 수정
* application.properties 수정
* build.gradle 수정
* pom.xml 수정

작업 전 변경 예정 내용을 먼저 설명한다.

---

## 4. 보안 정책

다음 정보가 포함된 파일 또는 코드가 발견될 경우 즉시 사용자에게 알린다.

* API Key
* Access Token
* Secret Key
* JWT Secret
* Database 계정 정보
* AWS 자격 증명
* OAuth Client Secret
* .env 파일
* application.yml/application.properties 내 민감 정보

사용자 승인 없이 외부 전송, 출력, 저장, 복사하지 않는다.

보안상 위험하다고 판단되는 작업은 즉시 중단하고 사용자에게 알린다.

---

## 5. 개발 방식

이 프로젝트의 목표는 기능 완성과 개발 역량 향상을 동시에 달성하는 것이다.

따라서 다음 원칙을 따른다.

1. 설계 설명
2. 예제 제시
3. 개발자 직접 구현
4. 코드 리뷰
5. 개선안 제시

전체 기능을 한 번에 자동 생성하지 않는다.

---

## 6. 자동 생성 제한

사용자가 명시적으로 요청하지 않는 한 다음 항목을 한 번에 생성하지 않는다.

* Controller
* Service
* Repository
* Entity
* DTO
* Config
* 전체 CRUD

항상 단계적으로 진행한다.

예시

설계 설명
→ 예제 코드
→ 사용자 구현
→ 코드 리뷰

---

## 7. 불확실한 경우

요구사항이 명확하지 않거나 여러 해석이 가능한 경우에는 작업을 진행하기 전에 사용자에게 확인한다.

추측으로 구현하지 않는다.

---

## 8. 코드 생성 원칙

코드를 생성할 때는 다음을 우선 고려한다.

* Spring Boot Best Practice
* JPA Best Practice
* 유지보수성
* 확장성
* 보안
* 성능

동작만 하는 코드보다 실무에서 사용할 수 있는 코드를 우선한다.

---

## 9. 코드 리뷰 원칙

코드 리뷰 시 다음 항목을 확인한다.

* 네이밍
* 객체지향 설계
* Spring 구조
* JPA 연관관계
* N+1 문제
* Lazy Loading 문제
* 트랜잭션 범위
* 예외 처리
* 보안 문제
* 인덱스 활용
* SQL 성능
* 유지보수성

문제가 발견되면 이유와 개선 방법을 함께 설명한다.

---

## 10. DB 설계 원칙

DB 설계 시 다음 우선순위를 따른다.

1. 유지보수성
2. 확장성
3. 성능

개인 프로젝트 MVP 단계에서는 과도한 최적화보다 이해하기 쉬운 구조를 우선한다.

---

## 11. 오버엔지니어링 방지

현재 프로젝트는 개인 프로젝트 및 포트폴리오 목적의 그룹웨어 MVP이다.

따라서 다음은 지양한다.

* 과도한 MSA
* 불필요한 디자인 패턴
* 과도한 추상화
* 지나치게 복잡한 계층 구조
* 조기 최적화

필요한 시점에 점진적으로 확장한다.

---

# 프로젝트 정보

## 프로젝트 개요

본 프로젝트는 학습 및 포트폴리오 목적으로 개발하는 그룹웨어 시스템이다.

목표는 7월 이전 MVP 완성이다.

---

## 주요 기능

* 사용자 관리
* 부서 관리
* 직급 관리
* 전자결재
* 결재선 관리
* 참조(CC) 관리
* 결재 이력 관리
* JWT 로그인
* 조직도
* Redis 적용 예정
* Kafka 적용 예정

---

# 기술 스택

## Backend

* Java 21
* Spring Boot 3.5.x
* Spring Security
* JWT
* Spring Data JPA
* MySQL 8.0

## Frontend

* React 19
* Node.js

## 개발 환경

* IntelliJ IDEA Community Edition
* VS Code
* Codex CLI

---

# 현재 DB 설계

## 사용자

### usertbl

* user_id
* login_id
* user_pass
* user_name
* user_email
* user_phone
* employee_no
* department_id
* position_id
* user_role
* created_at
* updated_at
* status

---

## 부서

### departmenttbl

* department_id
* parent_department_id
* department_name
* manager_user_id
* created_at
* updated_at

계층형 부서 구조 지원

예시

개발본부

* 백엔드팀
* 프론트엔드팀
* QA팀

---

## 직급

### positiontbl

* position_id
* position_name
* position_order
* created_at
* updated_at

예시

* 사원
* 대리
* 과장
* 차장
* 부장

---

# 전자결재

## approvaltbl

전자결재 문서

* approval_id
* writer_id
* title
* content
* approval_status
* current_step
* created_at
* updated_at

---

## approval_linetbl

실제 결재자 목록

* approval_line_id
* approval_id
* approver_id
* approval_step
* approval_status
* approval_comment
* approved_at

---

## approval_historytbl

결재 이력

* approval_history_id
* approval_id
* approver_id
* action_step
* action_type
* comment
* created_at

---

## approval_cctbl

참조자

* approval_cc_id
* approval_id
* user_id

---

# 향후 결재선 기능

전자결재는 두 가지 방식을 지원한다.

## 수동 결재선

사용자가 직접 결재자를 선택한다.

예시

사원
→ 대리
→ 팀장

---

## 자동 결재선

부서 및 직급 정보를 기반으로 결재선을 자동 생성한다.

예시

작성자
→ 팀장
→ 부서장
→ 본부장

또는

* 휴가 신청 결재선
* 구매 요청 결재선

등 템플릿 기반 결재선을 지원할 수 있다.

---

## 향후 추가 예정 테이블

### approval_template

결재선 템플릿

### approval_template_line

결재선 템플릿 상세

---

실제 결재 처리는 항상 approval_linetbl 기준으로 수행한다.

템플릿은 결재선 생성을 위한 용도로만 사용한다.

---

# 네이밍 규칙

DB 컬럼명은 snake_case를 사용한다.

예시

* user_id
* department_id
* approval_id

대소문자가 섞인 네이밍은 사용하지 않는다.

---

# PK 정책

모든 PK는 다음 규칙을 사용한다.

BIGINT AUTO_INCREMENT

---

# 공통 컬럼

대부분의 테이블은 다음 컬럼을 가진다.

* created_at
* updated_at

---

# 삭제 정책

가능한 경우 Soft Delete를 고려한다.

예시

status

* ACTIVE
* INACTIVE
* DELETED

---

# 개발 순서

1. DB 설계 확정
2. 화면 및 메뉴 구조 설계
3. 화면 기준 DB 재검토
4. JPA Entity 작성
5. JWT 로그인 구현
6. 사용자 관리 구현
7. 전자결재 구현
8. 조직도 구현
9. 배포
10. Redis 적용
11. Kafka 적용

---

# Codex 작업 방식

Codex는 다음 순서로 작업한다.

1. 설계 설명
2. 예제 코드 제시
3. 개발자 직접 구현
4. 코드 리뷰
5. 리팩토링 및 개선안 제시

전체 기능을 한 번에 생성하기보다 단계적으로 진행한다.

---

# 리뷰 시 확인 사항

* Spring Boot 구조 적절성
* JPA 연관관계
* N+1 문제
* Lazy Loading 문제
* 트랜잭션 범위
* 인덱스 필요 여부
* 성능 문제
* 예외 처리
* 보안 문제
* 유지보수성

단순히 동작하는 코드보다 실무에서 유지보수 가능한 코드를 우선한다.