# SideWorks

SideWorks는 전자결재와 조직 관리를 중심으로 개발하는 그룹웨어 MVP입니다.
Spring Boot와 React를 기반으로 설계·구현하며, 개발 과정에서의 기술 선택과 문제 해결을 학습하고 취업 포트폴리오로 정리하는 것을 목표로 합니다.

현재 V1의 핵심 기능과 프론트엔드 API 연동, JWT 자동 재발급 검증 및 Swagger/OpenAPI 문서화를 완료했으며 배포를 준비하고 있습니다.

## 주요 기능

- JWT 기반 로그인과 권한 관리
- 사용자·부서·직급 관리
- 전자결재 작성, 상신, 승인, 반려, 취소
- 결재선·참조자·처리 이력 관리
- 마이페이지
- React/MUI 기반 관리 화면과 대시보드
- Swagger/OpenAPI 기반 API 명세

## V1 진행 상태

- 완료: 인증·인가, 사용자·부서·직급 관리, 마이페이지, 전자결재, 통합 검색, 대시보드
- 완료: React 화면과 백엔드 API 연동, Access Token 자동 재발급 검증
- 완료: 역할별 접근 검증 및 Swagger/OpenAPI 문서화
- 진행 예정: 운영 환경 구성, 배포, 배포 후 핵심 업무 흐름 검증

## 기술 스택

- Backend: Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, QueryDSL, springdoc-openapi
- Database: MySQL 8.0
- Frontend: React 19, MUI
- Authentication: JWT Access Token, Refresh Token Cookie

## V2 통계 계획

통계 기능은 개인 순위나 성과 평가보다 조직 운영과 결재 흐름의 개선을 목적으로 합니다.

| 역할 | 조회 범위 |
| --- | --- |
| `USER` | 본인이 소속된 부서의 집계 통계 |
| `ADMIN` | 담당 부서의 상세 통계와 장기 미처리 현황 |
| `SUPER_ADMIN` | 조직 전체, 부서 비교, 사용자별 결재 처리 현황 |

일반 사용자를 위한 개인 성과 통계 화면은 만들지 않습니다. `SUPER_ADMIN`이 조회하는 사용자별 현황은 작성자 관점과 결재자 관점을 분리하며, 직원 순위 대신 업무 처리 부하와 지연 원인을 파악하는 운영 지표로 제공합니다.

사용자별 통계 권한은 화면에서 메뉴를 숨기는 것에 그치지 않고 서버에서도 검증합니다. 민감한 통계 조회는 감사 로그를 남기는 방안을 함께 검토합니다.

## 문서

- 개발 일정과 완료 상태: `docs/roadmap.md`
- 아키텍처와 설계 기록: `docs/architecture.md`
- Codex 협업 및 프로젝트 원칙: `AGENTS.md`
