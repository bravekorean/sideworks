package com.example.sideworks;

import com.example.sideworks.approval.dto.ApprovalActivityResponse;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.support.MySqlTestcontainersConfig;
import com.example.sideworks.support.RecordingStatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET /api/approvals/activities의 깊은 페이지(offset+size > 100)가 최신 구간 탐색을 쓰든 UNION으로 돌아가든
 * 접근 조건을 그대로 적용한 기준 SQL과 같은 페이지·전체 건수를 돌려주는지 확인한다.
 * 실행 경로는 Hibernate 통계에서 최신 구간 SQL과 UNION 페이지 SQL의 실행 횟수로 구분한다.
 */
@ActiveProfiles("test")
@Import(MySqlTestcontainersConfig.class)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.example.sideworks.support.RecordingStatementInspector",
        "app.cors.allowed-origins=http://localhost:5173"
})
class RecentActivityDeepPageIntegrationTest {

    private static final int DOCUMENTS = 600;
    private static final int HISTORIES_PER_DOCUMENT = 3;
    private static final int PAGE_SIZE = 20;

    // 서비스 getRecentActivities와 같은 접근 조건(작성자, 상신 문서의 원 결재자·실제 처리자·참조자).
    private static final String REFERENCE_WHERE = """
            from approval_historytbl h join approvaltbl a on a.approval_id = h.approval_id
            where a.writer_id = ? or (a.approval_status <> 'DRAFT' and (
                exists (select 1 from approval_linetbl l where l.approval_id = a.approval_id
                        and (l.approver_id = ? or l.processed_by_id = ?))
                or exists (select 1 from approval_cctbl c where c.approval_id = a.approval_id and c.user_id = ?)))
            """;
    private static final String REFERENCE_PAGE = "select h.approval_history_id " + REFERENCE_WHERE
            + " order by h.created_at desc, h.approval_history_id desc limit ? offset ?";
    private static final String REFERENCE_COUNT = "select count(*) " + REFERENCE_WHERE;
    private static final String REFERENCE_FIELDS = """
            select h.approval_history_id, a.approval_id, a.title, u.user_id, u.user_name,
                   h.action_step, h.action_type, h.created_at
            """ + REFERENCE_WHERE.replace("join approvaltbl a on a.approval_id = h.approval_id",
                    "join approvaltbl a on a.approval_id = h.approval_id join usertbl u on u.user_id = h.actor_id")
            + " order by h.created_at desc, h.approval_history_id desc limit ? offset ?";

    private static final long DENSE_APPROVER = 1;
    private static final long WRITER = 2;
    private static final long SPARSE_CC_AND_DELEGATE = 3;
    private static final long NO_PARTICIPATION = 4;
    private static final long OTHER_WRITER = 5;

    @Autowired JdbcTemplate jdbc;
    @Autowired ApprovalRepository approvals;
    @Autowired PlatformTransactionManager transactionManager;

    private TransactionTemplate snapshot;

