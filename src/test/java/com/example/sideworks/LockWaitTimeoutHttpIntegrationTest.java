package com.example.sideworks;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalDocumentType;
import com.example.sideworks.approval.entity.ApprovalLine;
import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.approval.repository.ApprovalDocumentTypeRepository;
import com.example.sideworks.approval.repository.ApprovalLineRepository;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.support.MySqlTestcontainersConfig;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 MySQL 행 잠금 대기 시간 초과(1205)가 HTTP 계층에서 500이 아니라 409 LOCK_WAIT_TIMEOUT으로 나가는지 확인한다.
 * 인증 필터는 끄고 principal을 직접 넣는다. 인증 규칙은 컨트롤러·보안 테스트에서 따로 검증한다.
 */
@ActiveProfiles("test")
@Import(MySqlTestcontainersConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.cors.allowed-origins=http://localhost:5173",
        "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=1"
})
@Timeout(30)
class LockWaitTimeoutHttpIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired ApprovalDocumentTypeRepository documentTypes;
    @Autowired ApprovalRepository approvals;
    @Autowired ApprovalLineRepository lines;

    @Test
    void approvalLockTimeoutIsReturnedAsConflict() throws Exception {
        Long approvalId = transactions.execute(ignored -> {
            User writer = createUser("http-lock-writer");
            User approver = createUser("http-lock-approver");
            ApprovalDocumentType type = documentTypes.save(
                    ApprovalDocumentType.createGeneral("HTTP_LOCK_GENERAL", "HTTP 잠금 검증", null, null, 1));
            Approval approval = approvals.save(Approval.createDraft(writer, "잠금 응답 검증", "본문", type));
            approval.submit(LocalDateTime.now());
            lines.save(ApprovalLine.create(approval, approver, 1, ApprovalLineStatus.PENDING));
            return approval.getApprovalId();
        });

        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var holder = executor.submit(() -> transactions.executeWithoutResult(ignored -> {
                approvals.findByIdForUpdate(approvalId).orElseThrow();
                locked.countDown();
                awaitQuietly(release);
            }));
            assertThat(locked.await(10, SECONDS)).isTrue();

            mockMvc.perform(post("/api/approvals/{id}/approve", approvalId)
                            .principal(new UsernamePasswordAuthenticationToken("http-lock-approver", null)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LOCK_WAIT_TIMEOUT"));

            release.countDown();
            holder.get(10, SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        assertThat(approvals.findById(approvalId).orElseThrow().getApprovalStatus())
                .isEqualTo(ApprovalStatus.IN_PROGRESS);
        assertThat(jdbc.queryForObject("select count(*) from approval_historytbl where approval_id = ?",
                Integer.class, approvalId)).isZero();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(20, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private User createUser(String loginId) {
        return users.save(User.create(loginId, "test-only", loginId, null, null,
                loginId, null, LocalDate.of(2025, 1, 1),
                null, null, UserRole.USER, UserStatus.ACTIVE));
    }
}
