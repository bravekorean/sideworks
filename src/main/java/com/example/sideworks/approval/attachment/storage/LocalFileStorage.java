package com.example.sideworks.approval.attachment.storage;

import com.example.sideworks.approval.attachment.config.AttachmentProperties;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
@Slf4j
public class LocalFileStorage implements FileStorage {

    private final Path rootPath;

    public LocalFileStorage(AttachmentProperties properties) {
        this.rootPath = properties.getStoragePath().toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(rootPath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.ATTACHMENT_STORAGE_FAILED);
        }
    }

    @Override
    public StoredFile store(Long approvalId, MultipartFile file) {
        String storageKey = approvalId + "/" + UUID.randomUUID();
        Path target = resolveSafely(storageKey);

        try {
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target);
            }
            return new StoredFile(storageKey);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.ATTACHMENT_STORAGE_FAILED);
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path target = resolveSafely(storageKey);
        if (!Files.isRegularFile(target)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveSafely(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Attachment physical file deletion failed. storageKeyHash={}", storageKey.hashCode());
        }
    }

    private Path resolveSafely(String storageKey) {
        Path target = rootPath.resolve(storageKey).normalize();
        if (!target.startsWith(rootPath)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        return target;
    }
}
