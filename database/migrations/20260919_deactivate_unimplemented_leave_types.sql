-- 휴가 신청·취소 화면과 승인 후처리가 구현되기 전까지 신규 작성 목록에서 숨긴다.
-- 20260918_approval_document_type.sql 적용 후 실행한다.
-- Workbench Safe Update Mode를 고려하여 PK 조건을 포함한다.

UPDATE approval_document_typetbl
SET active = b'0'
WHERE approval_document_type_id > 0
  AND type_code IN ('LEAVE_REQUEST', 'LEAVE_CANCELLATION')
  AND active = b'1';

SELECT type_code, active + 0 AS active
FROM approval_document_typetbl
WHERE type_code IN ('LEAVE_REQUEST', 'LEAVE_CANCELLATION')
ORDER BY type_code;
