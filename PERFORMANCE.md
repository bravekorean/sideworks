# SideWorks 성능 개선 기록

2026-11-25 로컬 환경에서 검증했다(휴가 첨부 잠금 사례는 2026-11-28). 대시보드 전용 API와 화면 처리는 제품 코드에 반영했다. 결재 처리함 복합 인덱스와 최근 활동 조회 방식은 격리 MySQL에서 검증한 후보다.

| 사례 | 결과 | 반영 상태 |
| --- | --- | --- |
| 대시보드 전용 조회 | 결재 조회 SQL 10 → 6개, 프로필 포함 대시보드 로더 요청 6 → 2개 | 제품 코드 반영 |
| 결재 처리함 인덱스 | 첫 페이지 목록 SQL p50 19.110 → 0.815ms, 결재선 접근 34,000 → 20행 | 격리 DB 검증 |
| 최근 결재 활동 | 결재·참조 편중 사용자 p50 23.134 → 3.773ms, 참여 없음 185.465 → 5.209ms | 격리 DB 검증 |
| 휴가 첨부 저장과 사용자 잠금 | 첨부 저장 지연 300ms에서 같은 직원의 잔액 조회 서비스 호출 p50 323~328 → 5ms | 제품 코드 반영(2026-11-28) |

## 대시보드에서 쓰지 않는 조회 제거

기존 화면은 프로필과 결재 API 5개를 호출했다. 진행 중·임시저장·참조 카드에서는 목록을 버리고 건수만 썼고, 최근 활동에서는 목록만 쓰고 전체 건수를 버렸다. 진행 중·임시저장·참조에는 count만 실행하고, 최근 활동은 상위 5개만 조회하도록 변경했다. 프로필은 별도로 요청하고, 결재 데이터는 `GET /api/approvals/dashboard` 한 번으로 받는다. 일반 결재함에서는 목록과 전체 건수를 모두 사용하므로 기존 Page 계약을 유지했다.

- **결재 조회 ORM SQL 10 → 6개(40% 감소).** 전후를 같은 MySQL 인스턴스에서 교차 비교했다. 새 API 전체는 사용자 조회 1개를 포함해 ORM SQL 7개를 실행했다. [Repository 비교 테스트](src/test/java/com/example/sideworks/DashboardQueryComparisonIntegrationTest.java) · [API 통합 테스트](src/test/java/com/example/sideworks/DashboardApiIntegrationTest.java)
- **대시보드 로더의 데이터 요청 6 → 2개(66.7% 감소).** 프로필 1건을 포함한다. 앱 공통 영역의 프로필·권한·알림·SSE 요청은 별도다. [대시보드 코드](frontend/src/pages/DashboardPage.jsx)
- **결재 데이터 HTTP p50 19.147 → 12.862ms(32.8% 감소).** 문서 1천 건·이력 5천 건, 일반 분포 사용자, 방식별 50회에서 기존 API 5개를 병렬 호출한 경우와 전용 API 1개를 비교했다. 재실행에서는 17.687 → 11.944ms였다. 편중 사용자에서는 개선 폭이 작았고 재실행 p95가 20.626 → 21.331ms로 늘었다. 측정 범위는 HTTP와 JSON 처리까지다. [HTTP 비교 및 브라우저 검증 서버](src/test/java/com/example/sideworks/DashboardBrowserSmokeTest.java)

응답 검증에서는 세 역할의 본인 범위와 건수·목록·정렬을 확인했다. 브라우저에서는 로그인 후 정상 목록과 실제 0건을 확인하고, 결재 조회에 503을 주입해 오류가 표시되는지도 점검했다. 조회 실패를 빈 목록으로 오인하던 문제를 수정해 실패 시 건수는 `—`로 표시하고, 프로필이 성공하면 사용자 이름을 유지한다. 오류 해제 후 기존 목록으로 돌아오는 것도 확인했다.

## 결재 처리함의 최신순 정렬에 맞춘 인덱스

처리함은 결재자별로 `APPROVED`와 `REJECTED` 결재선을 찾고, `processed_at DESC, approval_line_id DESC`로 정렬한다. 결재자 단일 인덱스로는 조건에 맞는 이력을 정렬하는 작업이 남는다. 상태를 앞에 둔 인덱스와 처리 시각을 앞에 둔 인덱스를 비교했다.

```sql
-- 상태 필터 우선
(approver_id, approval_status, processed_at DESC, approval_line_id DESC, approval_id)

-- 최신순 정렬 우선
(approver_id, processed_at DESC, approval_line_id DESC, approval_status, approval_id)
```

문서 6만 건·결재선 4만 건, 처리 문서 17,000건인 사용자, 페이지 크기 20이다. 각 조건은 워밍업 8회 후 30회씩 측정했다. 표의 값은 목록과 count 각각의 JDBC 실행 및 결과 매핑 p50이다.

