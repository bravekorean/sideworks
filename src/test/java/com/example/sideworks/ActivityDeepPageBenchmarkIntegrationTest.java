package com.example.sideworks;

import com.example.sideworks.approval.attachment.storage.FileStorage;
import com.example.sideworks.approval.factory.ApprovalSubmissionFactory;
import com.example.sideworks.approval.service.ApprovalDelegationService;
import com.example.sideworks.approval.service.ApprovalDocumentTypeService;
import com.example.sideworks.approval.service.ApprovalTemplateService;
import com.example.sideworks.approval.validator.ApprovalSubmissionValidator;
import com.example.sideworks.attendance.service.AttendanceCorrectionService;
import com.example.sideworks.auth.jwt.JwtTokenProvider;
import com.example.sideworks.leave.service.LeaveCancellationService;
import com.example.sideworks.leave.service.LeaveRequestService;
import com.example.sideworks.notification.service.ApprovalNotificationWorkflow;
import com.example.sideworks.support.RecordingStatementInspector;
import com.example.sideworks.user.entity.UserRole;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/approvals/activities 요청 전체(보안 필터·컨트롤러·서비스·JPA·MySQL·JSON)의 시간과 요청당 SQL 문장 수.
 * 같은 테스트를 변경 전·후 커밋에서 실행해 비교한다. 경로는 Hibernate 통계의 SQL별 실행 횟수로 구분한다
 * (window = 최신 구간 SQL, union = UNION 페이지 SQL, none = 100건 이내 고정 탐색만 쓰거나 목록 SQL 생략).
 */
