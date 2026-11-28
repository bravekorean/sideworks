package com.example.sideworks;

import com.example.sideworks.approval.dto.ApprovalDecisionRequest;
import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalDocumentType;
import com.example.sideworks.approval.entity.ApprovalLine;
import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.approval.repository.ApprovalDocumentTypeRepository;
import com.example.sideworks.approval.repository.ApprovalLineRepository;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.approval.service.ApprovalService;
import com.example.sideworks.leave.dto.LeaveRequestDocumentInput;
import com.example.sideworks.leave.entity.AnnualLeaveBalance;
import com.example.sideworks.leave.entity.LeavePeriod;
import com.example.sideworks.leave.repository.AnnualLeaveBalanceRepository;
import com.example.sideworks.leave.service.LeaveRequestService;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.notification.service.ApprovalNotificationWorkflow;
import com.example.sideworks.support.MySqlTestcontainersConfig;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

@ActiveProfiles("test")
@Import(MySqlTestcontainersConfig.class)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.cors.allowed-origins=http://localhost:5173",
        "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=2"
})
@Timeout(30)
class LockContentionIntegrationTest {

    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired UserRepository users;
    @Autowired ApprovalDocumentTypeRepository documentTypes;
    @Autowired ApprovalRepository approvals;
    @Autowired ApprovalLineRepository lines;
    @Autowired ApprovalService approvalService;
    @Autowired AnnualLeaveBalanceRepository balances;
    @Autowired LeaveRequestService leaveRequestService;
    @Autowired Clock clock;
    @MockitoSpyBean ApprovalNotificationWorkflow notificationWorkflow;

    /** 선행 요청이 잠금을 쥔 채 머무는 시간. 세션 잠금 대기(2초)보다 충분히 짧아야 한다. */
    private static final long LEADER_HOLD_MS = 700;

