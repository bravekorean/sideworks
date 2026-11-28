-- 기간제 대결: 원 결재선 보존 + 실제 처리자 기록으로 전환한다.
-- 검증: src/test/java/com/example/sideworks/approval/ApprovalDelegationIntegrationTest.java (마이그레이션 적용 시나리오 포함).
-- 선행 조건: 20261128_approval_dashboard_indexes.sql 적용 완료(idx_approval_linetbl_approver_processed가 있어야 한다).
-- 엔티티 ApprovalLine의 컬럼·@Index와 이름·컬럼 순서를 동일하게 유지한다.
--
-- 이전 방식은 단계가 활성화될 때 approver_id를 대결자로 바꾸고 원 결재자를 original_approver_id에 옮겼다.
-- 새 방식은 approver_id를 항상 원 결재자로 두고, 대기 단계의 처리자를 조회·처리 시점에 계산한다.
-- 실제로 승인·반려한 사람은 processed_by_id에 남긴다.
--
-- 반드시 백업 후 실행한다. 3) 이후는 되돌리려면 백업이 필요하다.
-- 애플리케이션은 이 파일 적용 후의 스키마로 기동해야 한다(ddl-auto=validate).

-- 1) 실제 처리자 컬럼과 FK. 새 컬럼은 모두 NULL이라 위반 행이 없으므로 FK 검사를 끄고 온라인으로 추가한다.
SET @previous_foreign_key_checks = @@foreign_key_checks;
SET foreign_key_checks = 0;
ALTER TABLE approval_linetbl
    ADD COLUMN processed_by_id BIGINT NULL,
    ADD CONSTRAINT fk_approval_linetbl_processed_by FOREIGN KEY (processed_by_id) REFERENCES usertbl (user_id),
    ALGORITHM = INPLACE, LOCK = NONE;
SET foreign_key_checks = @previous_foreign_key_checks;

-- 2) 이미 처리한 단계: 지금 approver_id에 있는 사람이 실제 처리자다(대결로 처리했다면 대결자).
UPDATE approval_linetbl
SET processed_by_id = approver_id
WHERE approval_status IN ('APPROVED', 'REJECTED')
  AND processed_by_id IS NULL;

-- 3) 모든 단계의 approver_id를 원 결재자로 되돌린다.
--    처리 전 단계는 이후 기간 규칙으로 다시 판단된다(기간 중이면 대결자, 끝났거나 취소됐으면 원 결재자).
--    이전 배정 규칙이 같은 문서의 다른 참여자를 대결자로 고르지 않았으므로 uk_approval_linetbl_approval_approver를 지킨다.
--    중복이 생기면 이 UPDATE 전체가 실패하고 아무 행도 바뀌지 않는다.
UPDATE approval_linetbl
SET approver_id = original_approver_id
WHERE original_approver_id IS NOT NULL;

-- 4) original_approver_id 제거. FK 이름은 환경마다 다르므로(Hibernate 생성 이름, 수동 생성 이름) 조회해서 지운다.
SET @original_approver_fk = (
    SELECT constraint_name
    FROM information_schema.key_column_usage
    WHERE table_schema = DATABASE()
      AND table_name = 'approval_linetbl'
      AND column_name = 'original_approver_id'
      AND referenced_table_name IS NOT NULL
    LIMIT 1
);
SET @drop_original_approver_fk = IF(@original_approver_fk IS NULL, 'DO 0',
    CONCAT('ALTER TABLE approval_linetbl DROP FOREIGN KEY `', @original_approver_fk, '`'));
PREPARE drop_original_approver_fk FROM @drop_original_approver_fk;
EXECUTE drop_original_approver_fk;
DEALLOCATE PREPARE drop_original_approver_fk;

ALTER TABLE approval_linetbl DROP COLUMN original_approver_id;

-- 5) 인덱스 교체.
--    대기함: approver_id(원 결재자 후보) + approval_status로 대기 단계를 찾는다. approver_id FK 인덱스 역할도 한다.
--    처리함: 실제 처리자 기준 최신순. 20261128 파일의 approver_id 기준 처리함 인덱스와 같은 모양으로 키만 바꾼다.
--    approver_id FK가 새 인덱스를 쓸 수 있도록 같은 ALTER에서 추가와 삭제를 함께 한다.
ALTER TABLE approval_linetbl
    ADD INDEX idx_approval_linetbl_approver_status (approver_id, approval_status),
    ADD INDEX idx_approval_linetbl_processor_processed
        (processed_by_id, processed_at DESC, approval_line_id DESC, approval_status, approval_id),
    DROP INDEX idx_approval_linetbl_approver_processed,
    ALGORITHM = INPLACE, LOCK = NONE;

-- 확인: 처리된 단계는 모두 실제 처리자가 있어야 하고(0), 대결 처리 건수와 인덱스 목록을 본다.
SELECT COUNT(*) AS processed_without_processor
FROM approval_linetbl
WHERE approval_status IN ('APPROVED', 'REJECTED') AND processed_by_id IS NULL;

SELECT COUNT(*) AS delegated_decisions
FROM approval_linetbl
WHERE processed_by_id IS NOT NULL AND processed_by_id <> approver_id;

SELECT index_name,
       GROUP_CONCAT(CONCAT(column_name, IF(collation = 'D', ' DESC', '')) ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'approval_linetbl'
GROUP BY index_name;
