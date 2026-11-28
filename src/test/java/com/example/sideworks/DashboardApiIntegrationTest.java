package com.example.sideworks;

import com.example.sideworks.approval.attachment.storage.FileStorage;
import com.example.sideworks.approval.controller.ApprovalController;
import com.example.sideworks.approval.factory.ApprovalSubmissionFactory;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.approval.service.ApprovalDelegationService;
import com.example.sideworks.approval.service.ApprovalDocumentTypeService;
import com.example.sideworks.approval.service.ApprovalService;
import com.example.sideworks.approval.service.ApprovalTemplateService;
import com.example.sideworks.approval.validator.ApprovalSubmissionValidator;
import com.example.sideworks.attendance.service.AttendanceCorrectionService;
import com.example.sideworks.auth.jwt.JwtAuthenticationFilter;
import com.example.sideworks.auth.jwt.JwtTokenProvider;
import com.example.sideworks.common.exception.GlobalExceptionHandler;
import com.example.sideworks.config.SecurityConfig;
import com.example.sideworks.leave.service.LeaveCancellationService;
import com.example.sideworks.leave.service.LeaveRequestService;
import com.example.sideworks.notification.service.ApprovalNotificationWorkflow;
import com.example.sideworks.user.entity.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real controller/security filter/service/JPA/MySQL; only token parsing and unused workflows are mocked. */
@EnabledIfEnvironmentVariable(named = "SIDEWORKS_DASHBOARD_BENCHMARK", matches = "true")
@SpringBootTest(classes = DashboardApiIntegrationTest.Config.class, properties = {
        "spring.config.location=optional:classpath:/dashboard-benchmark-empty.properties",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session.events.log=false",
        "app.cors.allowed-origins=http://localhost:5173",
        "logging.level.root=WARN",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@AutoConfigureMockMvc
@MockitoBean(types = {
        FileStorage.class, ApprovalSubmissionValidator.class, ApprovalSubmissionFactory.class,
        AttendanceCorrectionService.class, LeaveRequestService.class, LeaveCancellationService.class,
        ApprovalDocumentTypeService.class, ApprovalNotificationWorkflow.class,
        ApprovalTemplateService.class, ApprovalDelegationService.class
})
class DashboardApiIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
    @Import({DashboardBaselineIntegrationTest.Config.class, ApprovalController.class,
            ApprovalService.class, SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
    static class Config {
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApprovalRepository approvals;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean JwtTokenProvider tokens;

    @BeforeEach
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                ignored -> DashboardBenchmarkFixture.seed(jdbc, 1_000));
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void authenticatedDashboardMatchesExistingViewsForEveryRole(UserRole role) throws Exception {
        jdbc.update("update usertbl set user_role=? where user_id=1", role.name());
        authenticate("bench-1", 1, role);
        var pending = approvals.findPendingByProcessorId(1L, java.time.LocalDate.now(), null, PageRequest.of(0, 4));
        var inProgress = approvals.findSentByWriterId(1L, null,
                com.example.sideworks.approval.entity.ApprovalStatus.IN_PROGRESS, PageRequest.of(0, 1));
        var drafts = approvals.findDraftsByWriterId(1L, null, PageRequest.of(0, 1));
        var cc = approvals.findCcByUserId(1L, null, null, PageRequest.of(0, 1));
        var activities = approvals.findRecentActivitiesByUserId(1L, PageRequest.of(0, 5));
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long before = statistics.getPrepareStatementCount();

        var result = mvc.perform(get("/api/approvals/dashboard")
                        .header("Authorization", "Bearer dashboard-test-placeholder")
                        .param("userId", "101").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.pending").value(pending.getTotalElements()))
                .andExpect(jsonPath("$.summary.inProgress").value(inProgress.getTotalElements()))
                .andExpect(jsonPath("$.summary.drafts").value(drafts.getTotalElements()))
                .andExpect(jsonPath("$.summary.cc").value(cc.getTotalElements()))
                .andExpect(jsonPath("$.pendingApprovals.length()").value(4))
                .andExpect(jsonPath("$.recentActivities.length()").value(5))
                .andReturn();

        // One user lookup plus seven approval queries; excludes token parsing and profile requests.
        // Recent activity takes two statements when the latest-history probe fills the page.
        assertThat(statistics.getPrepareStatementCount() - before).isEqualTo(8);
        var body = mapper.readTree(result.getResponse().getContentAsString());
        // Parse both sides as JSON so Long DTO IDs and parsed integer nodes compare by wire values.
        assertThat(body.get("pendingApprovals"))
                .isEqualTo(mapper.readTree(mapper.writeValueAsString(pending.getContent())));
        assertThat(body.get("recentActivities"))
                .isEqualTo(mapper.readTree(mapper.writeValueAsString(activities.getContent())));
    }

    @Test
    void userWithoutDocumentsGetsZeroCountsAndEmptyLists() throws Exception {
        authenticate("bench-101", 101, UserRole.USER);
        mvc.perform(get("/api/approvals/dashboard").header("Authorization", "Bearer dashboard-test-placeholder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.pending").value(0))
                .andExpect(jsonPath("$.summary.inProgress").value(0))
                .andExpect(jsonPath("$.summary.drafts").value(0))
                .andExpect(jsonPath("$.summary.cc").value(0))
                .andExpect(jsonPath("$.pendingApprovals").isEmpty())
                .andExpect(jsonPath("$.recentActivities").isEmpty());
    }

    @Test
    void anonymousRequestCannotQueryDashboard() throws Exception {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long before = statistics.getPrepareStatementCount();
        mvc.perform(get("/api/approvals/dashboard")).andExpect(status().isForbidden());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(before);
        verifyNoInteractions(tokens);
    }

    @Test
    void missingDatabaseUserReturnsNotFound() throws Exception {
        authenticate("not-in-fixture", 999, UserRole.USER);
        mvc.perform(get("/api/approvals/dashboard").header("Authorization", "Bearer dashboard-test-placeholder"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    private void authenticate(String loginId, long userId, UserRole role) {
        when(tokens.validateToken("dashboard-test-placeholder")).thenReturn(true);
        when(tokens.getLoginId("dashboard-test-placeholder")).thenReturn(loginId);
        when(tokens.getUserId("dashboard-test-placeholder")).thenReturn(userId);
        when(tokens.getUserRole("dashboard-test-placeholder")).thenReturn(role);
    }
}