| 조회 | 기존 | 상태 우선 | 처리 시각 우선 |
| --- | ---: | ---: | ---: |
| 첫 페이지 | 19.110ms | 6.267ms | **0.815ms** |
| 깊은 페이지, OFFSET 12,740 | 28.423ms | 16.712ms | **13.301ms** |
| 전체 건수 | 28.828ms | **15.956ms** | 17.642ms |

첫 페이지에서 기존 계획은 결재선 **34,000행을 읽고 일치한 17,000행을 정렬**했다. 상태 우선 인덱스는 결재선 접근을 17,000행으로 줄였지만 두 상태 범위를 합쳐 정렬했다. 시각 우선 인덱스는 **최신순으로 20행을 읽고 종료**해 첫 페이지 p50이 95.7% 감소했다. 힌트 없는 실행계획도 편중 사용자에서 시각 우선 인덱스를 선택했다. 깊은 페이지의 시각 우선 계획은 12,760행을 읽었다.

시간 측정은 기본 조회를 대상으로 했고, 상태 필터에 대해서는 결과 동등성을 확인했다. 전체 건수 조회와 쓰기 작업도 처리함의 비용에 포함되므로 실제 DB의 인덱스·필터 분포·쓰기 비용을 확인한 뒤 채택할 후보다. [측정 및 EXPLAIN ANALYZE 재현 테스트](src/test/java/com/example/sideworks/ProcessedApprovalIndexIntegrationTest.java)

## 최근 활동의 사용자 분포에 따른 SQL 회귀

기존 목록 SQL은 작성자와 결재선·참조자 접근 조건을 `OR + EXISTS`로 판정한다. 참여 문서가 적은 경우에도 이력 10만 행을 지나갈 수 있었다. 역할별 인덱스로 접근 가능한 문서 ID를 모으는 `UNION`은 참여 문서가 적을 때 유리했다. 반면 결재·참조 편중 사용자는 문서 ID **13,334개**를 먼저 모으고 이력 **66,670건**을 조인했다. 최초 비교에서 그 사용자의 p50은 기존 24.269ms에서 141.747ms로 늘었다.

표시용 제목과 처리자 조회를 최신 5건으로 미루는 후속 변경은 처리자 조회를 **66,670회 → 5회**로 줄였다. 같은 후속 실행에서 초기 `UNION` 123.246ms → 조인을 미룬 `UNION` 43.444ms로 줄었지만, 접근 문서 집합을 만드는 비용이 남아 기존 22.004ms보다 느렸다.

마지막 비교에서는 이력에 `(created_at DESC, approval_history_id DESC)` 인덱스를 추가했다. 인덱스만 사용하는 경우와, 이 인덱스로 **최신 100건부터 확인하고 접근 가능한 이력이 5건 미만일 때 전체 `UNION`으로 조회하는 경우**를 비교했다. 두 조회는 동일 스냅샷을 사용했다. 문서 2만 건·이력 10만 건, 조건별 50회 교차 측정 결과다.

| 사용자 조건 | 기존 p50 | 이력 정렬 인덱스 p50 | 최신 100건 탐색 p50 | 추가 조회 |
| --- | ---: | ---: | ---: | ---: |
| 작성 편중 | 138.453ms | 142.516ms | **4.434ms** | 0/50 |
| 결재·참조 편중 | 23.134ms | **3.566ms** | 3.773ms | 0/50 |
| 일반 분포 | 22.698ms | **3.526ms** | 3.731ms | 0/50 |
| 참여 없음 | 185.465ms | 221.336ms | **5.209ms** | 50/50 |

참여 문서가 없는 사용자는 인덱스만 추가했을 때 10만 행을 끝까지 읽었다. 최신 100건 탐색은 인덱스에서 100행을 읽었고, 추가 `UNION`에서 접근 문서 집합이 비어 이력 테이블을 다시 조회하지 않았다. 반환 컬럼·정렬, 임시저장 문서 권한과 100건 경계를 넘어가는 경우도 검증했다. 합성 데이터에서는 동일한 생성 시각을 사용해 이력 ID로 순서를 결정한다. 실제 적용 전에는 최근 활동의 시점 분포를 바꿔 추가 조회 비율과 인덱스 쓰기 비용을 확인해야 한다. [SQL 비교 및 재현 테스트](src/test/java/com/example/sideworks/ApprovalActivityPlanIntegrationTest.java)

## 휴가 첨부 저장을 사용자 잠금 밖으로

휴가·취소 신청은 트랜잭션의 첫 쿼리에서 신청자의 사용자 행을 `SELECT … FOR UPDATE`로 잠그고, 그 안에서 진행 중 신청량을 빼 가용 일수를 판단한다. 변경 전에는 첨부 파일 저장도 이 잠금 안에서 실행되어, 저장 시간만큼 같은 직원의 잔액 조회(`ensureBalance`도 같은 행을 잠근다)와 다른 신청이 기다렸다.