@EnabledIfEnvironmentVariable(named = "SIDEWORKS_APPROVAL_PLAN_BENCHMARK", matches = "true")
@SpringBootTest(classes = DashboardApiIntegrationTest.Config.class, properties = {
        "spring.config.location=optional:classpath:/dashboard-benchmark-empty.properties",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session.events.log=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.example.sideworks.support.RecordingStatementInspector",
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
class ActivityDeepPageBenchmarkIntegrationTest {
    private static final int WARMUPS = 10;
    private static final int SAMPLES = 50;
    private static final long[] USERS = {1, 2, 10, 101};
    private static final int[] PAGES = {0, 5, 10, 50};

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean JwtTokenProvider tokens;

    private record Cell(long user, int page) {}

    // 저장소의 최신 구간 SQL과 같은 문장. 힌트 없이 정렬 인덱스를 쓰는지 기록한다(사용자 2, offset 200).
    private static final String WINDOW_SQL = """
            with latest as (
                select approval_history_id from approval_historytbl
                order by created_at desc, approval_history_id desc
                limit ?
            ), page as (
                select h.approval_history_id, h.approval_id, h.actor_id, h.action_step, h.action_type, h.created_at
                from latest w
                join approval_historytbl h on h.approval_history_id = w.approval_history_id
                join approvaltbl a on a.approval_id = h.approval_id
                where a.writer_id = ?
                   or (a.approval_status <> 'DRAFT' and (
                        exists (select 1 from approval_linetbl l where l.approval_id = a.approval_id
                                and (l.approver_id = ? or l.processed_by_id = ?))
                        or exists (select 1 from approval_cctbl c where c.approval_id = a.approval_id
                                and c.user_id = ?)))
                order by h.created_at desc, h.approval_history_id desc
                limit 20 offset 200
            )
            select h.approval_history_id, a.approval_id, a.title, u.user_id, u.user_name,
                   h.action_step, h.action_type, h.created_at
            from page h
            join approvaltbl a on a.approval_id = h.approval_id
            join usertbl u on u.user_id = h.actor_id
            order by h.created_at desc, h.approval_history_id desc
            """;

    private String windowPlan() {
        long visible = jdbc.queryForObject("""
                with visible as (
                    select approval_id from approvaltbl where writer_id = 2
                    union select l.approval_id from approval_linetbl l join approvaltbl a on a.approval_id = l.approval_id
                          where (l.approver_id = 2 or l.processed_by_id = 2) and a.approval_status <> 'DRAFT'
                    union select c.approval_id from approval_cctbl c join approvaltbl a on a.approval_id = c.approval_id
                          where c.user_id = 2 and a.approval_status <> 'DRAFT')
                select count(*) from visible v join approval_historytbl h on h.approval_id = v.approval_id
                """, Long.class);
        long maxId = jdbc.queryForObject("select max(approval_history_id) from approval_historytbl", Long.class);
        long window = (long) Math.ceil(2.0 * 220 * maxId / visible);
        return "user=2 offset=200 window=" + window + System.lineSeparator()
                + jdbc.queryForObject("explain analyze " + WINDOW_SQL, String.class, window, 2, 2, 2, 2);
    }

    @Test
    void measuresRecentActivityRequests() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                ignored -> DashboardBenchmarkFixture.seed(jdbc, 20_000));
        for (String table : List.of("usertbl", "approvaltbl", "approval_linetbl", "approval_cctbl", "approval_historytbl")) {
            jdbc.queryForList("analyze table " + table);
        }
        when(tokens.validateToken("activity-benchmark")).thenReturn(true);
        when(tokens.getUserRole("activity-benchmark")).thenReturn(UserRole.USER);

        List<Cell> cells = new ArrayList<>();
        for (long user : USERS) for (int page : PAGES) cells.add(new Cell(user, page));
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        // 측정 표본마다 SQL 문장 수와 목록 경로를 남긴다(워밍업 제외).
        long[][] statements = new long[cells.size()][SAMPLES];
        RecentActivityListPath[][] paths = new RecentActivityListPath[cells.size()][SAMPLES];
        long[][] nanos = new long[cells.size()][SAMPLES];
        for (int round = -WARMUPS; round < SAMPLES; round++) {
            for (int i = 0; i < cells.size(); i++) {
                // 셀 순서를 라운드마다 돌려 측정 순서의 영향을 줄인다.
                int index = Math.floorMod(i + round, cells.size());
                Cell cell = cells.get(index);
                when(tokens.getLoginId("activity-benchmark")).thenReturn("bench-" + cell.user());
                when(tokens.getUserId("activity-benchmark")).thenReturn(cell.user());
                long beforeStatements = statistics.getPrepareStatementCount();
                RecordingStatementInspector.start();
                long start = System.nanoTime();
                mvc.perform(get("/api/approvals/activities")
                                .header("Authorization", "Bearer activity-benchmark")
                                .param("page", String.valueOf(cell.page())).param("size", "20"))
                        .andExpect(status().isOk());
                long elapsed = System.nanoTime() - start;
                List<String> recorded = RecordingStatementInspector.stop();
                long statementCount = statistics.getPrepareStatementCount() - beforeStatements;
                if (round >= 0) {
                    nanos[index][round] = elapsed;
                    statements[index][round] = statementCount;
                    // NONE = 100건 이내 고정 탐색만 사용했거나 목록 SQL을 생략한 경우
                    paths[index][round] = RecentActivityListPath.of(recorded);
                }
            }
        }

        var report = new StringBuilder("# Recent activity request (HTTP, whole request)\n\n")
                .append("MySQL ").append(jdbc.queryForObject("select version()", String.class))
                .append("; 20,000 documents / 100,000 histories; page size 20; ")
                .append(WARMUPS).append(" warmup rounds, ").append(SAMPLES).append(" rotating samples per cell.\n\n")
                .append("Path and statement columns count every measured sample (warmups excluded).\n\n")
                .append("| user | page | offset | p50 ms | p95 ms | SQL statements (count) | list path (count) |\n|---:|---:|---:|---:|---:|---|---|\n");
        var raw = new StringBuilder("user,page,sample_ns,statements,list_path\n");
        var totals = new java.util.TreeMap<RecentActivityListPath, Integer>();
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            var statementCounts = new java.util.TreeMap<Long, Integer>();
            var pathCounts = new java.util.TreeMap<RecentActivityListPath, Integer>();
            for (int s = 0; s < SAMPLES; s++) {
                raw.append(cell.user()).append(',').append(cell.page()).append(',').append(nanos[i][s]).append(',')
                        .append(statements[i][s]).append(',').append(paths[i][s]).append('\n');
                statementCounts.merge(statements[i][s], 1, Integer::sum);
                pathCounts.merge(paths[i][s], 1, Integer::sum);
                if (cell.page() * 20 + 20 > 100) totals.merge(paths[i][s], 1, Integer::sum);
            }
            long[] sorted = nanos[i].clone();
            Arrays.sort(sorted);
            report.append(String.format(Locale.ROOT, "| %d | %d | %d | %.3f | %.3f | %s | %s |%n", cell.user(), cell.page(),
                    cell.page() * 20, sorted[SAMPLES / 2 - 1] / 1e6, sorted[(int) Math.ceil(SAMPLES * 0.95) - 1] / 1e6,
                    statementCounts, pathCounts));
        }
        report.append("\nDeep-page samples (offset + size > 100) by list path: ").append(totals).append('\n');
        Path output = Path.of("build/reports/activity-deep-page-request");
        Files.createDirectories(output);
        Files.writeString(output.resolve("results.md"), report);
        Files.writeString(output.resolve("samples.csv"), raw);
        Files.writeString(output.resolve("window-plan.txt"), windowPlan());
        System.out.println(report);
    }
}
