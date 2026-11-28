-- 결재 처리함과 대시보드 최근 활동 조회용 인덱스.
-- 근거: PERFORMANCE.md "결재 처리함의 최신순 정렬에 맞춘 인덱스", "최근 활동의 사용자 분포에 따른 SQL 회귀".
-- 엔티티 @Index(ApprovalLine, ApprovalHistory)와 이름·컬럼 순서를 동일하게 유지한다.
--
-- 1) 처리함: approver_id 범위 안에서 processed_at DESC 순서로 읽어 첫 페이지의 filesort를 없앤다.
--    approver_id로 시작하므로 FK가 MySQL이 암묵적으로 만든 approver_id 인덱스는 자동으로 대체될 수 있다.
-- 2) 최근 활동: 전체 이력의 최신 100건을 인덱스 순서로 읽는다.
--
-- InnoDB 보조 인덱스 추가는 온라인 DDL로 수행되며 쓰기를 막지 않는다.

ALTER TABLE approval_linetbl
    ADD INDEX idx_approval_linetbl_approver_processed
        (approver_id, processed_at DESC, approval_line_id DESC, approval_status, approval_id),
    ALGORITHM = INPLACE, LOCK = NONE;

ALTER TABLE approval_historytbl
    ADD INDEX idx_approval_historytbl_created
        (created_at DESC, approval_history_id DESC),
    ALGORITHM = INPLACE, LOCK = NONE;

SELECT table_name, index_name,
       GROUP_CONCAT(CONCAT(column_name, IF(collation = 'D', ' DESC', '')) ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND index_name IN ('idx_approval_linetbl_approver_processed', 'idx_approval_historytbl_created')
GROUP BY table_name, index_name;
