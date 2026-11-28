CREATE TABLE work_policytbl (
    work_policy_id BIGINT NOT NULL AUTO_INCREMENT,
    policy_name VARCHAR(100) NOT NULL,
    work_start_time TIME NOT NULL,
    work_end_time TIME NOT NULL,
    break_start_time TIME NOT NULL,
    break_end_time TIME NOT NULL,
    late_grace_minutes INT UNSIGNED NOT NULL DEFAULT 0,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (work_policy_id),
    KEY idx_work_policy_effective_period (status, effective_from, effective_to),
    CONSTRAINT chk_work_policy_work_time
        CHECK (work_start_time < work_end_time),
    CONSTRAINT chk_work_policy_break_time
        CHECK (
            break_start_time < break_end_time
            AND break_start_time >= work_start_time
            AND break_end_time <= work_end_time
        ),
    CONSTRAINT chk_work_policy_late_grace
        CHECK (late_grace_minutes <= 1440),
    CONSTRAINT chk_work_policy_effective_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_work_policy_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='회사 근무정책';

CREATE TABLE attendancetbl (
    attendance_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    work_policy_id BIGINT NOT NULL,
    attendance_date DATE NOT NULL,
    check_in_at DATETIME(6) NOT NULL,
    check_out_at DATETIME(6) NULL,
    scheduled_check_in_at DATETIME(6) NOT NULL,
    scheduled_check_out_at DATETIME(6) NOT NULL,
    late_grace_minutes INT UNSIGNED NOT NULL,
    is_late TINYINT(1) NOT NULL DEFAULT 0,
    is_early_leave TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (attendance_id),
    UNIQUE KEY uk_attendance_user_date (user_id, attendance_date),
    KEY idx_attendance_work_policy (work_policy_id),
    CONSTRAINT fk_attendance_user
        FOREIGN KEY (user_id) REFERENCES usertbl (user_id),
    CONSTRAINT fk_attendance_work_policy
        FOREIGN KEY (work_policy_id) REFERENCES work_policytbl (work_policy_id),
    CONSTRAINT chk_attendance_scheduled_time
        CHECK (scheduled_check_in_at < scheduled_check_out_at),
    CONSTRAINT chk_attendance_check_time
        CHECK (check_out_at IS NULL OR check_out_at >= check_in_at),
    CONSTRAINT chk_attendance_late_grace
        CHECK (late_grace_minutes <= 1440),
    CONSTRAINT chk_attendance_is_late
        CHECK (is_late IN (0, 1)),
    CONSTRAINT chk_attendance_is_early_leave
        CHECK (is_early_leave IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='사용자별 일일 근태';

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


SHOW CREATE TABLE work_policytbl;
SHOW CREATE TABLE attendancetbl;

SELECT *
FROM work_policytbl;
