CREATE TABLE work_policy_daytbl (
    work_policy_day_id BIGINT NOT NULL AUTO_INCREMENT,
    work_policy_id BIGINT NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    is_working TINYINT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (work_policy_day_id),
    UNIQUE KEY uk_work_policy_day (work_policy_id, day_of_week),
    CONSTRAINT fk_work_policy_day_policy
        FOREIGN KEY (work_policy_id) REFERENCES work_policytbl (work_policy_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_work_policy_day_of_week
        CHECK (
            day_of_week IN (
                'MONDAY',
                'TUESDAY',
                'WEDNESDAY',
                'THURSDAY',
                'FRIDAY',
                'SATURDAY',
                'SUNDAY'
            )
        ),
    CONSTRAINT chk_work_policy_day_is_working
        CHECK (is_working IN (0, 1))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='근무정책별 요일 규칙';

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
