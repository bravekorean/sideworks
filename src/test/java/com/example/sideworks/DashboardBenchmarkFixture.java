package com.example.sideworks;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/** Deterministic read-load data shared by baseline and comparison measurements. */
final class DashboardBenchmarkFixture {
    private DashboardBenchmarkFixture() {}

    static void seed(JdbcTemplate jdbc, int documents) {
        // Only this new container is ever written; deterministic IDs allow exact reproduction.
        for (String table : List.of("approval_historytbl", "approval_cctbl", "approval_linetbl", "approvaltbl", "usertbl")) {
            jdbc.update("delete from " + table);
        }
        List<Object[]> users = new ArrayList<>();
        for (int i = 1; i <= 101; i++) users.add(new Object[]{i, "bench-" + i, "Bench " + i, "BENCH-" + i});
        jdbc.batchUpdate("""
                insert into usertbl(user_id,login_id,user_pass,user_name,employee_no,user_role,status,created_at,updated_at)
                values (?,?,'not-a-login-credential',?,?,'USER','ACTIVE','2026-01-01','2026-01-01')
                """, users);
        for (int start = 1; start <= documents; start += 500) {
            List<Object[]> docs = new ArrayList<>();
            List<Object[]> lines = new ArrayList<>();
            List<Object[]> ccs = new ArrayList<>();
            List<Object[]> histories = new ArrayList<>();
            for (int i = start; i < start + 500 && i <= documents; i++) {
                long writer = i % 10 < 4 ? 1 : 3 + i % 98;
                String status = switch (i % 3) { case 0 -> "DRAFT"; case 1 -> "IN_PROGRESS"; default -> "APPROVED"; };
                docs.add(new Object[]{i, writer, "Synthetic document " + i, status, status.equals("DRAFT") ? null : 1});
                if (!status.equals("DRAFT")) {
                    long approver = writer != 1 && i % 4 == 0 ? 1 : 2;
                    lines.add(new Object[]{i, approver, status.equals("IN_PROGRESS") ? "PENDING" : "APPROVED"});
                    ccs.add(new Object[]{i, writer != 1 && i % 5 == 0 ? 1 : 2});
                }
                // Five records per document, including edit/resubmission-like accumulated history.
                for (int h = 0; h < 5; h++) histories.add(new Object[]{i, writer, h + 1});
            }
            jdbc.batchUpdate("""
                    insert into approvaltbl(approval_id,writer_id,title,content,approval_status,current_step,document_type,created_at,updated_at,submitted_at)
                    values (?,?,?,'Synthetic content',?,?,'GENERAL_PROPOSAL','2026-01-01','2026-01-01','2026-01-01')
                    """, docs);
            jdbc.batchUpdate("""
                    insert into approval_linetbl(approval_id,approver_id,approval_step,approval_status,created_at)
                    values (?,?,1,?,'2026-01-01')
                    """, lines);
            jdbc.batchUpdate("insert into approval_cctbl(approval_id,user_id,created_at) values (?,?,'2026-01-01')", ccs);
            jdbc.batchUpdate("""
                    insert into approval_historytbl(approval_id,actor_id,action_step,action_type,created_at)
                    values (?,?,?,'SUBMITTED','2026-01-01')
                    """, histories);
        }
    }

}
