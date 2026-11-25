package com.example.sideworks;

import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.approval.repository.ApprovalRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "SIDEWORKS_DASHBOARD_BENCHMARK", matches = "true")
@DataJpaTest(showSql = false, properties = {
        "spring.config.location=optional:classpath:/dashboard-benchmark-empty.properties",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session.events.log=false",
        "logging.level.root=WARN",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = DashboardBaselineIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DashboardQueryComparisonIntegrationTest {
    private static final int WARMUP = 15;
    private static final int ROUNDS = 3;
    private static final int SAMPLES_PER_ROUND = 50;
    private static final int TOTAL_SAMPLES = ROUNDS * SAMPLES_PER_ROUND;
    private static final Path OUTPUT = Path.of("build/reports/dashboard-query-comparison");

    @Autowired ApprovalRepository approvals;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void newQueriesKeepDashboardCountsAndVisibleActivity() {
        seed(1_000);
        // Other users must not see drafts even when a stray line or CC references them.
        jdbc.update("""
                insert into approval_cctbl(approval_id,user_id,created_at)
                values (999,101,'2026-01-01')
                """);
        jdbc.update("""
                insert into approval_linetbl(approval_id,approver_id,approval_step,approval_status,created_at)
                values (999,101,1,'PENDING','2026-01-01')
                """);
        assertThat(approvals.findLatestActivitiesByUserId(101L, 5)).isEmpty();
        // Counts follow the current Page query exactly, including CC's existing status policy.
        jdbc.update("update approvaltbl set approval_status='CANCELED' where approval_id=1000");
        jdbc.update("update approvaltbl set approval_status='REJECTED' where approval_id=998");
        jdbc.update("update approvaltbl set approval_status='TERMINATED' where approval_id=997");
        for (long userId : new long[]{1, 2, 10, 101}) verifyEquivalent(userId);
        jdbc.update("update approvaltbl set writer_id=101 where approval_id=999");
        assertThat(approvals.findLatestActivitiesByUserId(101L, 5)).hasSize(5)
                .allSatisfy(activity -> assertThat(activity.getApprovalId()).isEqualTo(999L));
        verifyEquivalent(101);
        assertThatThrownBy(() -> approvals.findLatestActivitiesByUserId(1L, 0))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> approvals.findLatestActivitiesByUserId(1L, -1))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compareAlternatingBaselineAndPurposeSpecificQueries() throws Exception {
        Files.createDirectories(OUTPUT);
        StringBuilder report = new StringBuilder("# Dashboard query comparison\n\n");
        report.append("MySQL: ").append(jdbc.queryForObject("select version()", String.class))
                .append("; Java: ").append(System.getProperty("java.version"))
                .append("; OS: ").append(System.getProperty("os.name"))
                .append("; JVM processors: ").append(Runtime.getRuntime().availableProcessors())
                .append("\n\nWarmup ").append(WARMUP).append(" pairs; ").append(ROUNDS)
                .append(" rounds x ").append(SAMPLES_PER_ROUND)
                .append(" samples per variant/scenario. Variant order alternates per sample.\n")
                .append("Read-only transaction per call; ORM statistics enabled; sequential repository bundle, not HTTP latency.\n")
                .append("Same fixture, DB, entity-generated schema and indexes for both variants; no timing assertions.\n\n")
                .append("| documents | user | variant | query | SQL/call | p50 ms | p95 ms | errors |\n")
                .append("|---:|---:|---|---|---:|---:|---:|---:|\n");
        StringBuilder raw = new StringBuilder("documents,user,round,sample,variant,query,nanos\n");
        for (int documents : new int[]{1_000, 20_000}) {
            seed(documents);
            for (long userId : new long[]{1, 10, 101}) {
                verifyEquivalent(userId);
                measure(documents, userId, report, raw);
                verifyEquivalent(userId);
            }
        }
        Files.writeString(OUTPUT.resolve("results.md"), report);
        Files.writeString(OUTPUT.resolve("samples.csv"), raw);
        StringBuilder schema = new StringBuilder();
        for (String table : List.of("usertbl", "approvaltbl", "approval_linetbl", "approval_cctbl", "approval_historytbl")) {
            schema.append(jdbc.queryForMap("show create table " + table).get("Create Table")).append(";\n\n");
        }
        Files.writeString(OUTPUT.resolve("schema.sql"), schema);
    }

    private void seed(int documents) {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                ignored -> DashboardBenchmarkFixture.seed(jdbc, documents));
        for (String table : List.of("approvaltbl", "approval_linetbl", "approval_cctbl", "approval_historytbl")) {
            jdbc.queryForList("analyze table " + table);
        }
    }

    private void verifyEquivalent(long userId) {
        assertThat(approvals.countInProgressByWriterId(userId)).isEqualTo(
                approvals.findSentByWriterId(userId, null, ApprovalStatus.IN_PROGRESS, PageRequest.of(0, 1)).getTotalElements());
        assertThat(approvals.countDraftsByWriterId(userId)).isEqualTo(
                approvals.findDraftsByWriterId(userId, null, PageRequest.of(0, 1)).getTotalElements());
        assertThat(approvals.countCcByUserId(userId)).isEqualTo(
                approvals.findCcByUserId(userId, null, null, PageRequest.of(0, 1)).getTotalElements());
        for (int limit : new int[]{1, 5, 20}) {
            var latestActivities = approvals.findLatestActivitiesByUserId(userId, limit);
            assertThat(latestActivities)
                    .usingRecursiveComparison().isEqualTo(
                            approvals.findRecentActivitiesByUserId(userId, PageRequest.of(0, limit)).getContent());
            assertThat(latestActivities).extracting(activity -> activity.getApprovalHistoryId())
                    .containsExactlyElementsOf(jdbc.queryForList("""
                            select h.approval_history_id from approval_historytbl h
                            join approvaltbl a on a.approval_id=h.approval_id
                            where a.writer_id=? or (a.approval_status<>'DRAFT' and (
                            exists(select 1 from approval_linetbl l where l.approval_id=a.approval_id and l.approver_id=?)
                            or exists(select 1 from approval_cctbl c where c.approval_id=a.approval_id and c.user_id=?)))
                            order by h.created_at desc,h.approval_history_id desc limit ?
                            """, Long.class, userId, userId, userId, limit));
        }
    }

    private Map<String, Supplier<?>> queries(long userId, boolean optimized) {
        Map<String, Supplier<?>> queries = new LinkedHashMap<>();
        queries.put("pending", () -> approvals.findPendingByApproverId(userId, null, PageRequest.of(0, 4)));
        if (optimized) {
            queries.put("sent", () -> approvals.countInProgressByWriterId(userId));
            queries.put("drafts", () -> approvals.countDraftsByWriterId(userId));
            queries.put("cc", () -> approvals.countCcByUserId(userId));
            queries.put("activities", () -> approvals.findLatestActivitiesByUserId(userId, 5));
        } else {
            queries.put("sent", () -> approvals.findSentByWriterId(userId, null, ApprovalStatus.IN_PROGRESS, PageRequest.of(0, 1)));
            queries.put("drafts", () -> approvals.findDraftsByWriterId(userId, null, PageRequest.of(0, 1)));
            queries.put("cc", () -> approvals.findCcByUserId(userId, null, null, PageRequest.of(0, 1)));
            queries.put("activities", () -> approvals.findRecentActivitiesByUserId(userId, PageRequest.of(0, 5)));
        }
        return queries;
    }

    private void measure(int documents, long userId, StringBuilder report, StringBuilder raw) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        Map<String, Map<String, Supplier<?>>> variants = new LinkedHashMap<>();
        variants.put("baseline", queries(userId, false));
        variants.put("optimized", queries(userId, true));
        for (int i = 0; i < WARMUP; i++) {
            for (var querySet : variants.values()) {
                querySet.values().forEach(query -> transaction.execute(ignored -> query.get()));
            }
        }
        Map<String, Map<String, long[]>> timings = new LinkedHashMap<>();
        for (var variant : variants.entrySet()) {
            Map<String, long[]> series = new LinkedHashMap<>();
            variant.getValue().keySet().forEach(name -> series.put(name, new long[TOTAL_SAMPLES]));
            series.put("sequential-bundle", new long[TOTAL_SAMPLES]);
            timings.put(variant.getKey(), series);
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        for (int round = 0; round < ROUNDS; round++) {
            for (int sample = 0; sample < SAMPLES_PER_ROUND; sample++) {
                List<String> order = (round + sample) % 2 == 0
                        ? List.of("baseline", "optimized") : List.of("optimized", "baseline");
                int index = round * SAMPLES_PER_ROUND + sample;
                for (String variant : order) {
                    long bundleTime = 0;
                    long bundleSql = 0;
                    for (var query : variants.get(variant).entrySet()) {
                        long before = statistics.getPrepareStatementCount();
                        long start = System.nanoTime();
                        transaction.execute(ignored -> query.getValue().get());
                        long elapsed = System.nanoTime() - start;
                        long statements = statistics.getPrepareStatementCount() - before;
                        assertThat(statements).as("%s %s SQL", variant, query.getKey())
                                .isEqualTo(expectedSql(variant, query.getKey()));
                        timings.get(variant).get(query.getKey())[index] = elapsed;
                        bundleTime += elapsed;
                        bundleSql += statements;
                        raw.append(documents).append(',').append(userId).append(',').append(round)
                                .append(',').append(sample).append(',').append(variant).append(',')
                                .append(query.getKey()).append(',').append(elapsed).append('\n');
                    }
                    assertThat(bundleSql).isEqualTo(expectedSql(variant, "sequential-bundle"));
                    timings.get(variant).get("sequential-bundle")[index] = bundleTime;
                }
            }
            System.out.printf("Compared documents=%d user=%d round=%d%n", documents, userId, round + 1);
        }
        timings.forEach((variant, series) -> series.forEach((name, values) -> {
            long[] sorted = values.clone();
            Arrays.sort(sorted);
            report.append(String.format(Locale.ROOT, "| %d | %d | %s | %s | %d | %.3f | %.3f | 0 |%n",
                    documents, userId, variant, name, expectedSql(variant, name),
                    sorted[(int) Math.ceil(TOTAL_SAMPLES * .50) - 1] / 1_000_000.0,
                    sorted[(int) Math.ceil(TOTAL_SAMPLES * .95) - 1] / 1_000_000.0));
        }));
    }

    private long expectedSql(String variant, String query) {
        if (query.equals("sequential-bundle")) return variant.equals("baseline") ? 10 : 6;
        return variant.equals("baseline") || query.equals("pending") ? 2 : 1;
    }
}
