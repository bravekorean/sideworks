package com.example.sideworks.approval;

import com.example.sideworks.approval.dto.ApprovalDecisionRequest;
import com.example.sideworks.approval.dto.ApprovalDelegationResponse;
import com.example.sideworks.approval.dto.ApprovalDelegationSaveRequest;
import com.example.sideworks.approval.dto.ApprovalDelegationState;
import com.example.sideworks.approval.dto.ApprovalDetailResponse;
import com.example.sideworks.approval.dto.ApprovalHistoryResponse;
import com.example.sideworks.approval.dto.ApprovalLineResponse;
import com.example.sideworks.approval.dto.ApprovalListResponse;
import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalActionType;
import com.example.sideworks.approval.entity.ApprovalCc;
import com.example.sideworks.approval.entity.ApprovalDocumentType;
import com.example.sideworks.approval.entity.ApprovalLine;
import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.approval.repository.ApprovalCcRepository;
import com.example.sideworks.approval.repository.ApprovalDocumentTypeRepository;
import com.example.sideworks.approval.repository.ApprovalLineRepository;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.approval.service.ApprovalDelegationService;
import com.example.sideworks.approval.service.ApprovalService;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 기간제 대결을 실제 MySQL 8에서 검증한다.
 * 시각은 바꿀 수 있는 Clock(Asia/Seoul)으로 제어하고, 잠금 경합은 두 트랜잭션을 실제로 겹쳐서 만든다.
 */
