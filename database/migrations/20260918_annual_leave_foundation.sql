-- SideWorks 연차·휴가 MVP용 MySQL 8.0 스키마.
-- 사용자가 직접 적용하는 로컬 마이그레이션이며 database/는 Git 추적 대상이 아니다.
-- 애플리케이션 중지 및 백업 후 1회 실행한다. MySQL DDL은 자동 커밋되므로
-- 일부 성공 후에는 재실행하지 말고 SHOW CREATE TABLE로 적용 범위를 확인한다.

CREATE TABLE annual_leave_balancetbl (
    annual_leave_balance_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    leave_year INT NOT NULL,
    granted_days DECIMAL(4,1) NOT NULL,
    remaining_days DECIMAL(4,1) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_annual_leave_balance_user_year (user_id, leave_year),
    KEY idx_annual_leave_balance_year (leave_year, user_id),
    CONSTRAINT fk_annual_leave_balance_user
        FOREIGN KEY (user_id) REFERENCES usertbl(user_id),
    CONSTRAINT chk_annual_leave_balance_year
        CHECK (leave_year BETWEEN 2000 AND 9999),
    CONSTRAINT chk_annual_leave_balance_granted
        CHECK (granted_days > 0.0 AND granted_days <= 25.0
            AND MOD(granted_days * 10, 5) = 0),
    CONSTRAINT chk_annual_leave_balance_remaining
        CHECK (remaining_days >= 0.0 AND remaining_days <= granted_days
            AND MOD(remaining_days * 10, 5) = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE leave_requesttbl (
    leave_request_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    approval_id BIGINT NOT NULL,
    balance_id BIGINT NOT NULL,
    total_days DECIMAL(4,1) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    canceled_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_leave_request_approval (approval_id),
    KEY idx_leave_request_user (user_id, leave_request_id),
    KEY idx_leave_request_balance (balance_id, leave_request_id),
    CONSTRAINT fk_leave_request_user
        FOREIGN KEY (user_id) REFERENCES usertbl(user_id),
    CONSTRAINT fk_leave_request_approval
        FOREIGN KEY (approval_id) REFERENCES approvaltbl(approval_id),
    CONSTRAINT fk_leave_request_balance
        FOREIGN KEY (balance_id) REFERENCES annual_leave_balancetbl(annual_leave_balance_id),
    CONSTRAINT chk_leave_request_total_days
        CHECK (total_days > 0.0 AND total_days <= 25.0
            AND MOD(total_days * 10, 5) = 0),
    CONSTRAINT chk_leave_request_reason
        CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 1000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE leave_request_daytbl (
    leave_request_day_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    leave_request_id BIGINT NOT NULL,
    leave_date DATE NOT NULL,
    leave_period VARCHAR(10) NOT NULL,
    days DECIMAL(2,1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_leave_request_day (leave_request_id, leave_date),
    KEY idx_leave_request_day_date (leave_date, leave_request_id),
    CONSTRAINT fk_leave_request_day_request
        FOREIGN KEY (leave_request_id) REFERENCES leave_requesttbl(leave_request_id),
    CONSTRAINT chk_leave_request_day_period
        CHECK (leave_period IN ('FULL', 'AM', 'PM')),
    CONSTRAINT chk_leave_request_day_amount
        CHECK ((leave_period = 'FULL' AND days = 1.0)
            OR (leave_period IN ('AM', 'PM') AND days = 0.5))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE leave_cancellationtbl (
    leave_cancellation_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    leave_request_id BIGINT NOT NULL,
    approval_id BIGINT NOT NULL,
    pending_leave_request_id BIGINT NULL,
    reason VARCHAR(1000) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_leave_cancellation_approval (approval_id),
    UNIQUE KEY uk_leave_cancellation_pending (pending_leave_request_id),
    KEY idx_leave_cancellation_request (leave_request_id, leave_cancellation_id),
    CONSTRAINT fk_leave_cancellation_request
        FOREIGN KEY (leave_request_id) REFERENCES leave_requesttbl(leave_request_id),
    CONSTRAINT fk_leave_cancellation_approval
        FOREIGN KEY (approval_id) REFERENCES approvaltbl(approval_id),
    CONSTRAINT fk_leave_cancellation_pending_request
        FOREIGN KEY (pending_leave_request_id) REFERENCES leave_requesttbl(leave_request_id),
    CONSTRAINT chk_leave_cancellation_pending
        CHECK (pending_leave_request_id IS NULL OR pending_leave_request_id = leave_request_id),
    CONSTRAINT chk_leave_cancellation_reason
        CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 1000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE annual_leave_historytbl (
    annual_leave_history_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    balance_id BIGINT NOT NULL,
    leave_request_id BIGINT NULL,
    cancellation_id BIGINT NULL,
    actor_id BIGINT NULL,
    action_type VARCHAR(20) NOT NULL,
    days_delta DECIMAL(4,1) NOT NULL,
    balance_after DECIMAL(4,1) NOT NULL,
    source_key VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_annual_leave_history_source (source_key),
    KEY idx_annual_leave_history_balance (balance_id, annual_leave_history_id),
    KEY idx_annual_leave_history_request (leave_request_id),
    KEY idx_annual_leave_history_cancellation (cancellation_id),
    CONSTRAINT fk_annual_leave_history_balance
        FOREIGN KEY (balance_id) REFERENCES annual_leave_balancetbl(annual_leave_balance_id),
    CONSTRAINT fk_annual_leave_history_request
        FOREIGN KEY (leave_request_id) REFERENCES leave_requesttbl(leave_request_id),
    CONSTRAINT fk_annual_leave_history_cancellation
        FOREIGN KEY (cancellation_id) REFERENCES leave_cancellationtbl(leave_cancellation_id),
    CONSTRAINT fk_annual_leave_history_actor
        FOREIGN KEY (actor_id) REFERENCES usertbl(user_id),
    CONSTRAINT chk_annual_leave_history_type
        CHECK (action_type IN ('GRANT', 'USE', 'RESTORE')),
    CONSTRAINT chk_annual_leave_history_delta
        CHECK (days_delta <> 0.0 AND MOD(ABS(days_delta) * 10, 5) = 0
            AND ((action_type = 'USE' AND days_delta < 0.0)
                OR (action_type IN ('GRANT', 'RESTORE') AND days_delta > 0.0))),
    CONSTRAINT chk_annual_leave_history_balance_after
        CHECK (balance_after >= 0.0 AND balance_after <= 25.0
            AND MOD(balance_after * 10, 5) = 0),
    CONSTRAINT chk_annual_leave_history_reference
        CHECK ((action_type = 'GRANT'
                AND leave_request_id IS NULL AND cancellation_id IS NULL)
            OR (action_type = 'USE'
                AND leave_request_id IS NOT NULL AND cancellation_id IS NULL)
            OR (action_type = 'RESTORE'
                AND leave_request_id IS NOT NULL AND cancellation_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
