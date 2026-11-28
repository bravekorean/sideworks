package com.example.sideworks;

import com.example.sideworks.approval.dto.ApprovalActivityResponse;
import com.example.sideworks.approval.repository.ApprovalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SIDEWORKS_APPROVAL_PLAN_BENCHMARK", matches = "true")
@DataJpaTest(showSql = false, properties = {
        "spring.config.location=optional:classpath:/dashboard-benchmark-empty.properties",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.level.root=WARN",
        "logging.level.org.hibernate.SQL=OFF"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = DashboardBaselineIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApprovalActivityPlanIntegrationTest {
    private static final int SAMPLES = 50;
    private static final String HISTORY_INDEX = "bench_activity_latest";
    private static final String PROJECTION = """
            select h.approval_history_id, a.approval_id, a.title, u.user_id,
                   u.user_name, h.action_step, h.action_type, h.created_at
            """;
    private static final String ORDER = " order by h.created_at desc, h.approval_history_id desc limit ?";
    private static final String EXISTING = PROJECTION + """
            from approval_historytbl h join approvaltbl a on a.approval_id=h.approval_id
            join usertbl u on u.user_id=h.actor_id
            where a.writer_id=? or (a.approval_status<>'DRAFT' and (
                exists (select 1 from approval_linetbl l where l.approval_id=a.approval_id and l.approver_id=?)
                or exists (select 1 from approval_cctbl c where c.approval_id=a.approval_id and c.user_id=?)
            ))
            """ + ORDER;
    private static final String VISIBLE = """
            with visible as (
                select approval_id from approvaltbl where writer_id=?
                union
                select l.approval_id from approval_linetbl l join approvaltbl a on a.approval_id=l.approval_id
                where l.approver_id=? and a.approval_status<>'DRAFT'
                union
                select c.approval_id from approval_cctbl c join approvaltbl a on a.approval_id=c.approval_id
                where c.user_id=? and a.approval_status<>'DRAFT'
            )
            """;
    private static final String CANDIDATE = VISIBLE + PROJECTION + """
            from visible v join approvaltbl a on a.approval_id=v.approval_id
            join approval_historytbl h on h.approval_id=v.approval_id
            join usertbl u on u.user_id=h.actor_id
            """ + ORDER;
    // The history actor and document FKs guarantee the final joins preserve each selected row.
    private static final String LATE_JOIN = VISIBLE + """
            , latest as (
                select h.approval_history_id, h.approval_id, h.actor_id, h.action_step, h.action_type, h.created_at
                from visible v join approval_historytbl h on h.approval_id=v.approval_id
            """ + ORDER + """
            )
            """ + PROJECTION + """
            from latest h join approvaltbl a on a.approval_id=h.approval_id
            join usertbl u on u.user_id=h.actor_id
            order by h.created_at desc,h.approval_history_id desc
            """;
    private static final String WITHOUT_HISTORY_INDEX = EXISTING.replace("from approval_historytbl h join",
            "from approval_historytbl h ignore index (" + HISTORY_INDEX + ") join");
    private static final String RECENT_WINDOW = EXISTING.replace("from approval_historytbl h join", """
            from (
                select approval_history_id, approval_id, actor_id, action_step, action_type, created_at
                from approval_historytbl
                order by created_at desc, approval_history_id desc limit 100
            ) h join""");

    @Autowired JdbcTemplate jdbc;
    @Autowired ApprovalRepository approvals;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void comparesActivityListPlansWithoutChangingIndexes() throws Exception {
        seedActivityData();
        Path output = Path.of("build/reports/approval-activity-plan");
        Files.createDirectories(output);
        var report = new StringBuilder("# Approval activity list SQL experiment\n\n")
                .append("MySQL ").append(jdbc.queryForObject("select version()", String.class))
                .append("; 20,000 documents / 100,000 histories; 10 warmups and 50 rotating samples per variant/user.\n")
                .append("Both run via JDBC against the same entity-generated indexes. Existing SQL mirrors QueryDSL joins, projection, predicates and sort.\n")
                .append("Wall time includes JDBC execution and mapping. EXPLAIN ANALYZE timings are separate.\n\n")
                .append("| user | result rows | variant | p50 ms | p95 ms |\n|---:|---:|---|---:|---:|\n");
        var raw = new StringBuilder("user,sample,variant,nanos\n");
        var variants = List.of(EXISTING, CANDIDATE, LATE_JOIN);
        var labels = List.of("OR + EXISTS", "UNION", "UNION + late join");
        for (long userId : new long[]{1, 2, 10, 101}) {
            for (int limit : new int[]{1, 5, 20}) verifyEquivalent(userId, limit);
            var existing = load(EXISTING, userId, 5);
            for (int i = 0; i < 10; i++) {
                for (String sql : variants) load(sql, userId, 5);
            }
            long[][] times = new long[variants.size()][SAMPLES];
            for (int i = 0; i < SAMPLES; i++) {
                for (int j = 0; j < variants.size(); j++) {
                    int variant = (i + j) % variants.size();
                    long start = System.nanoTime();
                    var result = load(variants.get(variant), userId, 5);
                    long elapsed = System.nanoTime() - start;
                    times[variant][i] = elapsed;
                    assertThat(result).isEqualTo(existing);
                    raw.append(userId).append(',').append(i).append(',').append(variant).append(',').append(elapsed).append('\n');
                }
            }
            for (int variant = 0; variant < variants.size(); variant++) {
                Arrays.sort(times[variant]);
                report.append(String.format(Locale.ROOT, "| %d | %d | %s | %.3f | %.3f |%n", userId,
                        existing.size(), labels.get(variant),
                        times[variant][24] / 1e6, times[variant][47] / 1e6));
            }
            Files.writeString(output.resolve("plans-" + userId + ".txt"), "OR + EXISTS\n" + plan(EXISTING, userId)
                    + "\nUNION\n" + plan(CANDIDATE, userId) + "\nUNION + late join\n" + plan(LATE_JOIN, userId));
        }
        verifyDraftVisibility();
        Files.writeString(output.resolve("results.md"), report);
        Files.writeString(output.resolve("samples.csv"), raw);
        Files.writeString(output.resolve("queries.sql"), EXISTING + ";\n\n" + CANDIDATE + ";\n\n" + LATE_JOIN + ";\n");
    }

    @Test
    void comparesOrderedIndexAndBoundedProbe() throws Exception {
        seedActivityData();
        var output = Path.of("build/reports/approval-activity-index");
        Files.createDirectories(output);
        var originalPlans = new StringBuilder();
        for (long user : new long[]{1, 2, 10, 101}) {
            originalPlans.append("user=").append(user).append('\n').append(plan(EXISTING, user)).append('\n');
        }
        Files.writeString(output.resolve("plans-before-index.txt"), originalPlans);
        jdbc.execute("create index " + HISTORY_INDEX + " on approval_historytbl(created_at desc, approval_history_id desc)");
        try {
            jdbc.queryForList("analyze table approval_historytbl");
            var read = new TransactionTemplate(transactions);
            read.setReadOnly(true);
            read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            var report = new StringBuilder("# Recent activity index and bounded probe comparison\n\n")
                    .append("MySQL ").append(jdbc.queryForObject("select version()", String.class))
                    .append("; 20,000 documents / 100,000 histories. Ten warmups and 50 rotating samples per variant/user.\n")
                    .append("Baseline ignores only the new index, indexed mode uses the natural plan. Probe checks latest 100 then uses UNION if fewer than five visible.\n")
                    .append("Time includes one read-only transaction plus JDBC and mapping. Probe and fallback share one snapshot.\n\n")
                    .append("| user | variant | p50 ms | p95 ms | fallbacks / 50 |\n|---:|---|---:|---:|---:|\n");
            var raw = new StringBuilder("user,sample,variant,nanos,fallback\n");
            var labels = List.of("baseline", "ordered-index", "bounded-probe");
            for (long user : new long[]{1, 2, 10, 101}) {
                for (int limit : new int[]{1, 5, 20}) verifyProbe(read, user, limit);
                var expected = load(WITHOUT_HISTORY_INDEX, user, 5);
                for (int i = 0; i < 10; i++) {
                    for (int variant = 0; variant < labels.size(); variant++) runActivity(read, variant, user, 5);
                }
                long[][] times = new long[labels.size()][SAMPLES];
                int fallbacks = 0;
                for (int i = 0; i < SAMPLES; i++) {
                    for (int j = 0; j < labels.size(); j++) {
                        int variant = (i + j) % labels.size();
                        long start = System.nanoTime();
                        var result = runActivity(read, variant, user, 5);
                        long elapsed = System.nanoTime() - start;
                        assertThat(result.rows()).isEqualTo(expected);
                        times[variant][i] = elapsed;
                        if (result.fallback()) fallbacks++;
                        raw.append(user).append(',').append(i).append(',').append(labels.get(variant))
                                .append(',').append(elapsed).append(',').append(result.fallback()).append('\n');
                    }
                }
                for (int variant = 0; variant < labels.size(); variant++) {
                    Arrays.sort(times[variant]);
                    report.append(String.format(Locale.ROOT, "| %d | %s | %.3f | %.3f | %s |%n", user,
                            labels.get(variant), times[variant][24] / 1e6, times[variant][47] / 1e6,
                            variant == 2 ? fallbacks : "-"));
                }
                Files.writeString(output.resolve("plans-" + user + ".txt"), "Baseline\n" + plan(WITHOUT_HISTORY_INDEX, user)
                        + "\nOrdered index\n" + plan(EXISTING, user) + "\nProbe\n" + plan(RECENT_WINDOW, user)
                        + "\nFallback\n" + plan(LATE_JOIN, user));
            }
            verifyProbeBoundaries(read);
            Files.writeString(output.resolve("results.md"), report);
            Files.writeString(output.resolve("samples.csv"), raw);
            Files.writeString(output.resolve("queries.sql"), WITHOUT_HISTORY_INDEX + ";\n\n" + EXISTING + ";\n\n"
                    + RECENT_WINDOW + ";\n\n" + LATE_JOIN + ";\n");
        } finally {
            jdbc.execute("drop index " + HISTORY_INDEX + " on approval_historytbl");
        }
    }

    // Paged GET /api/approvals/activities: the list and the count are timed separately.
    // "before" is the pre-change OR + EXISTS SQL on the pre-index schema, "indexed" is the same SQL
    // once the history index exists (the regression), "new" is the probe/UNION path of PR 2.
    // Deep pages now try a sized latest window first; that change is measured by ActivityDeepPageBenchmarkIntegrationTest.
    private static final String PAGED_WHERE = """
            from approval_historytbl h %s join approvaltbl a on a.approval_id=h.approval_id
            join usertbl u on u.user_id=h.actor_id
            where a.writer_id=? or (a.approval_status<>'DRAFT' and (
                exists (select 1 from approval_linetbl l where l.approval_id=a.approval_id and l.approver_id=?)
                or exists (select 1 from approval_cctbl c where c.approval_id=a.approval_id and c.user_id=?)
            ))
            """;
    private static final String PAGED_LIST = PROJECTION + PAGED_WHERE
            + " order by h.created_at desc, h.approval_history_id desc limit ? offset ?";
    private static final String PAGED_COUNT = "select count(*) " + PAGED_WHERE;
    private static final String NEW_COUNT = VISIBLE + """
            select count(*) from visible v join approval_historytbl h on h.approval_id=v.approval_id
            """;
    private static final String PAGED_INDEX = "idx_approval_historytbl_created";
    private static final String NEW_PAGE = LATE_JOIN.replaceFirst(" limit \\?", " limit ? offset ?");

    @Test
    void comparesPagedActivityListAndCount() throws Exception {
        seedActivityData();
        jdbc.execute("create index " + PAGED_INDEX + " on approval_historytbl(created_at desc, approval_history_id desc)");
        jdbc.queryForList("analyze table approval_historytbl");
        var output = Path.of("build/reports/approval-activity-paged");
        Files.createDirectories(output);
        var read = new TransactionTemplate(transactions);
        read.setReadOnly(true);
        var report = new StringBuilder("# Paged recent activity list and count\n\n")
                .append("MySQL ").append(jdbc.queryForObject("select version()", String.class))
                .append("; 20,000 documents / 100,000 histories; page size 20. Ten warmups, 50 rotating samples.\n")
                .append("before = OR + EXISTS without the history index, indexed = same SQL with it, new = shipped path.\n")
                .append("list new: pages inside the latest-100 window use the repository probe, deeper pages the UNION page SQL.\n\n")
                .append("| user | page | query | before p50 | indexed p50 | new p50 | new p95 |\n|---:|---:|---|---:|---:|---:|---:|\n");
        var plans = new StringBuilder();
        String ignore = "ignore index (" + PAGED_INDEX + ")";
        for (long user : new long[]{1, 2, 10, 101}) {
            for (int page : new int[]{0, 10}) {
                int offset = page * 20;
                var expected = jdbc.queryForList(String.format(PAGED_LIST, ignore), user, user, user, 20, offset);
                var actual = approvals.findRecentActivitiesByUserId(user, PageRequest.of(page, 20));
                assertThat(actual.getContent()).extracting(ApprovalActivityResponse::getApprovalHistoryId)
                        .containsExactlyElementsOf(expected.stream()
                                .map(row -> ((Number) row.get("approval_history_id")).longValue()).toList());
                long expectedCount = jdbc.queryForObject(String.format(PAGED_COUNT, ignore), Long.class, user, user, user);
                assertThat(actual.getTotalElements()).isEqualTo(expectedCount);
                assertThat(jdbc.queryForObject(NEW_COUNT, Long.class, user, user, user)).isEqualTo(expectedCount);

                Runnable newList = offset + 20 <= 100
                        ? () -> approvals.findLatestActivitiesByUserId(user, 20)
                        : () -> jdbc.queryForList(NEW_PAGE, user, user, user, 20, offset);
                long[][] list = time(read, List.of(
                        () -> jdbc.queryForList(String.format(PAGED_LIST, ignore), user, user, user, 20, offset),
                        () -> jdbc.queryForList(String.format(PAGED_LIST, ""), user, user, user, 20, offset),
                        newList));
                row(report, user, page, "list", list);
                if (page == 0) {
                    long[][] count = time(read, List.of(
                            () -> jdbc.queryForObject(String.format(PAGED_COUNT, ignore), Long.class, user, user, user),
                            () -> jdbc.queryForObject(String.format(PAGED_COUNT, ""), Long.class, user, user, user),
                            () -> jdbc.queryForObject(NEW_COUNT, Long.class, user, user, user)));
                    row(report, user, page, "count", count);
                    plans.append("user=").append(user).append(" count before\n")
                            .append(jdbc.queryForObject("explain analyze " + String.format(PAGED_COUNT, ignore), String.class, user, user, user))
                            .append("\ncount new\n")
                            .append(jdbc.queryForObject("explain analyze " + NEW_COUNT, String.class, user, user, user)).append('\n');
                }
            }
        }
        Files.writeString(output.resolve("results.md"), report);
        Files.writeString(output.resolve("plans.txt"), plans);
    }

    private long[][] time(TransactionTemplate read, List<Runnable> variants) {
        for (int i = 0; i < 10; i++) variants.forEach(v -> read.executeWithoutResult(ignored -> v.run()));
        long[][] times = new long[variants.size()][SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            for (int j = 0; j < variants.size(); j++) {
                int variant = (i + j) % variants.size();
                long start = System.nanoTime();
                read.executeWithoutResult(ignored -> variants.get(variant).run());
                times[variant][i] = System.nanoTime() - start;
            }
        }
        for (long[] t : times) Arrays.sort(t);
        return times;
    }

    private void row(StringBuilder report, long user, int page, String query, long[][] t) {
        report.append(String.format(Locale.ROOT, "| %d | %d | %s | %.3f | %.3f | %.3f | %.3f |%n", user, page, query,
                t[0][24] / 1e6, t[1][24] / 1e6, t[2][24] / 1e6, t[2][47] / 1e6));
    }

    private void seedActivityData() {
        new TransactionTemplate(transactions).executeWithoutResult(
                ignored -> DashboardBenchmarkFixture.seed(jdbc, 20_000));
        // The entity now declares this index; drop it so the baseline matches the pre-index schema.
        // Both tests share one schema, so drop it only while it still exists.
        Integer declared = jdbc.queryForObject("""
                select count(*) from information_schema.statistics
                where table_schema=database() and table_name='approval_historytbl'
                  and index_name='idx_approval_historytbl_created'
                """, Integer.class);
        if (declared != null && declared > 0) {
            jdbc.execute("drop index idx_approval_historytbl_created on approval_historytbl");
        }
        for (String table : List.of("approvaltbl", "approval_linetbl", "approval_cctbl", "approval_historytbl")) {
            jdbc.queryForList("analyze table " + table);
        }
    }

    private record ActivityRun(List<Map<String, Object>> rows, boolean fallback) {}

    private ActivityRun runActivity(TransactionTemplate read, int variant, long user, int limit) {
        return read.execute(ignored -> {
            if (variant == 0) return new ActivityRun(load(WITHOUT_HISTORY_INDEX, user, limit), false);
            if (variant == 1) return new ActivityRun(load(EXISTING, user, limit), false);
            var rows = load(RECENT_WINDOW, user, limit);
            if (rows.size() == limit) return new ActivityRun(rows, false);
            return new ActivityRun(load(LATE_JOIN, user, limit), true);
        });
    }

    private void verifyProbe(TransactionTemplate read, long user, int limit) {
        var expected = load(WITHOUT_HISTORY_INDEX, user, limit);
        assertThat(runActivity(read, 1, user, limit).rows()).isEqualTo(expected);
        assertThat(runActivity(read, 2, user, limit).rows()).isEqualTo(expected);
    }

    private void verifyProbeBoundaries(TransactionTemplate read) {
        jdbc.update("insert into approval_cctbl(approval_id,user_id,created_at) values(19998,101,now())");
        verifyProbe(read, 101, 5);
        assertThat(runActivity(read, 2, 101, 5).rows()).isEmpty();
        jdbc.update("update approvaltbl set writer_id=101 where approval_id in (3,19998)");
        // Only five visible histories are in the window; the other five are older than the window.
        verifyProbe(read, 101, 5);
        assertThat(runActivity(read, 2, 101, 5).fallback()).isFalse();
        verifyProbe(read, 101, 6);
        var result = runActivity(read, 2, 101, 6);
        assertThat(result.fallback()).isTrue();
        assertThat(result.rows()).hasSize(6);
    }

    private void verifyEquivalent(long userId, int limit) {
        var oldRows = load(EXISTING, userId, limit);
        assertThat(load(CANDIDATE, userId, limit)).isEqualTo(oldRows);
        assertThat(load(LATE_JOIN, userId, limit)).isEqualTo(oldRows);
        assertThat(approvals.findLatestActivitiesByUserId(userId, limit))
                .extracting(ApprovalActivityResponse::getApprovalHistoryId)
                .containsExactlyElementsOf(oldRows.stream().map(row -> ((Number) row.get("approval_history_id")).longValue()).toList());
    }

    private void verifyDraftVisibility() {
        jdbc.update("insert into approval_linetbl(approval_id,approver_id,approval_step,approval_status,created_at) values(19998,101,1,'PENDING',now())");
        jdbc.update("insert into approval_cctbl(approval_id,user_id,created_at) values(19998,101,now())");
        verifyEquivalent(101, 5);
        assertThat(load(CANDIDATE, 101, 5)).isEmpty();
        jdbc.update("update approvaltbl set writer_id=101 where approval_id=19998 and approval_status='DRAFT'");
        verifyEquivalent(101, 20);
        assertThat(load(CANDIDATE, 101, 20)).hasSize(5);
    }

    private List<Map<String, Object>> load(String sql, long userId, int limit) {
        return jdbc.queryForList(sql, userId, userId, userId, limit);
    }

    private String plan(String sql, long userId) {
        return jdbc.queryForObject("explain analyze " + sql, String.class, userId, userId, userId, 5);
    }
}