    @Test
    void waitingApprovalSeesNotInProgressAfterLeaderCommits() throws Exception {
        Long approvalId = transactions.execute(ignored -> createSubmittedApproval(
                createUser("commit-writer"), createUser("commit-approver"), createGeneralType("COMMIT_GENERAL")));
        holdLeaderInsideApproval(false);

        FollowerResult follower = leaderThenFollower(
                () -> approve(approvalId, "commit-approver"),
                () -> approve(approvalId, "commit-approver"));

        assertThat(follower.leaderFailure()).isNull();
        assertThat(follower.failure()).isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.APPROVAL_NOT_IN_PROGRESS));
        assertFollowerWaitedForLeader(follower);
        assertThat(approvals.findById(approvalId).orElseThrow().getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(count("select count(*) from approval_historytbl where approval_id = ? and action_type = 'APPROVED'",
                approvalId)).isEqualTo(1);
        assertThat(count("select count(*) from notificationtbl where approval_id = ? and event_key = 'FINAL_APPROVED'",
                approvalId)).isEqualTo(1);
    }

    @Test
    void waitingApprovalSucceedsAfterLeaderRollsBack() throws Exception {
        Long approvalId = transactions.execute(ignored -> createSubmittedApproval(
                createUser("rollback-writer"), createUser("rollback-approver"), createGeneralType("ROLLBACK_GENERAL")));
        holdLeaderInsideApproval(true);

        FollowerResult follower = leaderThenFollower(
                () -> approve(approvalId, "rollback-approver"),
                () -> approve(approvalId, "rollback-approver"));

        assertThat(follower.leaderFailure()).isInstanceOf(LeaderRollback.class);
        assertThat(follower.failure()).isNull();
        assertFollowerWaitedForLeader(follower);
        assertThat(approvals.findById(approvalId).orElseThrow().getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(count("select count(*) from approval_historytbl where approval_id = ? and action_type = 'APPROVED'",
                approvalId)).isEqualTo(1);
        assertThat(count("select count(*) from notificationtbl where approval_id = ?", approvalId)).isEqualTo(1);
    }

    @Test
    void waitingLeaveRequestIsRejectedAfterLeaderReservesLastDay() throws Exception {
        LeaveFixture fixture = createLeaveFixture("commit-leave-owner", "commit-leave-approver");
        holdLeaderInsideLeaveSubmission(false);

        FollowerResult follower = leaderThenFollower(
                () -> leaveRequestService.submit("commit-leave-owner", fixture.input(), List.of()),
                () -> leaveRequestService.submit("commit-leave-owner", fixture.input(), List.of()));

        assertThat(follower.leaderFailure()).isNull();
        assertThat(follower.failure()).isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.LEAVE_INSUFFICIENT_BALANCE));
        assertFollowerWaitedForLeader(follower);
        assertThat(count("select count(*) from leave_requesttbl lr join approvaltbl a on a.approval_id = lr.approval_id "
                + "where a.writer_id = ?", fixture.ownerId())).isEqualTo(1);
        assertThat(balances.findByUser_UserIdAndLeaveYear(fixture.ownerId(), fixture.leaveDate().getYear())
                .orElseThrow().getRemainingDays()).isEqualByComparingTo("1.0");
    }

    @Test
    void waitingLeaveRequestSucceedsAfterLeaderRollsBack() throws Exception {
        LeaveFixture fixture = createLeaveFixture("rollback-leave-owner", "rollback-leave-approver");
        holdLeaderInsideLeaveSubmission(true);

        FollowerResult follower = leaderThenFollower(
                () -> leaveRequestService.submit("rollback-leave-owner", fixture.input(), List.of()),
                () -> leaveRequestService.submit("rollback-leave-owner", fixture.input(), List.of()));

        assertThat(follower.leaderFailure()).isInstanceOf(LeaderRollback.class);
        assertThat(follower.failure()).isNull();
        assertFollowerWaitedForLeader(follower);
        assertThat(count("select count(*) from approvaltbl where writer_id = ?", fixture.ownerId())).isEqualTo(1);
        assertThat(count("select count(*) from leave_requesttbl lr join approvaltbl a on a.approval_id = lr.approval_id "
                + "where a.writer_id = ?", fixture.ownerId())).isEqualTo(1);
    }

    @Test
    void sameApprovalTimesOutWithoutChangingDecisionState() throws Exception {
        Long approvalId = transactions.execute(ignored -> {
            User writer = createUser("contended-writer");
            User approver = createUser("contended-approver");
            return createSubmittedApproval(writer, approver, createGeneralType("CONTENDED_GENERAL"));
        });

        assertThat(jdbc.queryForObject("select @@session.innodb_lock_wait_timeout", Integer.class)).isEqualTo(2);
        whileHoldingLock(
                () -> approvals.findByIdForUpdate(approvalId).orElseThrow(),
                () -> assertLockTimeout("same approval", () -> approve(approvalId, "contended-approver")));

        assertThat(approvals.findById(approvalId).orElseThrow().getApprovalStatus())
                .isEqualTo(ApprovalStatus.IN_PROGRESS);
        assertThat(jdbc.queryForObject("select approval_status from approval_linetbl where approval_id = ?",
                String.class, approvalId)).isEqualTo("PENDING");
        assertThat(count("select count(*) from approval_historytbl where approval_id = ?", approvalId)).isZero();
        assertThat(count("select count(*) from notificationtbl where approval_id = ?", approvalId)).isZero();
    }

    @Test
    void differentApprovalCommitsWhileFirstDocumentRemainsLocked() throws Exception {
        List<Long> ids = transactions.execute(ignored -> {
            User writer = createUser("independent-writer");
            User approver = createUser("independent-approver");
            ApprovalDocumentType type = createGeneralType("INDEPENDENT_GENERAL");
            return List.of(createSubmittedApproval(writer, approver, type),
                    createSubmittedApproval(writer, approver, type));
        });
        Long lockedId = ids.getFirst();
        Long independentId = ids.getLast();

        whileHoldingLock(
                () -> approvals.findByIdForUpdate(lockedId).orElseThrow(),
                () -> {
                    approve(independentId, "independent-approver");
                    assertThat(approvals.findById(independentId).orElseThrow().getApprovalStatus())
                            .isEqualTo(ApprovalStatus.APPROVED);
                });

        assertThat(approvals.findById(lockedId).orElseThrow().getApprovalStatus())
                .isEqualTo(ApprovalStatus.IN_PROGRESS);
        assertThat(count("select count(*) from approval_historytbl where approval_id = ?", independentId)).isEqualTo(1);
        assertThat(count("select count(*) from notificationtbl where approval_id = ?", independentId)).isEqualTo(1);
    }

    @Test
    void positiveJpaLockTimeoutHintDoesNotOverrideMysqlSessionTimeout() throws Exception {
        Long approvalId = transactions.execute(ignored -> {
            User writer = createUser("hint-writer");
            User approver = createUser("hint-approver");
            return createSubmittedApproval(writer, approver, createGeneralType("LOCK_HINT_GENERAL"));
        });

        // 잠금만 조회해 1초 JPA 힌트와 2초 DB 설정의 효과를 분리한다.
        whileHoldingLock(
                () -> approvals.findByIdForUpdate(approvalId).orElseThrow(),
                () -> {
                    long start = System.nanoTime();
                    Throwable failure = catchThrowable(() -> transactions.executeWithoutResult(ignored ->
                            entityManager.createQuery("select a from Approval a where a.approvalId = :id", Approval.class)
                                    .setParameter("id", approvalId)
                                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                                    .setHint("jakarta.persistence.lock.timeout", 1_000)
                                    .getSingleResult()));
                    assertMysqlWaitTimeout("1-second JPA hint", failure, start);
                });
    }

    @Test
    void sameLeaveOwnerTimesOutButDifferentOwnerCanSubmit() throws Exception {
        LocalDate leaveDate = LocalDate.now(clock).plusWeeks(2)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        var leaveUsers = transactions.execute(ignored -> {
            User locked = createUser("locked-leave-owner");
            User independent = createUser("independent-leave-owner");
            User approver = createUser("lock-test-leave-approver");
            for (User owner : List.of(locked, independent)) {
                balances.save(AnnualLeaveBalance.grant(owner, leaveDate.getYear(), new BigDecimal("1.0")));
            }
            insertLeavePolicy(leaveDate);
            return new LeaveUsers(locked.getUserId(), independent.getUserId(), approver.getUserId());
        });
        var input = new LeaveRequestDocumentInput(
                leaveDate, leaveDate, LeavePeriod.FULL, "잠금 대기 검증", leaveUsers.approverId());

        whileHoldingLock(
                () -> users.lockLeaveOwner("locked-leave-owner").orElseThrow(),
                () -> {
                    assertLockTimeout("same leave owner",
                            () -> leaveRequestService.submit("locked-leave-owner", input, List.of()));
                    Long approvalId = leaveRequestService.submit("independent-leave-owner", input, List.of());
                    assertThat(count("select count(*) from approvaltbl where approval_id = ? and writer_id = ?",
                            approvalId, leaveUsers.independentId())).isEqualTo(1);
                    assertThat(count("select count(*) from leave_requesttbl where approval_id = ?", approvalId))
                            .isEqualTo(1);
                });

        assertThat(count("select count(*) from approvaltbl where writer_id = ?", leaveUsers.lockedId())).isZero();
        for (Long ownerId : List.of(leaveUsers.lockedId(), leaveUsers.independentId())) {
            assertThat(balances.findByUser_UserIdAndLeaveYear(ownerId, leaveDate.getYear())
                    .orElseThrow().getRemainingDays()).isEqualByComparingTo("1.0");
        }
    }

    private static final ThreadLocal<Boolean> LEADER = ThreadLocal.withInitial(() -> false);
    private volatile CountDownLatch leaderReached;

    /** 승인 경로에서 문서 잠금을 얻은 뒤, 커밋 직전(알림 기록)에 선행 스레드만 멈춘다. */
    private void holdLeaderInsideApproval(boolean rollback) {
        doAnswer(invocation -> {
            holdIfLeader(rollback);
            return invocation.callRealMethod();
        }).when(spiedWorkflow()).onApproved(any(), anyInt());
    }

    /** 휴가 신청 경로에서 사용자 잠금을 얻은 뒤, 커밋 직전(상신 알림)에 선행 스레드만 멈춘다. */
    private void holdLeaderInsideLeaveSubmission(boolean rollback) {
        doAnswer(invocation -> {
            holdIfLeader(rollback);
            return invocation.callRealMethod();
        }).when(spiedWorkflow()).onSubmitted(any(), any(), any());
    }

    /** 트랜잭션 프록시를 거치지 않고 안쪽 Mockito spy에 직접 스텁한다(MANDATORY 전파 검사 회피). */
    private ApprovalNotificationWorkflow spiedWorkflow() {
        return AopTestUtils.getUltimateTargetObject(notificationWorkflow);
    }

    private void holdIfLeader(boolean rollback) throws InterruptedException {
        if (!LEADER.get()) return;
        leaderReached.countDown();
        Thread.sleep(LEADER_HOLD_MS);
        if (rollback) throw new LeaderRollback();
    }

    /**
     * 선행 요청이 잠금을 쥔 지점에 도달한 뒤 후속 요청을 보낸다.
     * 후속 요청은 선행 트랜잭션이 커밋 또는 롤백할 때까지 행 잠금에서 기다린다.
     */
    private FollowerResult leaderThenFollower(Runnable leaderAction, Runnable followerAction) throws Exception {
        leaderReached = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Throwable> leaderTask = () -> {
                LEADER.set(true);
                try {
                    leaderAction.run();
                    return null;
                } catch (Throwable error) {
                    return error;
                } finally {
                    LEADER.remove();
                }
            };
            Future<Throwable> leader = executor.submit(leaderTask);
            await(leaderReached, 10);

            Callable<FollowerOutcome> followerTask = () -> {
                long start = System.nanoTime();
                Throwable failure = catchThrowable(followerAction::run);
                return new FollowerOutcome(failure, Duration.ofNanos(System.nanoTime() - start).toMillis());
            };
            Future<FollowerOutcome> follower = executor.submit(followerTask);

            // 선행이 아직 잠금을 쥐고 있는 시점에 후속 요청이 끝나지 않았는지 확인한다.
            Thread.sleep(LEADER_HOLD_MS / 2);
            boolean leaderStillHolding = !leader.isDone();
            boolean followerFinishedWhileHeld = follower.isDone();

            FollowerOutcome outcome = follower.get(10, SECONDS);
            Throwable leaderFailure = leader.get(10, SECONDS);
            System.out.printf("follower waited %d ms (leader hold %d ms, leader=%s, follower=%s)%n", outcome.waitedMs(),
                    LEADER_HOLD_MS, describe(leaderFailure), describe(outcome.failure()));
            return new FollowerResult(leaderFailure, outcome.failure(), outcome.waitedMs(),
                    leaderStillHolding, followerFinishedWhileHeld);
        } finally {
            executor.shutdownNow();
        }
    }

    private static String describe(Throwable error) {
        if (error == null) return "success";
        if (error instanceof BusinessException business) return business.getErrorCode().name();
        return error.getClass().getSimpleName();
    }

    /**
     * 후속 요청이 실제로 행 잠금을 기다렸는지 확인한다.
     * 선행이 잠금을 쥔 동안 후속은 끝나지 않아야 하고, 보유 시간만큼 기다린 뒤 대기 시간 초과(2초) 전에 결과를 받아야 한다.
     */
    private static void assertFollowerWaitedForLeader(FollowerResult follower) {
        assertThat(follower.leaderStillHolding()).as("확인 시점에 선행 트랜잭션이 잠금을 보유 중").isTrue();
        assertThat(follower.followerFinishedWhileHeld()).as("선행이 잠금을 쥔 동안 후속 요청이 끝나면 안 된다").isFalse();
        assertThat(follower.waitedMs()).as("후속 요청 대기 시간(ms)").isBetween(LEADER_HOLD_MS - 50, 1_900L);
    }

    private LeaveFixture createLeaveFixture(String ownerLogin, String approverLogin) {
        LocalDate leaveDate = LocalDate.now(clock).plusWeeks(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        return transactions.execute(ignored -> {
            User owner = createUser(ownerLogin);
            User approver = createUser(approverLogin);
            balances.save(AnnualLeaveBalance.grant(owner, leaveDate.getYear(), new BigDecimal("1.0")));
            insertLeavePolicy(leaveDate);
            var input = new LeaveRequestDocumentInput(
                    leaveDate, leaveDate, LeavePeriod.FULL, "커밋 후 재판단 검증", approver.getUserId());
            return new LeaveFixture(owner.getUserId(), leaveDate, input);
        });
    }

    private record FollowerOutcome(Throwable failure, long waitedMs) {}

    private record FollowerResult(Throwable leaderFailure, Throwable failure, long waitedMs,
                                  boolean leaderStillHolding, boolean followerFinishedWhileHeld) {}

    private record LeaveFixture(Long ownerId, LocalDate leaveDate, LeaveRequestDocumentInput input) {}

    private static final class LeaderRollback extends RuntimeException {
        LeaderRollback() {
            super("선행 트랜잭션 롤백 유도", null, false, false);
        }
    }

    private void whileHoldingLock(Runnable acquire, Runnable contender) throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var holder = executor.submit(() -> transactions.executeWithoutResult(ignored -> {
                acquire.run();
                locked.countDown();
                await(release, 20);
            }));
            try {
                await(locked, 10);
                contender.run();
                assertThat(holder.isDone()).as("잠금 보유 트랜잭션이 아직 종료되지 않아야 한다").isFalse();
            } finally {
                release.countDown();
                holder.get(10, SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch, long seconds) {
        try {
            assertThat(latch.await(seconds, SECONDS)).as("트랜잭션 신호 대기").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("트랜잭션 신호 대기 중 인터럽트", e);
        }
    }

    private static void assertLockTimeout(String scenario, Runnable action) {
        long start = System.nanoTime();
        Throwable failure = catchThrowable(action::run);
        assertThat(failure).isInstanceOf(PessimisticLockingFailureException.class);
        assertMysqlWaitTimeout(scenario, failure, start);
    }

    private static void assertMysqlWaitTimeout(String scenario, Throwable failure, long start) {
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertThat(failure).hasRootCauseInstanceOf(SQLException.class);
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertThat(((SQLException) cause).getErrorCode()).isEqualTo(1205);
        assertThat(elapsedMs).as("%s의 잠금 대기 시간(ms)", scenario).isBetween(1_750L, 8_000L);
        System.out.printf("%s: %s, MySQL error 1205, elapsed=%d ms%n",
                scenario, failure.getClass().getSimpleName(), elapsedMs);
    }

    private void approve(Long approvalId, String approverLogin) {
        approvalService.approveApproval(approvalId, approverLogin, new ApprovalDecisionRequest());
    }

    private User createUser(String loginId) {
        return users.save(User.create(loginId, "test-only", loginId, null, null,
                loginId, null, LocalDate.of(2025, 1, 1),
                null, null, UserRole.USER, UserStatus.ACTIVE));
    }

    private ApprovalDocumentType createGeneralType(String code) {
        return documentTypes.save(ApprovalDocumentType.createGeneral(code, "잠금 경합 검증", null, null, 1));
    }

    private Long createSubmittedApproval(User writer, User approver, ApprovalDocumentType type) {
        Approval approval = approvals.save(Approval.createDraft(writer, "잠금 검증", "본문", type));
        approval.submit(LocalDateTime.now(clock));
        lines.save(ApprovalLine.create(approval, approver, 1, ApprovalLineStatus.PENDING));
        return approval.getApprovalId();
    }

    private void insertLeavePolicy(LocalDate leaveDate) {
        // 같은 컨텍스트의 여러 테스트가 공유하므로 문서 종류와 근무정책은 한 번만 만든다.
        if (count("select count(*) from approval_document_typetbl where type_code = 'LEAVE_REQUEST'") > 0) return;
        jdbc.update("""
                insert into approval_document_typetbl
                (type_code, type_name, behavior_type, is_system, active, sort_order, version, created_at, updated_at)
                values ('LEAVE_REQUEST', '휴가 신청', 'LEAVE_REQUEST', 1, 1, 1, 0, now(), now())
                """);
        jdbc.update("""
                insert into work_policytbl
                (policy_name, work_start_time, work_end_time, break_start_time, break_end_time,
                 late_grace_minutes, effective_from, status, created_at, updated_at)
                values ('잠금 경합 검증', '09:00:00', '18:00:00', '12:00:00', '13:00:00',
                        0, ?, 'ACTIVE', now(), now())
                """, leaveDate.minusDays(1));
        Long policyId = jdbc.queryForObject(
                "select work_policy_id from work_policytbl where policy_name = '잠금 경합 검증'", Long.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            jdbc.update("""
                    insert into work_policy_daytbl
                    (work_policy_id, day_of_week, is_working, created_at, updated_at)
                    values (?, ?, ?, now(), now())
                    """, policyId, day.name(), day.getValue() <= 5);
        }
    }

    private int count(String sql, Object... ids) {
        return jdbc.queryForObject(sql, Integer.class, ids);
    }

    private record LeaveUsers(Long lockedId, Long independentId, Long approverId) {}
}

