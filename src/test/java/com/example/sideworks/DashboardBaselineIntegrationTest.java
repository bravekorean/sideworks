package com.example.sideworks;

import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.config.QuerydslConfig;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in measurement of existing queries, not an HTTP load test or a timing assertion. */
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
class DashboardBaselineIntegrationTest {
    private static final int WARMUP = 15;
    private static final int SAMPLES = 100;
    private static final Path OUTPUT = Path.of("build/reports/dashboard-baseline");

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan("com.example.sideworks")
    @EnableJpaRepositories("com.example.sideworks")
    @Import(QuerydslConfig.class)
    static class Config {
        @Bean
        @ServiceConnection
        MySQLContainer<?> mysql() {
            // No host volume or existing database: this container owns all benchmark data.
            return new MySQLContainer<>("mysql:8.0.44")
                    .withUrlParam("rewriteBatchedStatements", "true");
        }
    }

    @Autowired ApprovalRepository approvals;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void measureExistingDashboardQueries() throws Exception {
        Files.createDirectories(OUTPUT);
        StringBuilder report = new StringBuilder("# Dashboard repository baseline\n\n");
        report.append("MySQL: ").append(jdbc.queryForObject("select version()", String.class))
                .append("; Java: ").append(System.getProperty("java.version"))
                .append("; OS: ").append(System.getProperty("os.name"))
                .append("; JVM processors: ").append(Runtime.getRuntime().availableProcessors())
                .append("\n\nWarmup: ").append(WARMUP).append(" bundles; samples: ").append(SAMPLES)
                .append(" per scenario. Read-only transaction per repository call. Sequential bundle; not HTTP latency.\n")
                .append("Statistics enabled; no timing thresholds. Fixture/schema/indexes are synthetic and entity-generated.\n\n")
                .append("| documents | histories | user | query | SQL/call | p50 ms | p95 ms | errors |\n")
                .append("|---:|---:|---:|---|---:|---:|---:|---:|\n");
        StringBuilder raw = new StringBuilder("documents,user,sample,query,nanos\n");
        for (int documents : new int[]{1_000, 20_000}) {
            // Batch inserts still commit per row under autocommit; seed in one transaction.
            new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> DashboardBenchmarkFixture.seed(jdbc, documents));
            for (String table : List.of("approvaltbl", "approval_linetbl", "approval_cctbl", "approval_historytbl")) {
                jdbc.queryForList("analyze table " + table);
            }
            for (long userId : new long[]{1, 10, 101}) {
                verifyResponses(userId);
                measure(documents, userId, report, raw);
            }
            writePlans(documents);
        }
        Files.writeString(OUTPUT.resolve("results.md"), report);
        Files.writeString(OUTPUT.resolve("samples.csv"), raw);
        StringBuilder schema = new StringBuilder();
        for (String table : List.of("usertbl", "approvaltbl", "approval_linetbl", "approval_cctbl", "approval_historytbl")) {
            schema.append(jdbc.queryForMap("show create table " + table).get("Create Table")).append(";\n\n");
        }
        Files.writeString(OUTPUT.resolve("schema.sql"), schema);
    }

    private Map<String, Supplier<?>> queries(long userId) {
        Map<String, Supplier<?>> queries = new LinkedHashMap<>();
        queries.put("pending", () -> approvals.findPendingByProcessorId(userId, java.time.LocalDate.now(), null, PageRequest.of(0, 4)));
        queries.put("sent", () -> approvals.findSentByWriterId(userId, null, ApprovalStatus.IN_PROGRESS, PageRequest.of(0, 1)));
        queries.put("drafts", () -> approvals.findDraftsByWriterId(userId, null, PageRequest.of(0, 1)));
        queries.put("cc", () -> approvals.findCcByUserId(userId, null, null, PageRequest.of(0, 1)));
        queries.put("activities", () -> approvals.findRecentActivitiesByUserId(userId, PageRequest.of(0, 5)));
        return queries;
    }

    private void measure(int documents, long userId, StringBuilder report, StringBuilder raw) {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        var queries = queries(userId);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        for (int i = 0; i < WARMUP; i++) {
            queries.values().forEach(query -> transaction.execute(ignored -> query.get()));
        }
        Map<String, long[]> samples = new LinkedHashMap<>();
        Map<String, Long> statementCounts = new LinkedHashMap<>();
        queries.keySet().forEach(name -> samples.put(name, new long[SAMPLES]));
        long[] bundleSamples = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long bundleNanos = 0;
            for (var entry : queries.entrySet()) {
                long before = statistics.getPrepareStatementCount();
                long start = System.nanoTime();
                transaction.execute(ignored -> entry.getValue().get());
                long elapsed = System.nanoTime() - start;
                long count = statistics.getPrepareStatementCount() - before;
                assertThat(count).as("SQL count: %s", entry.getKey()).isEqualTo(2);
                samples.get(entry.getKey())[i] = elapsed;
                statementCounts.put(entry.getKey(), count);
                bundleNanos += elapsed;
                raw.append(documents).append(',').append(userId).append(',').append(i).append(',')
                        .append(entry.getKey()).append(',').append(elapsed).append('\n');
            }
            bundleSamples[i] = bundleNanos;
        }
        samples.put("sequential-bundle", bundleSamples);
        statementCounts.put("sequential-bundle", 10L);
        samples.forEach((name, values) -> {
            long[] sorted = values.clone();
            Arrays.sort(sorted);
            report.append(String.format(Locale.ROOT, "| %d | %d | %d | %s | %d | %.3f | %.3f | 0 |%n",
                    documents, documents * 5, userId, name, statementCounts.get(name),
                    sorted[(int) Math.ceil(SAMPLES * 0.50) - 1] / 1_000_000.0,
                    sorted[(int) Math.ceil(SAMPLES * 0.95) - 1] / 1_000_000.0));
        });
        System.out.printf("Measured documents=%d user=%d (%d samples)%n", documents, userId, SAMPLES);
    }

    private void verifyResponses(long userId) {
        var pending = approvals.findPendingByProcessorId(userId, java.time.LocalDate.now(), null, PageRequest.of(0, 4));
        assertThat(pending.getTotalElements()).isEqualTo(jdbc.queryForObject("""
                select count(*) from approval_linetbl l join approvaltbl a using (approval_id)
                where l.approver_id=? and l.approval_status='PENDING' and a.approval_status='IN_PROGRESS'
                """, Long.class, userId));
        assertThat(pending.getContent()).extracting(row -> row.getApprovalId()).containsExactlyElementsOf(
                jdbc.queryForList("""
                        select a.approval_id from approval_linetbl l join approvaltbl a using (approval_id)
                        where l.approver_id=? and l.approval_status='PENDING' and a.approval_status='IN_PROGRESS'
                        order by a.created_at desc, a.approval_id desc limit 4
                        """, Long.class, userId));
        var sent = approvals.findSentByWriterId(userId, null, ApprovalStatus.IN_PROGRESS, PageRequest.of(0, 1));
        assertThat(sent.getTotalElements()).isEqualTo(jdbc.queryForObject(
                "select count(*) from approvaltbl where writer_id=? and approval_status='IN_PROGRESS'", Long.class, userId));
        assertThat(approvals.findDraftsByWriterId(userId, null, PageRequest.of(0, 1)).getTotalElements())
                .isEqualTo(jdbc.queryForObject("select count(*) from approvaltbl where writer_id=? and approval_status='DRAFT'", Long.class, userId));
        assertThat(approvals.findCcByUserId(userId, null, null, PageRequest.of(0, 1)).getTotalElements())
                .isEqualTo(jdbc.queryForObject("select count(*) from approval_cctbl where user_id=?", Long.class, userId));
        String accessible = """
                from approval_historytbl h join approvaltbl a using (approval_id)
                where (a.writer_id=? or (a.approval_status<>'DRAFT' and (
                exists(select 1 from approval_linetbl l where l.approval_id=a.approval_id and l.approver_id=?)
                or exists(select 1 from approval_cctbl c where c.approval_id=a.approval_id and c.user_id=?))))
                """;
        var activities = approvals.findRecentActivitiesByUserId(userId, PageRequest.of(0, 5));
        assertThat(activities.getTotalElements()).isEqualTo(jdbc.queryForObject(
                "select count(*) " + accessible, Long.class, userId, userId, userId));
        assertThat(activities.getContent()).extracting(row -> row.getApprovalHistoryId()).containsExactlyElementsOf(
                jdbc.queryForList("select h.approval_history_id " + accessible +
                        " order by h.created_at desc,h.approval_history_id desc limit 5", Long.class, userId, userId, userId));
    }

    private void writePlans(int documents) throws Exception {
        StringBuilder plans = new StringBuilder("Representative activity COUNT plan (same access predicate; no production-index claim)\n\n");
        for (long userId : new long[]{1, 10, 101}) {
            String sql = """
                    explain analyze select count(*) from approval_historytbl h
                    join approvaltbl a on a.approval_id=h.approval_id
                    where a.writer_id=? or (a.approval_status<>'DRAFT' and (
                    exists(select 1 from approval_linetbl l where l.approval_id=a.approval_id and l.approver_id=?)
                    or exists(select 1 from approval_cctbl c where c.approval_id=a.approval_id and c.user_id=?)))
                    """;
            plans.append("user=").append(userId).append('\n')
                    .append(jdbc.queryForObject(sql, String.class, userId, userId, userId)).append("\n\n");
        }
        Files.writeString(OUTPUT.resolve("plans-" + documents + ".txt"), plans);
    }
}
