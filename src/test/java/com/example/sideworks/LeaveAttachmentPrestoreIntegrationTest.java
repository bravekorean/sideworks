package com.example.sideworks;

import com.example.sideworks.approval.attachment.config.AttachmentProperties;
import com.example.sideworks.approval.attachment.service.ApprovalAttachmentService;
import com.example.sideworks.approval.attachment.storage.FileStorage;
import com.example.sideworks.approval.attachment.storage.LocalFileStorage;
import com.example.sideworks.approval.service.ApprovalService;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.leave.dto.LeaveCancellationDocumentInput;
import com.example.sideworks.leave.dto.LeaveRequestDocumentInput;
import com.example.sideworks.leave.entity.AnnualLeaveBalance;
import com.example.sideworks.leave.entity.LeavePeriod;
import com.example.sideworks.leave.repository.AnnualLeaveBalanceRepository;
import com.example.sideworks.leave.service.LeaveCancellationService;
import com.example.sideworks.leave.service.LeaveRequestService;
import com.example.sideworks.support.MySqlTestcontainersConfig;
import com.example.sideworks.support.RecordingStatementInspector;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 휴가·취소 신청의 첨부 파일은 사용자 행 잠금 전에 {@code unbound/} 키로 저장되고,
 * 트랜잭션이 커밋되지 않으면(업무 규칙 위반, 잠금 대기 초과, 저장 도중 실패) 남김없이 지워지는지 확인한다.
 */
@ActiveProfiles("test")
@Import({MySqlTestcontainersConfig.class, LeaveAttachmentPrestoreIntegrationTest.FailingStorageConfig.class})
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.cors.allowed-origins=http://localhost:5173",
        "app.attachment.storage-path=" + LeaveAttachmentPrestoreIntegrationTest.STORAGE_ROOT,
        "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=1",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.example.sideworks.support.RecordingStatementInspector"
})
@Timeout(30)
class LeaveAttachmentPrestoreIntegrationTest {

    static final String STORAGE_ROOT = "build/tmp/leave-prestore-attachments";
    private static final Path UNBOUND_DIR = Path.of(STORAGE_ROOT).toAbsolutePath().normalize().resolve("unbound");
    private static final byte[] PDF = "%PDF-1.4 prestore".getBytes();

    /** 0이면 실패하지 않는다. n이면 n번째 storeUnbound 호출에서 저장 실패를 낸다. */
    private static final AtomicInteger failOnUnboundCall = new AtomicInteger();
    private static final AtomicInteger unboundCalls = new AtomicInteger();

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingStorageConfig {
        @Bean
        @Primary
        FileStorage failingFileStorage(AttachmentProperties properties) {
            FileStorage delegate = new LocalFileStorage(properties);
            return new FileStorage() {
                @Override
                public StoredFile store(Long approvalId, MultipartFile file) {
                    return delegate.store(approvalId, file);
                }

                @Override
                public StoredFile storeUnbound(MultipartFile file) {
                    if (unboundCalls.incrementAndGet() == failOnUnboundCall.get()) {
                        throw new BusinessException(ErrorCode.ATTACHMENT_STORAGE_FAILED);
                    }
                    return delegate.storeUnbound(file);
                }

                @Override
                public Resource load(String storageKey) {
                    return delegate.load(storageKey);
                }

                @Override
                public void delete(String storageKey) {
                    delegate.delete(storageKey);
                }
            };
        }
    }

    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AnnualLeaveBalanceRepository balances;
    @Autowired LeaveRequestService leaveRequestService;
    @Autowired LeaveCancellationService leaveCancellationService;
    @Autowired ApprovalService approvalService;
    @Autowired ApprovalAttachmentService attachmentService;
    @Autowired Clock clock;

    private LocalDate leaveDate;