휴가·취소 첨부는 `unbound/{UUID}` 키로 잠금 전에 저장하고 잠금 안에서는 첨부 행만 기록한다. 일반 결재 첨부의 `{approvalId}/{UUID}` 키와 동작은 그대로다. 저장 단계는 DB를 읽지 않으며, 신청 트랜잭션의 첫 SQL이 `usertbl … for update`인지 테스트로 확인한다.

테스트 전용 FileStorage에 300ms 지연을 넣고, 같은 직원의 신청 A(PDF 1개)가 저장하는 동안 실행한 잔액 조회 서비스 호출(`getMyBalance()`)의 시작부터 반환까지를 10회 측정했다. 이 시간에는 트랜잭션 시작·종료, 사용자 행 잠금 획득, 잔액 조회가 함께 들어가며 순수한 행 잠금 대기만 분리한 값은 아니다. 변경 전 코드(`ce9bcc5`)와 변경 후 main(`cb8749d`)을 같은 측정 테스트로 비교했다. 두 커밋 모두 커넥션별 5초 잠금 대기 한도와 409 처리가 이미 적용돼 있어, 이 비교는 첨부 저장 위치 변경만의 효과다.

| 실행 | p50 | 최대 |
| --- | ---: | ---: |
| 변경 전, 로컬 PC | 328ms | 334ms |
| 변경 전, 별도 Linux 환경 2회 | 323ms · 328ms | 327ms · 331ms |
| 변경 후, 로컬 PC | **5ms** | 67ms |
| 변경 후, 별도 Linux 환경 2회 | **5ms** · **5ms** | 69ms · 74ms |

별도 Linux 환경 변경 후 2회의 원시 표본은 `[69, 6, 7, 6, 5, 5, 5, 5, 5, 5]`, `[74, 5, 5, 6, 6, 5, 6, 5, 5, 4]`로 최대값은 첫 표본이었다. 로컬 PC 실행의 원시 표본은 남기지 않았다. 저장하는 동안 트랜잭션이 이미 열려 있어 DB 커넥션 1개를 점유하는 비용은 남는다. 신청이 업무 규칙 위반·잠금 대기 초과·일부 파일 저장 실패로 롤백되면 미리 저장한 파일이 남지 않는 것을 별도 통합 테스트로 확인했다. [측정 테스트](src/test/java/com/example/sideworks/LeaveAttachmentLockHoldBenchmarkTest.java) · [파일 정리 테스트](src/test/java/com/example/sideworks/LeaveAttachmentPrestoreIntegrationTest.java)

## 재현 방법

Java 21과 Docker가 필요하다. 아래 테스트는 합성 데이터 전용 MySQL 8.0.44 컨테이너에서 실행하며 환경 변수를 켠 경우에만 동작한다. 보고서와 측정 표본은 `build/reports/` 하위에 생성된다.

```powershell
$env:SIDEWORKS_DASHBOARD_BENCHMARK='true'
./gradlew.bat test --rerun --tests '*DashboardQueryComparisonIntegrationTest' --tests '*DashboardApiIntegrationTest'

$env:SIDEWORKS_PROCESSED_INDEX_BENCHMARK='true'
./gradlew.bat test --rerun --tests '*ProcessedApprovalIndexIntegrationTest'

$env:SIDEWORKS_APPROVAL_PLAN_BENCHMARK='true'
./gradlew.bat test --rerun --tests '*ApprovalActivityPlanIntegrationTest'

$env:SIDEWORKS_LOCK_HOLD_BENCHMARK='true'
./gradlew.bat test --rerun --tests '*LeaveAttachmentLockHoldBenchmarkTest'
```

휴가 첨부 측정은 `build/reports/`에 별도 파일을 만들지 않는다. Gradle 테스트 보고서(`build/reports/tests/test/index.html`)의 `LeaveAttachmentLockHoldBenchmarkTest` 표준 출력에서 `LOCK_HOLD_BENCHMARK` 줄의 표본과 p50·최대값을 확인한다.

HTTP 비교와 브라우저 확인은 `frontend`에서 `npm run build`를 먼저 실행하고, 저장소 루트에서 `$env:SIDEWORKS_DASHBOARD_BROWSER_SMOKE='true'`를 설정한 뒤 `./gradlew.bat test --rerun --tests '*DashboardBrowserSmokeTest'`로 서버를 시작한다. `build/reports/dashboard-browser-smoke/server.txt`에 표시된 루프백 주소에서 브라우저 확인을 진행한다. 합성 계정 안내는 테스트 코드에 있으며, 검증이 끝나면 같은 보고서 폴더에 `stop` 파일을 만들어 서버를 종료한다.
