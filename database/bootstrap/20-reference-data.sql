-- Reference rows for a new demo database, based on the tracked migrations.
SET NAMES utf8mb4;

INSERT INTO work_policytbl (
    policy_name,
    work_start_time,
    work_end_time,
    break_start_time,
    break_end_time,
    late_grace_minutes,
    effective_from,
    effective_to,
    status,
    created_at,
    updated_at
) VALUES (
    '기본 근무정책',
    '09:00:00',
    '18:00:00',
    '12:00:00',
    '13:00:00',
    0,
    '2026-01-01',
    NULL,
    'ACTIVE',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
);

INSERT INTO work_policy_daytbl (
    work_policy_id,
    day_of_week,
    is_working,
    created_at,
    updated_at
)
SELECT
    policy.work_policy_id,
    weekdays.day_of_week,
    weekdays.is_working,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM (
    SELECT work_policy_id
    FROM work_policytbl
    WHERE policy_name = '기본 근무정책'
      AND effective_from = '2026-01-01'
      AND status = 'ACTIVE'
    ORDER BY work_policy_id
    LIMIT 1
) AS policy
CROSS JOIN (
    SELECT 1 AS sort_order, 'MONDAY' AS day_of_week, 1 AS is_working
    UNION ALL SELECT 2, 'TUESDAY', 1
    UNION ALL SELECT 3, 'WEDNESDAY', 1
    UNION ALL SELECT 4, 'THURSDAY', 1
    UNION ALL SELECT 5, 'FRIDAY', 1
    UNION ALL SELECT 6, 'SATURDAY', 0
    UNION ALL SELECT 7, 'SUNDAY', 0
) AS weekdays
ORDER BY weekdays.sort_order;

INSERT INTO approval_document_typetbl
    (type_code, type_name, description, content_template, behavior_type, is_system, active, sort_order, version, created_at, updated_at)
VALUES
    ('GENERAL_PROPOSAL', '품의서', '일반 결재 문서',
     CONCAT_WS(CHAR(10), '1. 요청 목적', '', '2. 요청 배경', '', '3. 요청 내용', '', '4. 기대 효과'),
     'GENERAL', b'0', b'1', 10, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('SUPPLY_PURCHASE', '비품 구매', '비품 구매 요청', NULL, 'GENERAL', b'0', b'1', 20, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('EXPENSE_SETTLEMENT', '비용 정산', '비용 정산 요청', NULL, 'GENERAL', b'0', b'1', 30, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('TRAINING_REQUEST', '교육 신청', '교육 신청', NULL, 'GENERAL', b'0', b'1', 40, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('WORK_REQUEST', '근무 신청', '근무 관련 신청', NULL, 'GENERAL', b'0', b'1', 50, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('ATTENDANCE_CORRECTION', '근태 정정', '근태 기록 정정 신청', NULL,
     'ATTENDANCE_CORRECTION', b'1', b'1', 60, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('LEAVE_REQUEST', '휴가 신청', '연차 및 반차 신청', NULL,
     'LEAVE_REQUEST', b'1', b'0', 70, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('LEAVE_CANCELLATION', '휴가 취소', '승인된 휴가 취소 신청', NULL,
     'LEAVE_CANCELLATION', b'1', b'0', 80, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
