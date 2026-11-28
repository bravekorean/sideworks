package com.example.sideworks;

import com.example.sideworks.approval.dto.ApprovalDecisionRequest;
import com.example.sideworks.approval.dto.ApprovalListResponse;
import com.example.sideworks.approval.dto.ApprovalTerminateRequest;
import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalDocumentType;
import com.example.sideworks.approval.entity.ApprovalLine;
import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.approval.repository.ApprovalDocumentTypeRepository;
import com.example.sideworks.approval.repository.ApprovalLineRepository;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.approval.service.ApprovalService;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.leave.dto.LeaveRequestDocumentInput;
import com.example.sideworks.leave.entity.AnnualLeaveBalance;
import com.example.sideworks.leave.entity.LeavePeriod;
import com.example.sideworks.leave.repository.AnnualLeaveBalanceRepository;
import com.example.sideworks.leave.repository.LeaveRequestRepository;
import com.example.sideworks.leave.service.LeaveRequestService;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@Import(PortfolioScenarioIntegrationTest.MySqlTestConfig.class)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.cors.allowed-origins=http://localhost:5173"
})
class PortfolioScenarioIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySqlTestConfig {
        @Bean
        @ServiceConnection
        MySQLContainer<?> mysql() {
            return new MySQLContainer<>("mysql:8.0");
        }
    }

    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired ApprovalDocumentTypeRepository documentTypes;
    @Autowired ApprovalRepository approvals;
    @Autowired ApprovalLineRepository lines;
    @Autowired ApprovalService approvalService;
    @Autowired AnnualLeaveBalanceRepository balances;
    @Autowired LeaveRequestRepository leaveRequests;
    @Autowired LeaveRequestService leaveRequestService;
    @Autowired Clock clock;

    /**
     * 같은 문서에 승인 2건을 동시에 출발시킨 뒤 최종 결과(1건 성공, 이력·알림 각 1건)만 검증한다.
     * 두 요청의 도착 순서는 보장하지 않으며, 잠금 대기 자체는 {@link LockContentionIntegrationTest}에서 검증한다.
     */
    @Test
    void concurrentApprovalProcessesOneDecisionOnly() throws Exception {
        Long approvalId = transactions.execute(ignored -> {
            User writer = createUser("approval-writer");
            User approver = createUser("approval-approver");
            ApprovalDocumentType type = documentTypes.save(
                    ApprovalDocumentType.createGeneral("CONCURRENCY_GENERAL", "동시성 검증", null, null, 1));
            Approval approval = approvals.save(Approval.createDraft(writer, "동시 승인", "본문", type));
            approval.submit(LocalDateTime.now());
            lines.save(ApprovalLine.create(approval, approver, 1, ApprovalLineStatus.PENDING));
            return approval.getApprovalId();
        });

        List<RuntimeException> results = runTogether(() ->
                approvalService.approveApproval(approvalId, "approval-approver", new ApprovalDecisionRequest()));

        assertThat(results).filteredOn(error -> error == null).hasSize(1);
        assertThat(results).filteredOn(error -> error != null).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.APPROVAL_NOT_IN_PROGRESS);
                });
        assertThat(approvals.findById(approvalId).orElseThrow().getApprovalStatus())
                .isEqualTo(ApprovalStatus.APPROVED);
        assertThat(count("select count(*) from approval_historytbl where approval_id = ? and action_type = 'APPROVED'",
                approvalId)).isEqualTo(1);
        assertThat(count("select count(*) from notificationtbl where approval_id = ? and event_key = 'FINAL_APPROVED'",
                approvalId)).isEqualTo(1);
    }

    /**
     * 잔여 1일인 직원의 1일 신청 2건을 동시에 출발시킨 뒤 최종 결과(1건 접수, 잔액 유지)만 검증한다.
     * 두 요청의 도착 순서는 보장하지 않으며, 잠금 대기 자체는 {@link LockContentionIntegrationTest}에서 검증한다.
     */
    @Test
    void concurrentLeaveRequestsCannotReserveMoreThanOneRemainingDay() throws Exception {
        LocalDate leaveDate = LocalDate.now(clock).plusWeeks(2)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        Long userId = transactions.execute(ignored -> {
            User writer = createUser("leave-writer");
            createUser("leave-approver");
            balances.save(AnnualLeaveBalance.grant(writer, leaveDate.getYear(), new BigDecimal("1.0")));
            insertLeaveDocumentType();
            insertWeekdayPolicy(leaveDate);
            return writer.getUserId();
        });
        Long approverId = users.findByLoginId("leave-approver").orElseThrow().getUserId();
        LeaveRequestDocumentInput input = new LeaveRequestDocumentInput(
                leaveDate, leaveDate, LeavePeriod.FULL, "동시 신청 검증", approverId);

        List<RuntimeException> results = runTogether(() ->
                leaveRequestService.submit("leave-writer", input, List.of()));

        assertThat(results).filteredOn(error -> error == null).hasSize(1);
        assertThat(results).filteredOn(error -> error != null).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.LEAVE_INSUFFICIENT_BALANCE);
                });
        assertThat(leaveRequests.count()).isEqualTo(1);
        assertThat(count("select count(*) from leave_request_daytbl")).isEqualTo(1);
        assertThat(balances.findByUser_UserIdAndLeaveYear(userId, leaveDate.getYear())
                .orElseThrow().getRemainingDays()).isEqualByComparingTo("1.0");
    }

    @Test
    void inactiveCurrentApproverIsVisibleToAdminAndCanBeTerminatedWithAudit() {
        Long approvalId = transactions.execute(ignored -> {
            User writer = createUser("recovery-writer");
            User approver = createUser("recovery-approver");
            User admin = createUser("recovery-admin");
            admin.changeRole(UserRole.SUPER_ADMIN);
            ApprovalDocumentType type = documentTypes.save(
                    ApprovalDocumentType.createGeneral("RECOVERY_GENERAL", "복구 검증", null, null, 2));
            Approval approval = approvals.save(Approval.createDraft(writer, "중단된 결재", "본문", type));
            approval.submit(LocalDateTime.now());
            lines.save(ApprovalLine.create(approval, approver, 1, ApprovalLineStatus.PENDING));
            return approval.getApprovalId();
        });
        transactions.executeWithoutResult(ignored ->
                users.findByLoginId("recovery-approver").orElseThrow().changeStatus(UserStatus.INACTIVE));

        var blocked = approvalService.getManagedApprovals(
                "recovery-admin", null, ApprovalStatus.IN_PROGRESS, true, PageRequest.of(0, 10));
        assertThat(blocked.getContent()).extracting(ApprovalListResponse::getApprovalId)
                .contains(approvalId);
        assertThatThrownBy(() -> approvalService.terminateApproval(
                approvalId, "recovery-writer", new ApprovalTerminateRequest("담당자 비활성화")))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.APPROVAL_TERMINATE_FORBIDDEN));

        approvalService.terminateApproval(
                approvalId, "recovery-admin", new ApprovalTerminateRequest("담당자 비활성화"));

        assertThat(approvals.findById(approvalId).orElseThrow().getApprovalStatus())
                .isEqualTo(ApprovalStatus.TERMINATED);
        assertThat(count("select count(*) from approval_historytbl where approval_id = ? and action_type = 'TERMINATED'",
                approvalId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select comment from approval_historytbl where approval_id = ? and action_type = 'TERMINATED'",
                String.class, approvalId)).isEqualTo("담당자 비활성화");
        assertThat(count("select count(*) from notificationtbl where approval_id = ? and event_key = 'TERMINATED'",
                approvalId)).isEqualTo(2);
        assertThat(approvalService.getManagedApprovals(
                "recovery-admin", null, null, true, PageRequest.of(0, 10)).getContent())
                .extracting(ApprovalListResponse::getApprovalId).doesNotContain(approvalId);
    }

    private User createUser(String loginId) {
        return users.save(User.create(loginId, "test-only", loginId, null, null,
                loginId, null, LocalDate.of(2025, 1, 1),
                null, null, UserRole.USER, UserStatus.ACTIVE));
    }

    private void insertLeaveDocumentType() {
        jdbc.update("""
                insert into approval_document_typetbl
                (type_code, type_name, behavior_type, is_system, active, sort_order, version, created_at, updated_at)
                values ('LEAVE_REQUEST', '휴가 신청', 'LEAVE_REQUEST', 1, 1, 1, 0, now(), now())
                """);
    }

    private void insertWeekdayPolicy(LocalDate leaveDate) {
        jdbc.update("""
                insert into work_policytbl
                (policy_name, work_start_time, work_end_time, break_start_time, break_end_time,
                 late_grace_minutes, effective_from, status, created_at, updated_at)
                values ('동시성 검증', '09:00:00', '18:00:00', '12:00:00', '13:00:00',
                        0, ?, 'ACTIVE', now(), now())
                """, leaveDate.minusDays(1));
        Long policyId = jdbc.queryForObject(
                "select work_policy_id from work_policytbl where policy_name = '동시성 검증'", Long.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            jdbc.update("""
                    insert into work_policy_daytbl
                    (work_policy_id, day_of_week, is_working, created_at, updated_at)
                    values (?, ?, ?, now(), now())
                    """, policyId, day.name(), day.getValue() <= 5);
        }
    }

    private int count(String sql, Long id) {
        return jdbc.queryForObject(sql, Integer.class, id);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private List<RuntimeException> runTogether(ThrowingAction action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<RuntimeException>> futures = new ArrayList<>();
            Callable<RuntimeException> task = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("두 요청이 시작 지점에 도달하지 못했습니다.");
                }
                try {
                    action.run();
                    return null;
                } catch (RuntimeException error) {
                    return error;
                }
            };
            futures.add(executor.submit(task));
            futures.add(executor.submit(task));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<RuntimeException> results = new ArrayList<>();
            results.add(futures.get(0).get(30, TimeUnit.SECONDS));
            results.add(futures.get(1).get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run();
    }
}
