CREATE TABLE approval_attachmenttbl (
    approval_attachment_id BIGINT NOT NULL AUTO_INCREMENT,
    approval_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NULL,
    file_extension VARCHAR(20) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (approval_attachment_id),
    UNIQUE KEY uk_approval_attachment_storage_key (storage_key),
    KEY idx_approval_attachment_approval (approval_id, approval_attachment_id),
    CONSTRAINT fk_approval_attachment_approval
        FOREIGN KEY (approval_id) REFERENCES approvaltbl (approval_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_approval_attachment_uploader
        FOREIGN KEY (uploader_id) REFERENCES usertbl (user_id),
    CONSTRAINT chk_approval_attachment_file_size CHECK (file_size > 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='전자결재 첨부파일 메타데이터';
