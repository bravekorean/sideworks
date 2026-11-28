package com.example.sideworks.approval.attachment.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {

    StoredFile store(Long approvalId, MultipartFile file);

    /**
     * 결재 문서가 생기기 전에 저장한다. 휴가·취소 신청처럼 사용자 행 잠금 안에서 파일 I/O를 하지 않으려는 경로에서 쓴다.
     * 키는 문서 ID에 의존하지 않으며, 조회·삭제는 기존과 같이 키로 한다.
     */
    StoredFile storeUnbound(MultipartFile file);

    Resource load(String storageKey);

    void delete(String storageKey);

    record StoredFile(String storageKey) {
    }
}
