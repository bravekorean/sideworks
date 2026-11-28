-- 사용자가 직접 적용하는 MySQL 8.0 마이그레이션. 애플리케이션 중지 및 백업 후 1회 실행.
-- DDL은 자동 커밋된다. 일부 성공 후 재실행하지 말고 SHOW CREATE TABLE로 적용 범위를 확인할 것.
-- 기존 출퇴근 기록은 변경/삭제하지 않으며 version 초기값만 추가한다.
ALTER TABLE attendancetbl ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE work_schedule_exceptiontbl (
    work_schedule_exception_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    exception_date DATE NOT NULL,
    name VARCHAR(100) NOT NULL,
    working BIT NOT NULL,
    active BIT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_schedule_exception_date (exception_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE attendance_correctiontbl (
    attendance_correction_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    approval_id BIGINT NOT NULL,
    attendance_date DATE NOT NULL,
    pending_date DATE NULL,
    original_attendance_id BIGINT NULL,
    original_version BIGINT NULL,
    original_check_in_at DATETIME(6) NULL,
    original_check_out_at DATETIME(6) NULL,
    requested_check_in_at DATETIME(6) NOT NULL,
    requested_check_out_at DATETIME(6) NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_correction_approval (approval_id),
    UNIQUE KEY uk_correction_pending (user_id, pending_date),
    KEY idx_correction_user_date (user_id, attendance_date),
    CONSTRAINT fk_correction_user FOREIGN KEY (user_id) REFERENCES usertbl(user_id),
    CONSTRAINT fk_correction_approval FOREIGN KEY (approval_id) REFERENCES approvaltbl(approval_id),
    CONSTRAINT chk_correction_pending CHECK (pending_date IS NULL OR pending_date = attendance_date),
    CONSTRAINT chk_correction_time CHECK (DATE(requested_check_in_at) = attendance_date
        AND (requested_check_out_at IS NULL OR (DATE(requested_check_out_at) = attendance_date
        AND requested_check_out_at >= requested_check_in_at)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE attendance_change_historytbl (
    attendance_change_history_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    approval_id BIGINT NULL,
    source VARCHAR(30) NOT NULL,
    attendance_date DATE NOT NULL,
    before_check_in_at DATETIME(6) NULL,
    before_check_out_at DATETIME(6) NULL,
    before_late BIT NULL,
    before_early_leave BIT NULL,
    after_check_in_at DATETIME(6) NOT NULL,
    after_check_out_at DATETIME(6) NULL,
    after_late BIT NOT NULL,
    after_early_leave BIT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_attendance_history_user_date (user_id, attendance_date, attendance_change_history_id),
    CONSTRAINT fk_attendance_history_user FOREIGN KEY (user_id) REFERENCES usertbl(user_id),
    CONSTRAINT fk_attendance_history_actor FOREIGN KEY (actor_id) REFERENCES usertbl(user_id),
    CONSTRAINT fk_attendance_history_approval FOREIGN KEY (approval_id) REFERENCES approvaltbl(approval_id),
    CONSTRAINT chk_attendance_history_source CHECK (
        (source = 'APPROVAL' AND approval_id IS NOT NULL)
        OR (source = 'SUPER_ADMIN_DIRECT' AND approval_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