    @BeforeEach
    void seed() {
        snapshot = new TransactionTemplate(transactionManager);
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> seedDocuments());
    }

    @Test
    void everyPageMatchesTheReferenceQueryForEachParticipationPattern() {
        for (long user : new long[]{DENSE_APPROVER, WRITER, SPARSE_CC_AND_DELEGATE, NO_PARTICIPATION}) {
            long total = jdbc.queryForObject(REFERENCE_COUNT, Long.class, user, user, user, user);
            int lastPage = (int) ((total + PAGE_SIZE - 1) / PAGE_SIZE);
            for (int page = 0; page <= lastPage; page++) {
                assertPageMatchesReference(user, page);
            }
        }
    }

    @Test
    void denseUserDeepPageUsesTheWindowAndPartialLastPageGoesStraightToUnion() {
        long total = jdbc.queryForObject(REFERENCE_COUNT, Long.class,
                DENSE_APPROVER, DENSE_APPROVER, DENSE_APPROVER, DENSE_APPROVER);
        int lastPage = (int) ((total - 1) / PAGE_SIZE);
        assertThat(total % PAGE_SIZE).as("마지막 페이지가 꽉 차지 않아야 경계를 검증할 수 있다").isNotZero();

        assertThat(pathFor(DENSE_APPROVER, 10)).as("offset 200: 구간 사용").isEqualTo(RecentActivityListPath.WINDOW);
        assertThat(pathFor(DENSE_APPROVER, lastPage)).as("부분 페이지: 남은 건수가 부족해 구간 없이 바로 UNION")
                .isEqualTo(RecentActivityListPath.UNION);
        assertThat(pathFor(DENSE_APPROVER, lastPage + 1)).as("마지막 뒤: 목록 SQL 생략").isEqualTo(RecentActivityListPath.NONE);
    }

    @Test
    void deepPageSkipsTheWindowWhenTheEstimateExceedsTheCap() {
        // 최대 이력 ID가 실제 건수보다 훨씬 크면(ID 간격) 접근 비율이 낮게 추정돼 구간 상한(5,000)을 넘는다.
        // 이때는 구간을 읽지 않고 바로 UNION을 쓰며, 결과는 기준 SQL과 같아야 한다.
        jdbc.update("""
                insert into approval_historytbl(approval_history_id,approval_id,actor_id,action_step,action_type,created_at)
                values (100000, 1, ?, 9, 'SUBMITTED', '2026-01-01 09:00:00')
                """, OTHER_WRITER);
        for (long user : new long[]{DENSE_APPROVER, WRITER}) {
            for (int page : new int[]{5, 8}) {
                assertPageMatchesReference(user, page);
                assertThat(pathFor(user, page)).as("user=%d page=%d", user, page).isEqualTo(RecentActivityListPath.UNION);
            }
        }
    }

    @Test
    void fullPageMissingFromTheLatestWindowFallsBackToUnion() {
        // 사용자 6은 가장 오래된 문서 100건(이력 300건)의 작성자다. 전체로는 페이지를 채울 수 있지만
        // 추정 구간(최신 1,440건)에는 접근 가능한 이력이 없어 구간 탐색 후 UNION으로 다시 조회해야 한다.
        jdbc.update("""
                insert into usertbl(user_id,login_id,user_pass,user_name,employee_no,user_role,status,created_at,updated_at)
                values (6,'deep-page-6','not-a-login-credential','Deep page 6','DEEP-6','USER','ACTIVE','2026-01-01','2026-01-01')
                """);
        jdbc.update("update approvaltbl set writer_id = 6 where approval_id <= 100");
        jdbc.update("update approval_historytbl set created_at = '2025-12-01 09:00:00' where approval_id <= 100");
        long oldDocumentWriter = 6;

        assertThat(pathFor(oldDocumentWriter, 5)).isEqualTo(RecentActivityListPath.WINDOW_THEN_UNION);
        assertPageMatchesReference(oldDocumentWriter, 5);
        assertResponseFieldsMatchReference(oldDocumentWriter, 5);
    }

    @Test
    void deepPageResponseFieldsMatchTheReferenceOnEveryPath() {
        // 경로마다 별도 네이티브 매핑을 거치므로 제목·행위자·단계·종류·시각까지 비교한다.
        assertThat(pathFor(DENSE_APPROVER, 10)).isEqualTo(RecentActivityListPath.WINDOW);
        assertResponseFieldsMatchReference(DENSE_APPROVER, 10);
        long total = jdbc.queryForObject(REFERENCE_COUNT, Long.class,
                DENSE_APPROVER, DENSE_APPROVER, DENSE_APPROVER, DENSE_APPROVER);
        int lastPage = (int) ((total - 1) / PAGE_SIZE);
        assertThat(pathFor(DENSE_APPROVER, lastPage)).isEqualTo(RecentActivityListPath.UNION);
        assertResponseFieldsMatchReference(DENSE_APPROVER, lastPage);
    }

    private void assertResponseFieldsMatchReference(long user, int page) {
        Page<ApprovalActivityResponse> actual = snapshot.execute(ignored ->
                approvals.findRecentActivitiesByUserId(user, PageRequest.of(page, PAGE_SIZE)));
        List<String> expected = jdbc.query(REFERENCE_FIELDS, (rs, i) -> String.join("|",
                rs.getString("approval_history_id"), rs.getString("approval_id"), rs.getString("title"),
                rs.getString("user_id"), rs.getString("user_name"), rs.getString("action_step"),
                rs.getString("action_type"), rs.getTimestamp("created_at").toLocalDateTime().toString()),
                user, user, user, user, PAGE_SIZE, page * PAGE_SIZE);
        assertThat(expected).hasSize((int) Math.min(PAGE_SIZE, actual.getTotalElements() - (long) page * PAGE_SIZE));
        assertThat(actual.getContent()).extracting(a -> String.join("|",
                        String.valueOf(a.getApprovalHistoryId()), String.valueOf(a.getApprovalId()), a.getTitle(),
                        String.valueOf(a.getActorId()), a.getActorName(), String.valueOf(a.getActionStep()),
                        a.getActionType().name(), a.getCreatedAt().toString()))
                .as("user=%d page=%d", user, page)
                .containsExactlyElementsOf(expected);
    }

    private void assertPageMatchesReference(long user, int page) {
        Page<ApprovalActivityResponse> actual = snapshot.execute(ignored ->
                approvals.findRecentActivitiesByUserId(user, PageRequest.of(page, PAGE_SIZE)));
        List<Long> expected = jdbc.queryForList(REFERENCE_PAGE, Long.class,
                user, user, user, user, PAGE_SIZE, page * PAGE_SIZE);
        assertThat(actual.getContent()).extracting(ApprovalActivityResponse::getApprovalHistoryId)
                .as("user=%d page=%d", user, page)
                .containsExactlyElementsOf(expected);
        assertThat(actual.getTotalElements())
                .isEqualTo(jdbc.queryForObject(REFERENCE_COUNT, Long.class, user, user, user, user));
    }

    private RecentActivityListPath pathFor(long user, int page) {
        RecordingStatementInspector.start();
        try {
            snapshot.executeWithoutResult(ignored ->
                    approvals.findRecentActivitiesByUserId(user, PageRequest.of(page, PAGE_SIZE)));
        } catch (RuntimeException failure) {
            RecordingStatementInspector.stop();
            throw failure;
        }
        return RecentActivityListPath.of(RecordingStatementInspector.stop());
    }

    /**
     * 문서 600건, 이력 1,800건. 생성 시각은 7개 값만 써서 같은 시각의 이력이 많고, 시각 순서가 ID 순서와 다르다.
     * 사용자 1은 대부분 문서의 결재자지만 다른 사람의 임시저장 문서는 볼 수 없다.
     * 사용자 3은 일부 문서의 참조자이거나 대결 처리자(processed_by)다.
     */
    private void seedDocuments() {
        for (String table : List.of("approval_historytbl", "approval_cctbl", "approval_linetbl", "approvaltbl", "usertbl")) {
            jdbc.update("delete from " + table);
        }
        List<Object[]> users = new ArrayList<>();
        for (long id = 1; id <= 5; id++) {
            users.add(new Object[]{id, "deep-page-" + id, "Deep page " + id, "DEEP-" + id});
        }
        jdbc.batchUpdate("""
                insert into usertbl(user_id,login_id,user_pass,user_name,employee_no,user_role,status,created_at,updated_at)
                values (?,?,'not-a-login-credential',?,?,'USER','ACTIVE','2026-01-01','2026-01-01')
                """, users);
        List<Object[]> docs = new ArrayList<>();
        List<Object[]> lines = new ArrayList<>();
        List<Object[]> ccs = new ArrayList<>();
        List<Object[]> histories = new ArrayList<>();
        for (int i = 1; i <= DOCUMENTS; i++) {
            long writer = i % 10 == 0 ? WRITER : OTHER_WRITER;
            String status = i % 9 == 0 ? "DRAFT" : (i % 2 == 0 ? "IN_PROGRESS" : "APPROVED");
            docs.add(new Object[]{i, writer, "Deep page document " + i, status, status.equals("DRAFT") ? null : 1});
            if (!status.equals("DRAFT") || i % 4 == 0) {
                // 임시저장 문서에도 결재선이 있어 "상신 문서만" 조건을 검증한다.
                boolean delegated = i % 37 == 0;
                long approver = i % 6 == 5 ? OTHER_WRITER : DENSE_APPROVER;
                lines.add(new Object[]{i, approver, delegated ? SPARSE_CC_AND_DELEGATE : null,
                        status.equals("APPROVED") ? "APPROVED" : "PENDING"});
            }
            if (i % 23 == 0) {
                ccs.add(new Object[]{i, SPARSE_CC_AND_DELEGATE});
            }
            String createdAt = "2026-01-0" + (1 + (i * 3) % 7) + " 09:00:00";
            for (int h = 0; h < HISTORIES_PER_DOCUMENT; h++) {
                histories.add(new Object[]{(long) (i - 1) * HISTORIES_PER_DOCUMENT + h + 1, i, writer, h + 1, createdAt});
            }
        }
        jdbc.batchUpdate("""
                insert into approvaltbl(approval_id,writer_id,title,content,approval_status,current_step,document_type,created_at,updated_at,submitted_at)
                values (?,?,?,'Synthetic content',?,?,'GENERAL_PROPOSAL','2026-01-01','2026-01-01','2026-01-01')
                """, docs);
        jdbc.batchUpdate("""
                insert into approval_linetbl(approval_id,approver_id,processed_by_id,approval_step,approval_status,created_at)
                values (?,?,?,1,?,'2026-01-01')
                """, lines);
        jdbc.batchUpdate("insert into approval_cctbl(approval_id,user_id,created_at) values (?,?,'2026-01-01')", ccs);
        jdbc.batchUpdate("""
                insert into approval_historytbl(approval_history_id,approval_id,actor_id,action_step,action_type,created_at)
                values (?,?,?,?,'SUBMITTED',?)
                """, histories);
    }
}
