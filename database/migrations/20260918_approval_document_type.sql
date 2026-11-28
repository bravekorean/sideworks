-- SideWorks 결재 문서 종류 1차 이관 (MySQL 8.0).
-- 2026-09-19 확인: approvaltbl.document_type VARCHAR(30), 기존 값은
-- GENERAL_PROPOSAL 9건 / ATTENDANCE_CORRECTION 2건, 마스터 테이블 없음.
-- 2026-09-19 사용자가 DB에 적용했고 기존 11건의 매핑 및 FK를 확인했다.
-- DDL은 자동 커밋되므로 새 환경에 적용할 때는 백업 후 1회만 실행한다.
-- 서버 코드가 아직 새 FK를 쓰지 않으므로 document_type_id는 NULL을 허용한다.
-- 기존 document_type, CHECK, 기본값은 이 단계에서 변경하거나 삭제하지 않는다.

CREATE TABLE approval_document_typetbl (
    approval_document_type_id BIGINT NOT NULL AUTO_INCREMENT,
    type_code VARCHAR(30) NOT NULL,
    type_name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    content_template LONGTEXT NULL,
    behavior_type VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    is_system BIT(1) NOT NULL DEFAULT b'0',
    active BIT(1) NOT NULL DEFAULT b'1',
    sort_order INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (approval_document_type_id),
    CONSTRAINT uk_approval_document_type_code UNIQUE (type_code),
    CONSTRAINT uk_approval_document_type_name UNIQUE (type_name),
    CONSTRAINT chk_approval_document_type_behavior
        CHECK (behavior_type IN (
            'GENERAL',
            'ATTENDANCE_CORRECTION',
            'LEAVE_REQUEST',
            'LEAVE_CANCELLATION'
        )),
    CONSTRAINT chk_approval_document_type_sort_order
        CHECK (sort_order >= 0),
    KEY idx_approval_document_type_active_sort
        (active, sort_order, approval_document_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO approval_document_typetbl
    (type_code, type_name, description, content_template, behavior_type, is_system, active, sort_order)
VALUES
    ('GENERAL_PROPOSAL', '품의서', '일반 결재 문서',
     CONCAT_WS(CHAR(10), '1. 요청 목적', '', '2. 요청 배경', '', '3. 요청 내용', '', '4. 기대 효과'),
     'GENERAL', b'0', b'1', 10),
    ('SUPPLY_PURCHASE', '비품 구매', '비품 구매 요청', NULL, 'GENERAL', b'0', b'1', 20),
    ('EXPENSE_SETTLEMENT', '비용 정산', '비용 정산 요청', NULL, 'GENERAL', b'0', b'1', 30),
    ('TRAINING_REQUEST', '교육 신청', '교육 신청', NULL, 'GENERAL', b'0', b'1', 40),
    ('WORK_REQUEST', '근무 신청', '근무 관련 신청', NULL, 'GENERAL', b'0', b'1', 50),
    ('ATTENDANCE_CORRECTION', '근태 정정', '근태 기록 정정 신청', NULL,
     'ATTENDANCE_CORRECTION', b'1', b'1', 60),
    ('LEAVE_REQUEST', '휴가 신청', '연차 및 반차 신청', NULL,
     'LEAVE_REQUEST', b'1', b'1', 70),
    ('LEAVE_CANCELLATION', '휴가 취소', '승인된 휴가 취소 신청', NULL,
     'LEAVE_CANCELLATION', b'1', b'1', 80);

ALTER TABLE approvaltbl
    ADD COLUMN document_type_id BIGINT NULL AFTER content;

UPDATE approvaltbl a
INNER JOIN approval_document_typetbl t
        ON t.type_code = a.document_type
SET a.document_type_id = t.approval_document_type_id
WHERE a.approval_id > 0
  AND a.document_type_id IS NULL;

ALTER TABLE approvaltbl
    ADD CONSTRAINT fk_approval_document_type
        FOREIGN KEY (document_type_id)
        REFERENCES approval_document_typetbl (approval_document_type_id),
    ADD KEY idx_approval_document_type_id_status
        (document_type_id, approval_status, approval_id);

-- 실행 후 확인: 이관 시점의 기존 11건은 모두 매핑되어야 한다.
-- 이후 구버전 서버가 만든 문서는 FK가 NULL일 수 있으므로 코드 전환 때 다시 보정한다.
SELECT a.document_type, COUNT(*) AS document_count,
       SUM(a.document_type_id IS NULL) AS unmapped_count
FROM approvaltbl a
GROUP BY a.document_type;