    @BeforeEach
    void setUp() throws IOException {
        failOnUnboundCall.set(0);
        unboundCalls.set(0);
        deleteRecursively(UNBOUND_DIR);
        leaveDate = LocalDate.now(clock).plusWeeks(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        transactions.executeWithoutResult(ignored -> ensureLeavePolicy());
    }

    @Test
    void storesFileBeforeUserLockAndLinksItToTheNewDocument() throws Exception {
        Long approverId = createOwnerAndApprover("prestore-owner", "prestore-approver", "1.0");

        RecordingStatementInspector.start();
        Long approvalId;
        try {
            approvalId = leaveRequestService.submit("prestore-owner", input(approverId), List.of(pdf("evidence.pdf")));
        } finally {
            List<String> statements = RecordingStatementInspector.stop();
            assertThat(statements).isNotEmpty();
            assertThat(statements.getFirst()).as("트랜잭션의 첫 SQL은 사용자 행 잠금이어야 한다")
                    .containsIgnoringCase("usertbl").containsIgnoringCase("for update");
        }

        assertThat(storedFiles()).hasSize(1);
        var row = jdbc.queryForMap(
                "select approval_attachment_id, storage_key from approval_attachmenttbl where approval_id = ?", approvalId);
        String storageKey = (String) row.get("storage_key");
        assertThat(storageKey).startsWith("unbound/");
        assertThat(UNBOUND_DIR.getParent().resolve(storageKey)).isEqualTo(storedFiles().getFirst());

        var download = attachmentService.download(approvalId,
                ((Number) row.get("approval_attachment_id")).longValue(), "prestore-owner");
        try (InputStream content = download.resource().getInputStream()) {
            assertThat(content.readAllBytes()).isEqualTo(PDF);
        }
        assertThat(download.originalFileName()).isEqualTo("evidence.pdf");
    }

    @Test
    void businessRuleFailureAfterLockRemovesPrestoredFile() throws IOException {
        Long approverId = createOwnerAndApprover("short-owner", "short-approver", "0.5");

        Throwable failure = catchThrowable(() ->
                leaveRequestService.submit("short-owner", input(approverId), List.of(pdf("evidence.pdf"))));

        assertThat(failure).isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.LEAVE_INSUFFICIENT_BALANCE));
        assertThat(unboundCalls).as("잠금 전에 저장을 시도했어야 한다").hasValue(1);
        assertThat(storedFiles()).isEmpty();
        assertThat(count("select count(*) from approvaltbl a join usertbl u on u.user_id = a.writer_id "
                + "where u.login_id = 'short-owner'")).isZero();
    }

    @Test
    void lockWaitTimeoutRemovesPrestoredFile() throws Exception {
        Long approverId = createOwnerAndApprover("busy-owner", "busy-approver", "1.0");
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        Throwable failure;
        try {
            var holder = executor.submit(() -> transactions.executeWithoutResult(ignored -> {
                users.lockLeaveOwner("busy-owner").orElseThrow();
                locked.countDown();
                try {
                    release.await(20, SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, SECONDS)).isTrue();
            failure = catchThrowable(() ->
                    leaveRequestService.submit("busy-owner", input(approverId), List.of(pdf("evidence.pdf"))));
            release.countDown();
            holder.get(10, SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        assertThat(failure).isInstanceOf(PessimisticLockingFailureException.class);
        assertThat(unboundCalls).hasValue(1);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void failureWhileStoringSecondFileRemovesTheFirst() throws IOException {
        Long approverId = createOwnerAndApprover("partial-owner", "partial-approver", "1.0");
        failOnUnboundCall.set(2);

        Throwable failure = catchThrowable(() -> leaveRequestService.submit("partial-owner", input(approverId),
                List.of(pdf("first.pdf"), pdf("second.pdf"))));

        assertThat(failure).isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ATTACHMENT_STORAGE_FAILED));
        assertThat(unboundCalls).hasValue(2);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void cancellationStoresBeforeLockAndRemovesFileWhenRejected() throws Exception {
        Long approverId = createOwnerAndApprover("cancel-owner", "cancel-approver", "1.0");
        Long leaveApprovalId = leaveRequestService.submit("cancel-owner", input(approverId), List.of());
        approvalService.approveApproval(leaveApprovalId, "cancel-approver", null);
        Long leaveRequestId = jdbc.queryForObject(
                "select leave_request_id from leave_requesttbl where approval_id = ?", Long.class, leaveApprovalId);
        var cancellation = new LeaveCancellationDocumentInput(leaveRequestId, "일정 변경", approverId);

        RecordingStatementInspector.start();
        Long cancellationApprovalId;
        try {
            cancellationApprovalId = leaveCancellationService.submit(
                    "cancel-owner", cancellation, List.of(pdf("cancel.pdf")));
        } finally {
            List<String> statements = RecordingStatementInspector.stop();
            assertThat(statements.getFirst()).containsIgnoringCase("usertbl").containsIgnoringCase("for update");
        }
        assertThat(storedFiles()).hasSize(1);
        assertThat(jdbc.queryForObject("select storage_key from approval_attachmenttbl where approval_id = ?",
                String.class, cancellationApprovalId)).startsWith("unbound/");

        Throwable duplicate = catchThrowable(() ->
                leaveCancellationService.submit("cancel-owner", cancellation, List.of(pdf("again.pdf"))));

        assertThat(duplicate).isInstanceOfSatisfying(BusinessException.class, error ->
                assertThat(error.getErrorCode()).isEqualTo(ErrorCode.LEAVE_CANCELLATION_PENDING));
        assertThat(storedFiles()).as("거절된 두 번째 신청의 파일만 지워지고 첫 신청 파일은 남아야 한다").hasSize(1);
    }

    private LeaveRequestDocumentInput input(Long approverId) {
        return new LeaveRequestDocumentInput(leaveDate, leaveDate, LeavePeriod.FULL, "첨부 선저장 검증", approverId);
    }

    private Long createOwnerAndApprover(String ownerLogin, String approverLogin, String days) {
        return transactions.execute(ignored -> {
            User owner = createUser(ownerLogin);
            balances.save(AnnualLeaveBalance.grant(owner, leaveDate.getYear(), new BigDecimal(days)));
            return createUser(approverLogin).getUserId();
        });
    }

    private static MultipartFile pdf(String name) {
        return new MockMultipartFile("files", name, "application/pdf", PDF);
    }

    private static List<Path> storedFiles() throws IOException {
        if (!Files.exists(UNBOUND_DIR)) return List.of();
        try (Stream<Path> files = Files.list(UNBOUND_DIR)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private User createUser(String loginId) {
        return users.save(User.create(loginId, "test-only", loginId, null, null,
                loginId, null, LocalDate.of(2025, 1, 1),
                null, null, UserRole.USER, UserStatus.ACTIVE));
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private void ensureLeavePolicy() {
        if (count("select count(*) from approval_document_typetbl where type_code = 'LEAVE_REQUEST'") > 0) return;
        for (String type : List.of("LEAVE_REQUEST", "LEAVE_CANCELLATION")) {
            jdbc.update("""
                    insert into approval_document_typetbl
                    (type_code, type_name, behavior_type, is_system, active, sort_order, version, created_at, updated_at)
                    values (?, ?, ?, 1, 1, 1, 0, now(), now())
                    """, type, type, type);
        }
        jdbc.update("""
                insert into work_policytbl
                (policy_name, work_start_time, work_end_time, break_start_time, break_end_time,
                 late_grace_minutes, effective_from, status, created_at, updated_at)
                values ('첨부 선저장 검증', '09:00:00', '18:00:00', '12:00:00', '13:00:00', 0, ?, 'ACTIVE', now(), now())
                """, leaveDate.minusDays(1));
        Long policyId = jdbc.queryForObject(
                "select work_policy_id from work_policytbl where policy_name = '첨부 선저장 검증'", Long.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            jdbc.update("""
                    insert into work_policy_daytbl (work_policy_id, day_of_week, is_working, created_at, updated_at)
                    values (?, ?, ?, now(), now())
                    """, policyId, day.name(), day.getValue() <= 5);
        }
    }
}