@ActiveProfiles("test")
@Import(ApprovalDelegationIntegrationTest.Config.class)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.cors.allowed-origins=http://localhost:5173"
})
class ApprovalDelegationIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        // 마이그레이션 검증용 별도 스키마를 만들 수 있도록 root로 접속한다.
        @Bean
        @ServiceConnection
        MySQLContainer<?> mysql() {
            return new MySQLContainer<>("mysql:8.0").withUsername("root");
        }

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(SEOUL);
        }
    }

    static final class MutableClock extends Clock {
        private final ZoneId zone;
        private volatile Instant instant = Instant.parse("2026-12-01T00:00:00Z");

        MutableClock(ZoneId zone) {
            this.zone = zone;
        }

        void setSeoulTime(LocalDateTime seoulTime) {
            this.instant = seoulTime.atZone(zone).toInstant();
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { throw new UnsupportedOperationException(); }
        @Override public Instant instant() { return instant; }
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired UserRepository users;
    @Autowired ApprovalDocumentTypeRepository documentTypes;
    @Autowired ApprovalRepository approvals;
    @Autowired ApprovalLineRepository lines;
    @Autowired ApprovalCcRepository ccs;
    @Autowired ApprovalService approvalService;
    @Autowired ApprovalDelegationService delegationService;

    private String prefix;

    @BeforeEach
    void setUp() {
        prefix = "d" + SEQUENCE.incrementAndGet() + "-";
        clock.setSeoulTime(LocalDateTime.of(2026, 12, 1, 9, 0));
    }

    // ── 1. 시작 전부터 대기 중인 문서 ─────────────────────────────────────────

    @Test
    void 기간_시작_전부터_대기_중이던_문서를_시작일_0시부터_대결자가_처리한다() {
        User writer = createUser("writer");
        User original = createUser("original");
        User delegatee = createUser("delegatee");
        Long approvalId = submit(writer, List.of(original), List.of());
        delegate(original, delegatee, LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 4));

        // 시작 전날: 원 결재자만 처리한다.
        clock.setSeoulTime(LocalDateTime.of(2026, 12, 1, 23, 59, 59));
        assertThat(inbox(original)).containsExactly(approvalId);
        assertThat(inbox(delegatee)).isEmpty();
        assertForbidden(() -> approve(approvalId, delegatee));

        // 시작일 0시: 대결자에게 넘어가고, 원 결재자는 처리할 수 없다.
        clock.setSeoulTime(LocalDateTime.of(2026, 12, 2, 0, 0));
        assertThat(inbox(delegatee)).containsExactly(approvalId);
        assertThat(inbox(original)).isEmpty();
        assertThat(dashboardPending(delegatee)).isEqualTo(1);
        assertThat(dashboardPending(original)).isZero();
        ApprovalListResponse item = pendingItems(delegatee).getFirst();
        assertThat(item.getDelegationState()).isEqualTo(ApprovalDelegationState.DELEGATED);
        assertThat(item.getOriginalApproverName()).isEqualTo(original.getUserName());
        assertThat(detail(delegatee, approvalId).isCanDecide()).isTrue();
        assertThat(detail(original, approvalId).isCanDecide()).isFalse();
        assertForbidden(() -> approve(approvalId, original));

        approve(approvalId, delegatee);

        ApprovalLine line = lines.findByApproval_ApprovalIdAndApprovalStep(approvalId, 1).orElseThrow();
        assertThat(line.getApprovalStatus()).isEqualTo(ApprovalLineStatus.APPROVED);
        assertThat(line.getApprover().getUserId()).isEqualTo(original.getUserId());
        assertThat(line.getProcessedBy().getUserId()).isEqualTo(delegatee.getUserId());
        ApprovalHistoryResponse decision = decisionHistory(writer, approvalId);
        assertThat(decision.getActorId()).isEqualTo(delegatee.getUserId());
        assertThat(decision.getActionType()).isEqualTo(ApprovalActionType.APPROVED);
        assertThat(decision.getOnBehalfOfId()).isEqualTo(original.getUserId());
        ApprovalLineResponse lineView = detail(writer, approvalId).getApprovalLines().getFirst();
        assertThat(lineView.getApproverId()).isEqualTo(original.getUserId());
        assertThat(lineView.getProcessorId()).isEqualTo(delegatee.getUserId());
        assertThat(lineView.getDelegationState()).isEqualTo(ApprovalDelegationState.DELEGATED);
        assertThat(processed(delegatee)).containsExactly(approvalId);
        assertThat(processed(original)).isEmpty();
    }

    // ── 2. 기간 중 새로 도착한 문서 ───────────────────────────────────────────

    @Test
    void 기간_중_새로_차례가_된_단계는_대결자에게_가고_알림도_대결자가_받는다() {
        User writer = createUser("writer");
        User first = createUser("first");
        User original = createUser("original");
        User delegatee = createUser("delegatee");
        delegate(original, delegatee, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3));
        Long approvalId = submit(writer, List.of(first, original), List.of());
        assertThat(inbox(delegatee)).isEmpty();

        approve(approvalId, first);

        assertThat(inbox(delegatee)).containsExactly(approvalId);
        assertThat(inbox(original)).isEmpty();
        assertThat(notificationRecipients(approvalId, "STEP_APPROVED:1")).containsExactly(delegatee.getUserId());
        reject(approvalId, delegatee, "예산 초과");
        ApprovalHistoryResponse decision = decisionHistory(writer, approvalId);
        assertThat(decision.getActionType()).isEqualTo(ApprovalActionType.REJECTED);
        assertThat(decision.getActorId()).isEqualTo(delegatee.getUserId());
        assertThat(decision.getOnBehalfOfId()).isEqualTo(original.getUserId());
    }

    // ── 3. 만료 후 미처리 문서 ────────────────────────────────────────────────

    @Test
    void 종료일_자정이_지나면_미처리_문서가_원_결재자에게_돌아온다() {
        User writer = createUser("writer");
        User original = createUser("original");
        User delegatee = createUser("delegatee");
        Long approvalId = submit(writer, List.of(original), List.of());
        delegate(original, delegatee, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3));

        clock.setSeoulTime(LocalDateTime.of(2026, 12, 3, 23, 59, 59));
        assertThat(inbox(delegatee)).containsExactly(approvalId);

        clock.setSeoulTime(LocalDateTime.of(2026, 12, 4, 0, 0));
        assertThat(inbox(delegatee)).isEmpty();
        assertThat(inbox(original)).containsExactly(approvalId);
        assertThat(dashboardPending(original)).isEqualTo(1);
        assertNotFound(() -> approvalService.getApprovalDetail(approvalId, delegatee.getLoginId()));
        assertForbidden(() -> approve(approvalId, delegatee));

        approve(approvalId, original);
        assertThat(lines.findByApproval_ApprovalIdAndApprovalStep(approvalId, 1).orElseThrow()
                .getProcessedBy().getUserId()).isEqualTo(original.getUserId());
        assertThat(decisionHistory(writer, approvalId).getOnBehalfOfId()).isNull();
    }

    // ── 4. 취소 후 미처리 문서 ────────────────────────────────────────────────

    @Test
    void 위임을_취소하면_즉시_원_결재자에게_돌아온다() {
        User writer = createUser("writer");
        User original = createUser("original");
        User delegatee = createUser("delegatee");
        Long approvalId = submit(writer, List.of(original), List.of());
        ApprovalDelegationResponse delegation =
                delegate(original, delegatee, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3));
        assertThat(inbox(delegatee)).containsExactly(approvalId);

        delegationService.cancel(original.getLoginId(), delegation.approvalDelegationId());

        assertThat(inbox(delegatee)).isEmpty();
        assertThat(inbox(original)).containsExactly(approvalId);
        assertForbidden(() -> approve(approvalId, delegatee));
        approve(approvalId, original);
    }

    // ── 5. 잠금을 기다리는 사이 만료·취소된 요청 ─────────────────────────────

    @Test
    void 문서_잠금을_기다리는_사이_기간이_끝나면_대결자의_승인을_거부한다() throws Exception {
        User writer = createUser("writer");
        User original = createUser("original");
        User delegatee = createUser("delegatee");
        Long approvalId = submit(writer, List.of(original), List.of());
        delegate(original, delegatee, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3));
        clock.setSeoulTime(LocalDateTime.of(2026, 12, 3, 23, 59, 59));
        assertThat(inbox(delegatee)).containsExactly(approvalId);

        RuntimeException result = approveWhileLocked(approvalId, delegatee,
                () -> clock.setSeoulTime(LocalDateTime.of(2026, 12, 4, 0, 0)));

        assertThat(result).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) result).getErrorCode()).isEqualTo(ErrorCode.APPROVAL_DECISION_FORBIDDEN);
        assertThat(lines.findByApproval_ApprovalIdAndApprovalStep(approvalId, 1).orElseThrow().getApprovalStatus())
                .isEqualTo(ApprovalLineStatus.PENDING);
        assertThat(count("select count(*) from approval_historytbl where approval_id = ? and action_type = 'APPROVED'",
                approvalId)).isZero();
    }

    @Test
    void 문서_잠금을_기다리는_사이_위임이_취소되면_대결자의_승인을_거부한다() throws Exception {
        User writer = createUser("writer");
        User original = createUser("original");
        User delegatee = createUser("delegatee");
        Long approvalId = submit(writer, List.of(original), List.of());
        ApprovalDelegationResponse delegation =
                delegate(original, delegatee, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3));

        RuntimeException result = approveWhileLocked(approvalId, delegatee,
                () -> delegationService.cancel(original.getLoginId(), delegation.approvalDelegationId()));

        assertThat(result).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) result).getErrorCode()).isEqualTo(ErrorCode.APPROVAL_DECISION_FORBIDDEN);
    }

    @Test
    void 잠금을_기다린_뒤에도_기간_안이면_대결자의_승인이_성공한다() throws Exception {
        User writer = createUser("writer");
        User original = createUser("original");
        User delegatee = createUser("delegatee");
        Long approvalId = submit(writer, List.of(original), List.of());
        delegate(original, delegatee, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3));

        RuntimeException result = approveWhileLocked(approvalId, delegatee, () -> { });

        assertThat(result).isNull();
        assertThat(lines.findByApproval_ApprovalIdAndApprovalStep(approvalId, 1).orElseThrow()
                .getProcessedBy().getUserId()).isEqualTo(delegatee.getUserId());
    }

    // ── 6. 기존 참여자와 충돌하는 문서 ───────────────────────────────────────

    @Test
    void 대결자가_문서의_다른_참여자이면_원_결재자가_처리하고_대결_불가로_표시한다() {
        User writer = createUser("writer");
        User original = createUser("original");
        User delegatee = createUser("delegatee");
        User other = createUser("other");
        delegate(original, delegatee, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3));
        Long writtenByDelegatee = submit(delegatee, List.of(original), List.of());
        Long delegateeIsCc = submit(writer, List.of(original), List.of(delegatee));
        Long delegateeIsLaterStep = submit(writer, List.of(original, delegatee), List.of());
        Long normal = submit(other, List.of(original), List.of());

        assertThat(inbox(original)).containsExactlyInAnyOrder(writtenByDelegatee, delegateeIsCc, delegateeIsLaterStep);
        assertThat(inbox(delegatee)).containsExactly(normal);
        assertThat(pendingItems(original)).allSatisfy(item ->
                assertThat(item.getDelegationState()).isEqualTo(ApprovalDelegationState.DELEGATION_BLOCKED));
        ApprovalLineResponse blockedLine = detail(original, writtenByDelegatee).getApprovalLines().getFirst();
        assertThat(blockedLine.getProcessorId()).isEqualTo(original.getUserId());
        assertThat(blockedLine.getDelegationState()).isEqualTo(ApprovalDelegationState.DELEGATION_BLOCKED);
        assertForbidden(() -> approve(writtenByDelegatee, delegatee));
        approve(writtenByDelegatee, original);
    }

    @Test
    void 근태_정정_문서는_인사_권한이_없는_대결자에게_넘기지_않는다() {
        User writer = createUser("writer");
        User hrOriginal = createUser("hr", UserRole.HR_MANAGER);
        User plainDelegatee = createUser("plain");
        User hrDelegatee = createUser("hr2", UserRole.HR_MANAGER);
        ApprovalDocumentType correction = attendanceCorrectionType();
        Long toPlain = submit(writer, List.of(hrOriginal), List.of(), correction);
        delegate(hrOriginal, plainDelegatee, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1));
        assertThat(inbox(hrOriginal)).containsExactly(toPlain);
        assertThat(inbox(plainDelegatee)).isEmpty();

        clock.setSeoulTime(LocalDateTime.of(2026, 12, 2, 9, 0));
        delegate(hrOriginal, hrDelegatee, LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 2));
        assertThat(inbox(hrDelegatee)).containsExactly(toPlain);
        assertThat(inbox(hrOriginal)).isEmpty();
    }

    // ── 7. 목록·건수·상세·처리 API가 같은 기준인가 ───────────────────────────

    @Test
    void 대기함_건수_상세_처리_권한이_모든_사용자에게_같은_처리자를_가리킨다() {
        User writer = createUser("writer");
        User a = createUser("a");
        User b = createUser("b");
        User c = createUser("c");
        User d = createUser("d");
        User inactive = createUser("inactive");
        delegate(a, b, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2));
        delegate(c, inactive, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5));
        transactions.executeWithoutResult(ignored ->
                users.findById(inactive.getUserId()).orElseThrow().changeStatus(UserStatus.INACTIVE));
        List<Long> documents = new ArrayList<>(List.of(
                submit(writer, List.of(a), List.of()),
                submit(b, List.of(a), List.of()),
                submit(writer, List.of(a, b), List.of()),
                submit(writer, List.of(c), List.of()),
                submit(writer, List.of(d), List.of(b)),
                submit(writer, List.of(b), List.of())));
        Long future = submit(writer, List.of(d), List.of());
        delegate(d, a, LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 4));
        documents.add(future);
        List<User> everyone = List.of(writer, a, b, c, d, inactive);

        for (LocalDateTime at : List.of(LocalDateTime.of(2026, 12, 1, 12, 0),
                LocalDateTime.of(2026, 12, 3, 0, 0), LocalDateTime.of(2026, 12, 6, 0, 0))) {
            clock.setSeoulTime(at);
            Map<Long, Long> processorByDocument = new HashMap<>();
            for (Long id : documents) processorByDocument.put(id, resolveProcessorId(id, at.toLocalDate()));
            for (User user : everyone) {
                if (user.getUserId().equals(inactive.getUserId())) continue;
                List<Long> expected = documents.stream()
                        .filter(id -> user.getUserId().equals(processorByDocument.get(id))).toList();
                assertThat(inbox(user)).as("%s inbox at %s", user.getLoginId(), at)
                        .containsExactlyInAnyOrderElementsOf(expected);
                assertThat(dashboardPending(user)).isEqualTo(expected.size());
                for (Long id : documents) {
                    boolean canDecide = detailIfVisible(user, id);
                    assertThat(canDecide).as("%s canDecide %d at %s", user.getLoginId(), id, at)
                            .isEqualTo(expected.contains(id));
                }
            }
        }
    }

    // ── 8. 기존 방식으로 저장된 진행 중 문서 전환(마이그레이션 SQL) ────────────

    @Test
    void 마이그레이션은_원_결재자를_복원하고_실제_처리자를_남기며_엔티티와_같은_스키마를_만든다() throws Exception {
        jdbc.execute("drop database if exists legacy_delegation");
        jdbc.execute("create database legacy_delegation");
        String migration = Files.readString(Path.of("database/migrations/20261128_approval_delegation_processor.sql"));

        Map<String, Object> result = jdbc.execute((ConnectionCallback<Map<String, Object>>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("use legacy_delegation");
                for (String sql : LEGACY_SCHEMA_AND_DATA) statement.execute(sql);
                for (String sql : splitStatements(migration)) statement.execute(sql);
                Map<String, Object> rows = new HashMap<>();
                try (var rs = statement.executeQuery("select approval_line_id, approver_id, processed_by_id, "
                        + "approval_status from approval_linetbl order by approval_line_id")) {
                    List<String> lines = new ArrayList<>();
                    while (rs.next()) {
                        lines.add(rs.getLong(1) + ":" + rs.getLong(2) + ":" + rs.getObject(3) + ":" + rs.getString(4));
                    }
                    rows.put("lines", lines);
                }
                statement.execute("use test");
                return rows;
            }
        });

        // 1: 대결로 처리됨(원 2, 대결 3) / 2: 대결 배정 후 대기(원 2, 대결 3) / 3: 위임 없이 처리 / 4: 대기
        assertThat(result.get("lines")).isEqualTo(List.of(
                "1:2:3:APPROVED",
                "2:2:null:PENDING",
                "3:4:4:REJECTED",
                "4:4:null:WAITING"));
        // 이전 스키마는 관련 컬럼만 단순화했으므로, 컬럼 구성과 이번에 바뀐 컬럼의 타입만 비교한다.
        assertThat(columns("legacy_delegation").keySet()).isEqualTo(columns("test").keySet());
        assertThat(columns("legacy_delegation").get("processed_by_id")).isEqualTo(columns("test").get("processed_by_id"));
        assertThat(indexes("legacy_delegation")).containsAllEntriesOf(indexes("test").entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("idx_") || entry.getKey().startsWith("uk_"))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        assertThat(indexes("legacy_delegation")).doesNotContainKey("idx_approval_linetbl_approver_processed");
        jdbc.execute("drop database legacy_delegation");
    }

    // 20261128_approval_dashboard_indexes.sql까지 적용된 이전 스키마(관련 테이블만)와 이전 방식 데이터.
    private static final List<String> LEGACY_SCHEMA_AND_DATA = List.of(
            "create table usertbl (user_id bigint not null auto_increment primary key)",
            "create table approvaltbl (approval_id bigint not null auto_increment primary key)",
            """
            create table approval_linetbl (
                approval_line_id bigint not null auto_increment primary key,
                created_at datetime(6),
                approval_comment text,
                approval_status varchar(20) not null,
                approval_step integer not null,
                processed_at datetime(6),
                approval_id bigint not null,
                approver_id bigint not null,
                original_approver_id bigint,
                constraint uk_approval_linetbl_approval_step unique (approval_id, approval_step),
                constraint uk_approval_linetbl_approval_approver unique (approval_id, approver_id),
                constraint FKlegacyline_approval foreign key (approval_id) references approvaltbl (approval_id),
                constraint FKlegacyline_approver foreign key (approver_id) references usertbl (user_id),
                constraint FKlegacyline_original foreign key (original_approver_id) references usertbl (user_id)
            )""",
            "create index idx_approval_linetbl_approver_processed on approval_linetbl "
                    + "(approver_id, processed_at desc, approval_line_id desc, approval_status, approval_id)",
            "insert into usertbl values (1), (2), (3), (4)",
            "insert into approvaltbl values (10), (20)",
            "insert into approval_linetbl (approval_line_id, approval_id, approver_id, original_approver_id, "
                    + "approval_step, approval_status, processed_at) values "
                    + "(1, 10, 3, 2, 1, 'APPROVED', '2026-11-20 10:00:00'), "
                    + "(2, 20, 3, 2, 1, 'PENDING', null), "
                    + "(3, 10, 4, null, 2, 'REJECTED', '2026-11-21 10:00:00'), "
                    + "(4, 20, 4, null, 2, 'WAITING', null)");

    // ── helpers ──────────────────────────────────────────────────────────────

    private User createUser(String name) {
        return createUser(name, UserRole.USER);
    }

    private User createUser(String name, UserRole role) {
        String loginId = prefix + name;
        return transactions.execute(ignored -> users.save(User.create(loginId, "test-only", loginId, null, null,
                loginId, null, LocalDate.of(2025, 1, 1), null, null, role, UserStatus.ACTIVE)));
    }

    private ApprovalDocumentType generalType() {
        return transactions.execute(ignored -> documentTypes.save(ApprovalDocumentType.createGeneral(
                prefix.toUpperCase().replace("-", "_") + "GENERAL" + SEQUENCE.incrementAndGet(),
                "대결 검증", null, null, 1)));
    }

    private ApprovalDocumentType attendanceCorrectionType() {
        String code = prefix.toUpperCase().replace("-", "_") + "CORRECTION";
        jdbc.update("""
                insert into approval_document_typetbl
                (type_code, type_name, behavior_type, is_system, active, sort_order, version, created_at, updated_at)
                values (?, '근태 정정', 'ATTENDANCE_CORRECTION', 1, 1, 1, 0, now(), now())
                """, code);
        return transactions.execute(ignored -> documentTypes.findAll().stream()
                .filter(type -> type.getTypeCode().equals(code)).findFirst().orElseThrow());
    }

    private Long submit(User writer, List<User> approvers, List<User> ccUsers) {
        return submit(writer, approvers, ccUsers, generalType());
    }

    // 상신 결과(첫 단계 PENDING, 나머지 WAITING, 참조자)를 직접 만든다. 대결 판단은 상신 시점에 저장하지 않는다.
    private Long submit(User writer, List<User> approvers, List<User> ccUsers, ApprovalDocumentType type) {
        return transactions.execute(ignored -> {
            Approval approval = approvals.save(Approval.createDraft(writer, prefix + "문서", "본문", type));
            approval.submit(LocalDateTime.now(clock));
            for (int i = 0; i < approvers.size(); i++) {
                lines.save(ApprovalLine.create(approval, approvers.get(i), i + 1,
                        i == 0 ? ApprovalLineStatus.PENDING : ApprovalLineStatus.WAITING));
            }
            ccUsers.forEach(cc -> ccs.save(ApprovalCc.create(approval, cc)));
            return approval.getApprovalId();
        });
    }

    private ApprovalDelegationResponse delegate(User from, User to, LocalDate start, LocalDate end) {
        return delegationService.create(from.getLoginId(),
                new ApprovalDelegationSaveRequest(from.getUserId(), to.getUserId(), start, end));
    }

    private void approve(Long approvalId, User actor) {
        approvalService.approveApproval(approvalId, actor.getLoginId(), new ApprovalDecisionRequest());
    }

    private void reject(Long approvalId, User actor, String comment) {
        ApprovalDecisionRequest request = new ApprovalDecisionRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "comment", comment);
        approvalService.rejectApproval(approvalId, actor.getLoginId(), request);
    }

    private List<ApprovalListResponse> pendingItems(User user) {
        return approvalService.getPendingApprovals(user.getLoginId(), null, PageRequest.of(0, 50)).getContent();
    }

    private List<Long> inbox(User user) {
        return pendingItems(user).stream().map(ApprovalListResponse::getApprovalId).toList();
    }

    private List<Long> processed(User user) {
        return approvalService.getProcessedApprovals(user.getLoginId(), null, null, PageRequest.of(0, 50))
                .getContent().stream().map(ApprovalListResponse::getApprovalId).toList();
    }

    private long dashboardPending(User user) {
        return approvalService.getDashboard(user.getLoginId()).summary().pending();
    }

    private ApprovalDetailResponse detail(User user, Long approvalId) {
        return approvalService.getApprovalDetail(approvalId, user.getLoginId());
    }

    private boolean detailIfVisible(User user, Long approvalId) {
        try {
            return detail(user, approvalId).isCanDecide();
        } catch (BusinessException e) {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.APPROVAL_NOT_FOUND);
            return false;
        }
    }

    private ApprovalHistoryResponse decisionHistory(User viewer, Long approvalId) {
        return detail(viewer, approvalId).getHistories().stream()
                .filter(h -> h.getActionType() == ApprovalActionType.APPROVED
                        || h.getActionType() == ApprovalActionType.REJECTED)
                .reduce((first, second) -> second).orElseThrow();
    }

    // 처리 API와 같은 경로(잠금 읽기)로 실제 처리자를 계산한다.
    private Long resolveProcessorId(Long approvalId, LocalDate today) {
        return transactions.execute(ignored -> {
            Approval approval = approvals.findByIdForUpdate(approvalId).orElseThrow();
            ApprovalLine line = lines.findByApproval_ApprovalIdAndApprovalStep(approvalId, approval.getCurrentStep())
                    .orElseThrow();
            return delegationService.resolveProcessor(approval, line, today).getUserId();
        });
    }

    private List<Long> notificationRecipients(Long approvalId, String eventKey) {
        return jdbc.queryForList("select recipient_id from notificationtbl where approval_id = ? and event_key = ?",
                Long.class, approvalId, eventKey);
    }

    /**
     * 다른 트랜잭션이 문서 행을 잠근 상태에서 승인 요청을 보낸다. 승인 요청이 잠금 대기에 들어간 것을
     * InnoDB에서 확인한 뒤 duringWait를 실행하고 잠금을 푼다. 승인 결과(예외 또는 null)를 돌려준다.
     */
    private RuntimeException approveWhileLocked(Long approvalId, User actor, Runnable duringWait) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> holder = executor.submit(() -> transactions.executeWithoutResult(ignored -> {
                approvals.findByIdForUpdate(approvalId).orElseThrow();
                locked.countDown();
                await(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<RuntimeException> request = executor.submit(() -> {
                try {
                    approve(approvalId, actor);
                    return null;
                } catch (RuntimeException e) {
                    return e;
                }
            });
            waitForLockWait(request);
            duringWait.run();
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            return request.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    // 승인 요청의 문서 잠금 쿼리(SELECT ... FOR UPDATE)가 다른 연결에서 실행 중인지 processlist로 확인한다.
    // 잠금을 잡은 쪽은 쿼리를 끝내고 Sleep 상태이므로, 실행 중인 FOR UPDATE는 기다리는 쪽뿐이다.
    private void waitForLockWait(Future<RuntimeException> request) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (request.isDone()) {
                throw new AssertionError("승인 요청이 잠금을 기다리지 않고 끝났습니다: " + request.get(), request.get());
            }
            Integer waiting = jdbc.queryForObject("""
                    select count(*) from information_schema.processlist
                    where id <> connection_id() and command = 'Query' and info like '%approvaltbl%for update%'
                    """, Integer.class);
            if (waiting != null && waiting > 0) return;
            Thread.sleep(20);
        }
        throw new AssertionError("승인 요청이 문서 잠금 대기에 들어가지 않았습니다.");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("잠금 해제 신호 없음");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APPROVAL_DECISION_FORBIDDEN);
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APPROVAL_NOT_FOUND);
    }

    private int count(String sql, Long id) {
        Integer value = jdbc.queryForObject(sql, Integer.class, id);
        return value == null ? 0 : value;
    }

    private Map<String, String> columns(String schema) {
        Map<String, String> columns = new HashMap<>();
        jdbc.query("select column_name, column_type, is_nullable from information_schema.columns "
                        + "where table_schema = ? and table_name = 'approval_linetbl'",
                rs -> { columns.put(rs.getString(1), rs.getString(2) + " " + rs.getString(3)); }, schema);
        return columns;
    }

    private Map<String, String> indexes(String schema) {
        Map<String, String> indexes = new HashMap<>();
        jdbc.query("select index_name, group_concat(concat(column_name, if(collation = 'D', ' DESC', '')) "
                        + "order by seq_in_index) from information_schema.statistics "
                        + "where table_schema = ? and table_name = 'approval_linetbl' group by index_name",
                rs -> { indexes.put(rs.getString(1), rs.getString(2)); }, schema);
        return indexes;
    }

    // 마이그레이션 파일을 문장 단위로 나눈다. 주석 줄은 버리고, 파일 안에는 문자열 속 세미콜론이 없다.
    private static List<String> splitStatements(String script) {
        StringBuilder withoutComments = new StringBuilder();
        for (String line : script.split("\\R")) {
            if (!line.strip().startsWith("--")) withoutComments.append(line).append('\n');
        }
        List<String> statements = new ArrayList<>();
        for (String part : withoutComments.toString().split(";")) {
            if (!part.isBlank()) statements.add(part.strip());
        }
        return statements;
    }
}
