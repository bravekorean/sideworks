package com.example.sideworks.approval.attachment.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {

    StoredFile store(Long approvalId, MultipartFile file);

    Resource load(String storageKey);

    void delete(String storageKey);

    record StoredFile(String storageKey) {
    }
}
