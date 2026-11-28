package com.example.sideworks;

import com.example.sideworks.approval.dto.ApprovalListResponse;
import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.config.QuerydslConfig;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static com.example.sideworks.approval.entity.QApproval.approval;
import static com.example.sideworks.approval.entity.QApprovalLine.approvalLine;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대결 규칙이 붙은 대기함 쿼리의 비용을 이전 쿼리(approver_id만 비교)와 비교한다. 시간 기준을 단정하지 않는 측정용 테스트다.
 * 결과: build/reports/delegation-inbox/results.md, plans.txt
 */
@EnabledIfEnvironmentVariable(named = "SIDEWORKS_DELEGATION_BENCHMARK", matches = "true")
@DataJpaTest(showSql = false, properties = {
        "spring.config.location=optional:classpath:/dashboard-benchmark-empty.properties",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session.events.log=false",
        "logging.level.root=WARN",
        "logging.level.org.hibernate.SQL=OFF"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ApprovalDelegationInboxBenchmarkIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApprovalDelegationInboxBenchmarkIntegrationTest {
    private static final int DOCUMENTS = 60_000;
    private static final int WARMUP = 10;
    private static final int SAMPLES = 50;
    private static final LocalDate TODAY = LocalDate.of(2026, 12, 2);
    private static final Path OUTPUT = Path.of("build/reports/delegation-inbox");

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.example.sideworks")
    @EnableJpaRepositories("com.example.sideworks")
    @Import(QuerydslConfig.class)
    static class Config {
        // 실행된 SQL 원문을 general_log에서 꺼내 EXPLAIN ANALYZE 하려고 root로 접속한다.
        @Bean
        @ServiceConnection
        MySQLContainer<?> mysql() {
            return new MySQLContainer<>("mysql:8.0.44").withUsername("root")
                    .withUrlParam("rewriteBatchedStatements", "true");
        }
    }

    @Autowired ApprovalRepository approvals;
    @Autowired JPAQueryFactory queryFactory;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PlatformTransactionManager transactionManager;

    private record Scenario(String name, long userId, boolean delegation) { }

    @Test
    void measurePendingInboxWithDelegationRule() throws Exception {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> DashboardBenchmarkFixture.seed(jdbc, DOCUMENTS));
        // 처리된 단계는 실제 처리자를 채운다(마이그레이션 2단계와 같은 상태).
        jdbc.update("update approval_linetbl set processed_by_id = approver_id where approval_status in ('APPROVED','REJECTED')");
        for (String table : List.of("approvaltbl", "approval_linetbl", "approval_cctbl", "usertbl")) {
            jdbc.queryForList("analyze table " + table);
        }

        Files.createDirectories(OUTPUT);
        StringBuilder report = new StringBuilder("# 대결 규칙을 적용한 결재 대기함 쿼리 비용\n\n")
                .append("MySQL ").append(jdbc.queryForObject("select version()", String.class))
                .append(", 문서 ").append(DOCUMENTS).append("건(대기 단계 ")
                .append(jdbc.queryForObject("select count(*) from approval_linetbl where approval_status='PENDING'", Long.class))
                .append("건), 첫 페이지 4건 + 전체 건수. 읽기 전용 트랜잭션 1회 = 1샘플, 워밍업 ")
                .append(WARMUP).append("회, 측정 ").append(SAMPLES).append("회. 시간 기준 단정 없음.\n\n")
                .append("| 시나리오 | 사용자 | 쿼리 | 건수 | SQL/호출 | p50 ms | p95 ms |\n")
                .append("|---|---:|---|---:|---:|---:|---:|\n");
        StringBuilder plans = new StringBuilder();

        List<Scenario> scenarios = List.of(
                new Scenario("위임 없음, 대기 많음", 2, false),
                new Scenario("위임 없음, 대기 중간", 1, false),
                new Scenario("위임 없음, 대기 없음", 101, false),
                new Scenario("2→50 위임 중, 원 결재자", 2, true),
                new Scenario("2→50 위임 중, 대결자", 50, true),
                new Scenario("1→60 위임 중, 원 결재자", 1, true),
                new Scenario("1→60 위임 중, 대결자", 60, true));
        boolean delegationInserted = false;
        for (Scenario scenario : scenarios) {
            if (scenario.delegation() && !delegationInserted) {
                jdbc.update("""
                        insert into approval_delegationtbl(delegator_id, delegatee_id, start_date, end_date, created_by, created_at)
                        values (2, 50, '2026-12-01', '2026-12-31', 2, now()),
                               (1, 60, '2026-12-01', '2026-12-31', 1, now())
                        """);
                delegationInserted = true;
            }
            if (!scenario.delegation()) {
                measure(report, scenario, "이전(approver_id만)", () -> readOnly.execute(ignored -> previousPending(scenario.userId())));
                recordPlan(plans, scenario, "이전", readOnly, () -> previousPending(scenario.userId()));
            }
            measure(report, scenario, "현재(대결 규칙)", () -> readOnly.execute(ignored ->
                    approvals.findPendingByProcessorId(scenario.userId(), TODAY, null, PageRequest.of(0, 4))));
            recordPlan(plans, scenario, "현재", readOnly, () ->
                    approvals.findPendingByProcessorId(scenario.userId(), TODAY, null, PageRequest.of(0, 4)));
            if (!scenario.delegation()) {
                // 위임이 없으면 결과가 이전 쿼리와 같아야 한다.
                var current = readOnly.execute(ignored ->
                        approvals.findPendingByProcessorId(scenario.userId(), TODAY, null, PageRequest.of(0, 4)));
                var previous = readOnly.execute(ignored -> previousPending(scenario.userId()));
                assertThat(current.getTotalElements()).isEqualTo(previous.total());
                assertThat(current.getContent().stream().map(ApprovalListResponse::getApprovalId).toList())
                        .isEqualTo(previous.ids());
            }
        }
        Files.writeString(OUTPUT.resolve("results.md"), report);
        Files.writeString(OUTPUT.resolve("plans.txt"), plans);
        StringBuilder schema = new StringBuilder();
        for (String table : List.of("approval_linetbl", "approval_delegationtbl")) {
            schema.append(jdbc.queryForMap("show create table " + table).get("Create Table")).append(";\n\n");
        }
        Files.writeString(OUTPUT.resolve("schema.sql"), schema);
    }

    private record PreviousPage(List<Long> ids, long total) { }

    // 변경 전 ApprovalRepositoryImpl.findPendingByApproverId와 같은 QueryDSL.
    private PreviousPage previousPending(long approverId) {
        List<ApprovalListResponse> content = queryFactory
                .select(Projections.constructor(ApprovalListResponse.class,
                        approval.approvalId, approval.title, approval.writer.userId, approval.writer.userName,
                        approval.approvalStatus, approval.currentStep, approval.createdAt, approval.submittedAt,
                        approval.completedAt))
                .from(approvalLine)
                .join(approvalLine.approval, approval)
                .join(approval.writer)
                .where(approvalLine.approver.userId.eq(approverId),
                        approvalLine.approvalStatus.eq(ApprovalLineStatus.PENDING),
                        approval.approvalStatus.eq(ApprovalStatus.IN_PROGRESS))
                .orderBy(approval.createdAt.desc(), approval.approvalId.desc())
                .limit(4)
                .fetch();
        Long total = queryFactory.select(approvalLine.count())
                .from(approvalLine)
                .join(approvalLine.approval, approval)
                .join(approval.writer)
                .where(approvalLine.approver.userId.eq(approverId),
                        approvalLine.approvalStatus.eq(ApprovalLineStatus.PENDING),
                        approval.approvalStatus.eq(ApprovalStatus.IN_PROGRESS))
                .fetchOne();
        return new PreviousPage(content.stream().map(ApprovalListResponse::getApprovalId).toList(),
                total == null ? 0 : total);
    }

    private void measure(StringBuilder report, Scenario scenario, String query, Supplier<Object> call) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        Object last = null;
        for (int i = 0; i < WARMUP; i++) last = call.get();
        long before = statistics.getPrepareStatementCount();
        long[] nanos = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long start = System.nanoTime();
            last = call.get();
            nanos[i] = System.nanoTime() - start;
        }
        double statementsPerCall = (statistics.getPrepareStatementCount() - before) / (double) SAMPLES;
        Arrays.sort(nanos);
        long total = last instanceof PreviousPage page ? page.total()
                : ((org.springframework.data.domain.Page<?>) last).getTotalElements();
        report.append(String.format(Locale.ROOT, "| %s | %d | %s | %d | %.0f | %.2f | %.2f |%n",
                scenario.name(), scenario.userId(), query, total, statementsPerCall,
                nanos[SAMPLES / 2] / 1e6, nanos[(int) Math.ceil(SAMPLES * 0.95) - 1] / 1e6));
    }

    // general_log로 이번 호출의 SQL 원문(리터럴 포함)을 모아 EXPLAIN ANALYZE를 남긴다.
    private void recordPlan(StringBuilder plans, Scenario scenario, String label,
                            TransactionTemplate readOnly, Runnable call) {
        jdbc.execute("truncate table mysql.general_log");
        jdbc.execute("set global log_output = 'TABLE'");
        jdbc.execute("set global general_log = 'ON'");
        readOnly.executeWithoutResult(ignored -> call.run());
        jdbc.execute("set global general_log = 'OFF'");
        List<String> statements = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "select convert(argument using utf8mb4) as sql_text from mysql.general_log "
                        + "where command_type = 'Query' and argument like 'select%' order by event_time")) {
            String sql = (String) row.get("sql_text");
            if (sql.contains("approval_linetbl") || sql.contains("approval_delegationtbl")) statements.add(sql);
        }
        plans.append("## ").append(scenario.name()).append(" / user ").append(scenario.userId())
                .append(" / ").append(label).append(" (SQL ").append(statements.size()).append("개)\n\n");
        for (String sql : statements) {
            plans.append(sql).append("\n\n");
            plans.append(jdbc.queryForObject("explain analyze " + sql, String.class)).append("\n\n");
        }
    }
}
