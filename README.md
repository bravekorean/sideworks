# SideWorks

SideWorks는 전자결재와 조직 관리를 중심으로 개발하는 그룹웨어 MVP입니다.
Spring Boot와 React를 기반으로 설계·구현하며, 개발 과정에서의 기술 선택과 문제 해결을 학습하고 취업 포트폴리오로 정리하는 것을 목표로 합니다.

V1의 핵심 기능, 프론트엔드 API 연동, JWT 자동 재발급, Swagger/OpenAPI 문서화와 AWS 배포 검증을 완료했습니다. 현재는 V1을 기준선으로 보존하고 V2 고도화를 준비하고 있습니다.

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
- 완료: AWS EC2·RDS·Nginx 기반 배포와 로그인부터 결재 완료까지의 운영 환경 스모크 테스트
- 완료: 작성자·결재자·참조자 관점의 문서 상태와 실제 RDS 데이터 반영 확인

배포는 일회성 검증 환경에서 수행했으며, 검증과 화면 기록 후 과금 방지를 위해 클라우드 자원을 정리했습니다. 재현 가능한 구성과 트러블슈팅 과정은 별도 배포 문서에 값이 아닌 절차 중심으로 기록했습니다.

## 기술 스택

- Backend: Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, QueryDSL, springdoc-openapi
- Database: MySQL 8.0
- Frontend: React 19, MUI
- Authentication: JWT Access Token, Refresh Token Cookie

## V2 고도화 계획

2026-09-09 기준 기초 설계를 합의했습니다. 상세 정책은 [V2 기초 설계와 결정 기록](docs/v2-foundation-design.md)을 기준으로 하며 아래 항목은 구현 예정입니다.

- 요청 ID와 Application/Audit 파일 로그
- 부서 트리·구성원·상세 화면의 조직도
- 고정 근무시간 기반 출퇴근과 HR 근태 정정
- 연차 부여·사용·복원 원장과 승인 대기량 기반 잔액 검증
- 휴가 신청·취소 전자결재, 한 단계 자동 결재선, 기간제 대리 결재
- 부서 범위에 따른 승인 휴가·반차 캘린더
- DB 알림 저장과 커밋 후 SSE 전달
- 역할별 조직 운영 통계와 보안·성능 점검

계정 역할은 USER / HR_MANAGER / SUPER_ADMIN으로 설계하고 부서장 관계와 문서별 결재 권한을 별도로 판단합니다. 현재 코드의 ADMIN 이관은 아직 진행하지 않았습니다.

작은 업무 흐름을 백엔드·화면·테스트·문서까지 완성하는 Vertical Slice 방식으로 개발합니다. 9월 구현을 목표로 하고 11~12월 종합 검증을 계획하되 기능별 테스트는 개발과 함께 수행합니다.

급여·수당·교대·유연근무·출근 위치 제한은 제외합니다. 전결·합의·범용 템플릿·WebSocket 채팅은 향후 후보이며, Redis·Kafka와 별도 로그 플랫폼은 필요성이 확인될 때 검토합니다.

## 문서

- [아키텍처와 설계 결정](docs/architecture.md)
- [클래스 구조와 요청 흐름](docs/class-structure.md)
- [DB 구조와 설계 결정](docs/database-design.md)
- [JWT 인증과 전자결재 흐름](docs/approval-security-flow.md)
- [개발 현황과 V2 로드맵](docs/roadmap.md)
- [AWS 배포 및 트러블슈팅](docs/deployment.md)
- [주요 트러블슈팅](docs/troubleshooting.md)
- [AI 협업 방식](docs/ai-collaboration.md)
