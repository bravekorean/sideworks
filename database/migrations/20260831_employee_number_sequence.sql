ALTER TABLE usertbl
    ADD COLUMN job_family VARCHAR(20) NULL COMMENT '직렬(TECHNICAL, CORPORATE)' AFTER employee_no,
    ADD COLUMN hire_date DATE NULL COMMENT '입사일' AFTER job_family;

CREATE TABLE employee_number_sequencetbl (
    employee_number_sequence_id BIGINT NOT NULL AUTO_INCREMENT,
    job_family VARCHAR(20) NOT NULL,
    hire_year INT NOT NULL,
    last_sequence INT NOT NULL,
    PRIMARY KEY (employee_number_sequence_id),
    UNIQUE KEY uk_employee_number_sequence_family_year (job_family, hire_year),
    CONSTRAINT chk_employee_number_sequence_positive CHECK (last_sequence > 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='직렬·입사연도별 사번 시퀀스';

UPDATE usertbl
SET job_family = CASE
        WHEN employee_no LIKE 'TC-%' THEN 'TECHNICAL'
        WHEN employee_no LIKE 'CP-%' THEN 'CORPORATE'
    END,
    hire_date = STR_TO_DATE(
        CONCAT('20', SUBSTRING(employee_no, 4, 2), '-01-01'),
        '%Y-%m-%d'
    )
WHERE employee_no REGEXP '^(TC|CP)-[0-9]{5}$'
  AND job_family IS NULL
  AND hire_date IS NULL;

INSERT INTO employee_number_sequencetbl (job_family, hire_year, last_sequence)
SELECT
    CASE
        WHEN employee_no LIKE 'TC-%' THEN 'TECHNICAL'
        ELSE 'CORPORATE'
    END,
    2000 + CAST(SUBSTRING(employee_no, 4, 2) AS UNSIGNED),
    MAX(CAST(RIGHT(employee_no, 3) AS UNSIGNED))
FROM usertbl
WHERE employee_no REGEXP '^(TC|CP)-[0-9]{5}$'
GROUP BY
    CASE
        WHEN employee_no LIKE 'TC-%' THEN 'TECHNICAL'
        ELSE 'CORPORATE'
    END,
    2000 + CAST(SUBSTRING(employee_no, 4, 2) AS UNSIGNED)
ON DUPLICATE KEY UPDATE
    last_sequence = GREATEST(last_sequence, VALUES(last_sequence));
