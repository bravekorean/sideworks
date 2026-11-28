package com.example.sideworks;

import com.example.sideworks.approval.attachment.config.AttachmentProperties;
import com.example.sideworks.approval.attachment.storage.FileStorage;
import com.example.sideworks.approval.attachment.storage.LocalFileStorage;
import com.example.sideworks.leave.dto.LeaveRequestDocumentInput;
import com.example.sideworks.leave.entity.AnnualLeaveBalance;
import com.example.sideworks.leave.entity.LeavePeriod;
import com.example.sideworks.leave.repository.AnnualLeaveBalanceRepository;
import com.example.sideworks.leave.service.AnnualLeaveBalanceService;
import com.example.sideworks.leave.service.LeaveRequestService;
import com.example.sideworks.support.MySqlTestcontainersConfig;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 측정 전용: 휴가 신청이 첨부 파일을 저장하는 동안 같은 직원의 잔액 조회가 사용자 행 잠금을 얼마나 기다리는지 잰다.
 * 파일 저장은 실제 로컬 저장소에 쓰되 {@value #STORE_DELAY_MS}ms 지연을 넣어 느린 디스크·원격 저장소를 흉내 낸다.
 * 시간 단언은 하지 않는다. SIDEWORKS_LOCK_HOLD_BENCHMARK=true일 때만 실행한다.
 */
@EnabledIfEnvironmentVariable(named = "SIDEWORKS_LOCK_HOLD_BENCHMARK", matches = "true")
@ActiveProfiles("test")
@Import({MySqlTestcontainersConfig.class, LeaveAttachmentLockHoldBenchmarkTest.DelayedStorageConfig.class})
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.cors.allowed-origins=http://localhost:5173",
        "app.attachment.storage-path=build/tmp/lock-hold-benchmark-attachments",
        "logging.level.root=WARN"
})
class LeaveAttachmentLockHoldBenchmarkTest {

    static final long STORE_DELAY_MS = 300;
    private static final int SAMPLES = 10;
    private static volatile CountDownLatch storing = new CountDownLatch(1);

    @TestConfiguration(proxyBeanMethods = false)
    static class DelayedStorageConfig {
        @Bean
        @Primary
        FileStorage delayedFileStorage(AttachmentProperties properties) {
            FileStorage delegate = new LocalFileStorage(properties);
            return new FileStorage() {
                @Override
                public StoredFile store(Long approvalId, MultipartFile file) {
                    slowDown();
                    return delegate.store(approvalId, file);
                }

                @Override
                public StoredFile storeUnbound(MultipartFile file) {
                    slowDown();
                    return delegate.storeUnbound(file);
                }

                private void slowDown() {
                    storing.countDown();
                    try {
                        Thread.sleep(STORE_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
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
    @Autowired AnnualLeaveBalanceService balanceService;
    @Autowired LeaveRequestService leaveRequestService;
    @Autowired Clock clock;

    @Test
    void measureBalanceLookupWaitWhileLeaveAttachmentIsStored() throws Exception {
        LocalDate leaveDate = LocalDate.now(clock).plusWeeks(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        Long approverId = transactions.execute(ignored -> {
            insertLeavePolicy(leaveDate);
            return createUser("bench-approver").getUserId();
        });

        List<Long> waits = new ArrayList<>();
        var executor = Executors.newSingleThreadExecutor();
        try {
            for (int i = 0; i < SAMPLES; i++) {
                String owner = "bench-owner-" + i;
                transactions.executeWithoutResult(ignored -> balances.save(
                        AnnualLeaveBalance.grant(createUser(owner), leaveDate.getYear(), new BigDecimal("15.0"))));
                var input = new LeaveRequestDocumentInput(leaveDate, leaveDate, LeavePeriod.FULL, "잠금 보유 측정", approverId);
                storing = new CountDownLatch(1);

                var submission = executor.submit(() -> leaveRequestService.submit(owner, input, List.of(pdf())));
                assertThat(storing.await(10, SECONDS)).as("첨부 저장 시작").isTrue();
                long start = System.nanoTime();
                balanceService.getMyBalance(owner, leaveDate.getYear());
                waits.add(Duration.ofNanos(System.nanoTime() - start).toMillis());
                submission.get(10, SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        List<Long> sorted = waits.stream().sorted().toList();
        System.out.printf("LOCK_HOLD_BENCHMARK store_delay=%dms samples=%d p50=%dms max=%dms raw=%s%n",
                STORE_DELAY_MS, SAMPLES, sorted.get(SAMPLES / 2), sorted.getLast(), waits);
    }

    private static MultipartFile pdf() {
        return new MockMultipartFile("files", "evidence.pdf", "application/pdf", "%PDF-1.4 benchmark".getBytes());
    }

    private User createUser(String loginId) {
        return users.save(User.create(loginId, "test-only", loginId, null, null,
                loginId, null, LocalDate.of(2025, 1, 1),
                null, null, UserRole.USER, UserStatus.ACTIVE));
    }

    private void insertLeavePolicy(LocalDate leaveDate) {
        jdbc.update("""
                insert into approval_document_typetbl
                (type_code, type_name, behavior_type, is_system, active, sort_order, version, created_at, updated_at)
                values ('LEAVE_REQUEST', '휴가 신청', 'LEAVE_REQUEST', 1, 1, 1, 0, now(), now())
                """);
        jdbc.update("""
                insert into work_policytbl
                (policy_name, work_start_time, work_end_time, break_start_time, break_end_time,
                 late_grace_minutes, effective_from, status, created_at, updated_at)
                values ('잠금 보유 측정', '09:00:00', '18:00:00', '12:00:00', '13:00:00', 0, ?, 'ACTIVE', now(), now())
                """, leaveDate.minusDays(1));
        Long policyId = jdbc.queryForObject(
                "select work_policy_id from work_policytbl where policy_name = '잠금 보유 측정'", Long.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            jdbc.update("""
                    insert into work_policy_daytbl (work_policy_id, day_of_week, is_working, created_at, updated_at)
                    values (?, ?, ?, now(), now())
                    """, policyId, day.name(), day.getValue() <= 5);
        }
    }
}
