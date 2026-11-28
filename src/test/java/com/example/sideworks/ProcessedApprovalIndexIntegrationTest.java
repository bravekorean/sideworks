package com.example.sideworks;

import com.example.sideworks.approval.dto.ApprovalListResponse;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.approval.repository.ApprovalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
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

@EnabledIfEnvironmentVariable(named = "SIDEWORKS_PROCESSED_INDEX_BENCHMARK", matches = "true")
@DataJpaTest(showSql = false, properties = {
        "spring.config.location=optional:classpath:/dashboard-benchmark-empty.properties",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.level.root=WARN",
        "logging.level.org.hibernate.SQL=OFF"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = DashboardBaselineIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProcessedApprovalIndexIntegrationTest {
    private static final int PAGE_SIZE = 20;
    private static final int SAMPLES = 30;
    private static final String STATUS_INDEX = "bench_processed_status_first";
    private static final String TIME_INDEX = "bench_processed_time_first";
    private static final String STATUS_COLUMNS = "approver_id, approval_status, processed_at DESC, approval_line_id DESC, approval_id";
    private static final String TIME_COLUMNS = "approver_id, processed_at DESC, approval_line_id DESC, approval_status, approval_id";
    private static final String PROJECTION = """
            select a.approval_id, a.title, u.user_id, u.user_name, a.approval_status,
                   a.current_step, a.created_at, a.submitted_at, a.completed_at
            """;
    private static final String BODY = """
            join approvaltbl a on a.approval_id=l.approval_id
            join usertbl u on u.user_id=a.writer_id
            where l.approver_id=? and l.approval_status in ('APPROVED','REJECTED')
            """;
    private static final String ORDER = " order by l.processed_at desc,l.approval_line_id desc limit ? offset ?";

    private enum Variant {
        BASELINE(""),
        STATUS_FIRST("force index (" + STATUS_INDEX + ")"),
        TIME_FIRST("force index (" + TIME_INDEX + ")");

        final String hint;

        Variant(String hint) { this.hint = hint; }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ApprovalRepository approvals;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void compareProcessedInboxIndexes() throws Exception {
        seed();
        Path directory = Path.of("build/reports/processed-approval-index");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("baseline-schema.sql"), schema());
        StringBuilder report = new StringBuilder("# Processed approval inbox index comparison\n\n")
                .append("MySQL ").append(jdbc.queryForObject("select version()", String.class))
                .append("; 60,000 documents, 20 items per page, 8 warmup and 30 timed calls per variant/scenario.\n")
                .append("JDBC execution + mapping, no keyword/document status filter in timed cases.\n")
                .append("Natural plan on baseline schema; forced candidate paths while only one test index exists.\n\n")
                .append("| user | matched | query | offset | variant | p50 ms | p95 ms |\n")
                .append("|---:|---:|---|---:|---|---:|---:|\n");
        StringBuilder raw = new StringBuilder("user,query,offset,variant,sample,nanos\n");
        for (Variant variant : Variant.values()) {
            switch (variant) {
                case BASELINE -> { }
                case STATUS_FIRST -> jdbc.execute("create index " + STATUS_INDEX + " on approval_linetbl (" + STATUS_COLUMNS + ")");
                case TIME_FIRST -> {
                    // InnoDB can replace the implicit FK index with the first replacement.
                    jdbc.execute("create index " + TIME_INDEX + " on approval_linetbl (" + TIME_COLUMNS + ")");
                    jdbc.execute("drop index " + STATUS_INDEX + " on approval_linetbl");
                }
            }
            jdbc.queryForList("analyze table approval_linetbl");
            StringBuilder plans = new StringBuilder();
            for (long user : new long[]{1, 2, 101}) {
                long matched = count(Variant.BASELINE, user, null);
                int deepPage = (int) ((matched * 3 / 4) / PAGE_SIZE);
                verifyRepository(variant, user, 0, null);
                verifyRepository(variant, user, deepPage, null);
                verifyRepository(variant, user, 0, ApprovalStatus.REJECTED);
                for (int page : deepPage == 0 ? new int[]{0} : new int[]{0, deepPage}) {
                    int offset = page * PAGE_SIZE;
                    measure(report, raw, user, matched, "list", offset, variant, () -> list(variant, user, offset, null));
                    recordPlan(plans, "list", variant, user, offset, false);
                    if (variant != Variant.BASELINE) recordPlan(plans, "list", Variant.BASELINE, user, offset, false);
                }
                measure(report, raw, user, matched, "count", 0, variant, () -> count(variant, user, null));
                recordPlan(plans, "count", variant, user, 0, true);
                if (variant != Variant.BASELINE) recordPlan(plans, "count", Variant.BASELINE, user, 0, true);
            }
            Files.writeString(directory.resolve("plans-" + variant + ".txt"), plans);
        }
        Files.writeString(directory.resolve("results.md"), report);
        Files.writeString(directory.resolve("samples.csv"), raw);
    }

    private void seed() {
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            DashboardBenchmarkFixture.seed(jdbc, 60_000);
            jdbc.update("""
                    update approval_linetbl l join approvaltbl a on a.approval_id=l.approval_id
                    set l.processed_at=timestampadd(second,floor(l.approval_id/10),'2026-01-01 00:00:00'),
                        l.approval_status=if(mod(l.approval_line_id,3)=0,'REJECTED','APPROVED'),
                        a.approval_status=if(mod(l.approval_line_id,3)=0,'REJECTED','APPROVED')
                    where l.approval_status='APPROVED'
                    """);
        });
        restorePreIndexBaseline();
        jdbc.queryForList("analyze table approvaltbl, usertbl");
    }

    // The entity now declares the time-first index. Recreate the earlier schema so the
    // baseline keeps measuring the approver FK index alone; the FK needs an index before the drop.
    private void restorePreIndexBaseline() {
        jdbc.execute("create index bench_baseline_approver on approval_linetbl (approver_id)");
        jdbc.execute("drop index idx_approval_linetbl_approver_processed on approval_linetbl");
    }

    private void verifyRepository(Variant variant, long user, int page, ApprovalStatus status) {
        int offset = page * PAGE_SIZE;
        var expected = approvals.findProcessedByApproverId(user, null, status, PageRequest.of(page, PAGE_SIZE));
        var rows = list(variant, user, offset, status);
        assertThat(rows).isEqualTo(list(Variant.BASELINE, user, offset, status));
        assertThat(rows.stream().map(row -> ((Number) row.get("approval_id")).longValue()).toList())
                .containsExactlyElementsOf(expected.getContent().stream().map(ApprovalListResponse::getApprovalId).toList());
        assertThat(count(variant, user, status)).isEqualTo(expected.getTotalElements());
    }

    private String sql(Variant variant, boolean count, ApprovalStatus status) {
        return (count ? "select count(l.approval_line_id)\n" : PROJECTION)
                + " from approval_linetbl l " + variant.hint + " " + BODY
                + (status == null ? "" : " and a.approval_status=?") + (count ? "" : ORDER);
    }

    private List<Map<String, Object>> list(Variant variant, long user, int offset, ApprovalStatus status) {
        Object[] params = status == null ? new Object[]{user, PAGE_SIZE, offset}
                : new Object[]{user, status.name(), PAGE_SIZE, offset};
        return jdbc.queryForList(sql(variant, false, status), params);
    }

    private long count(Variant variant, long user, ApprovalStatus status) {
        Object[] params = status == null ? new Object[]{user} : new Object[]{user, status.name()};
        return jdbc.queryForObject(sql(variant, true, status), Long.class, params);
    }

    private void recordPlan(StringBuilder plans, String query, Variant path, long user, int offset, boolean count) {
        Object[] params = count ? new Object[]{user} : new Object[]{user, PAGE_SIZE, offset};
        plans.append("user=").append(user).append(" query=").append(query).append(" offset=").append(offset)
                .append(" path=").append(path == Variant.BASELINE ? "natural" : path).append('\n')
                .append(jdbc.queryForObject("explain analyze " + sql(path, count, null), String.class, params)).append("\n\n");
    }

    private void measure(StringBuilder report, StringBuilder raw, long user, long matched,
                         String query, int offset, Variant variant, Runnable call) {
        for (int i = 0; i < 8; i++) call.run();
        long[] times = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long start = System.nanoTime();
            call.run();
            times[i] = System.nanoTime() - start;
            raw.append(user).append(',').append(query).append(',').append(offset).append(',').append(variant)
                    .append(',').append(i).append(',').append(times[i]).append('\n');
        }
        Arrays.sort(times);
        report.append(String.format(Locale.ROOT, "| %d | %d | %s | %d | %s | %.3f | %.3f |%n",
                user, matched, query, offset, variant, times[14] / 1e6, times[28] / 1e6));
    }

    private String schema() {
        return (String) jdbc.queryForMap("show create table approval_linetbl").get("Create Table");
    }
}
