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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

@Component
@Slf4j
public class LocalFileStorage implements FileStorage {

    /** 문서 생성 전에 저장한 첨부(휴가·취소 신청)의 키 접두사. 문서별 폴더(`{approvalId}/`)와 섞이지 않게 분리한다. */
    private static final String UNBOUND_PREFIX = "unbound/";

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
        return write(approvalId + "/" + UUID.randomUUID(), file);
    }

    @Override
    public StoredFile storeUnbound(MultipartFile file) {
        return write(UNBOUND_PREFIX + UUID.randomUUID(), file);
    }

    private StoredFile write(String storageKey, MultipartFile file) {
        Path target = resolveSafely(storageKey);

        boolean created = false;
        boolean stored = false;
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream();
                 OutputStream output = Files.newOutputStream(target, CREATE_NEW, WRITE)) {
                created = true;
                input.transferTo(output);
            }
            stored = true;
            return new StoredFile(storageKey);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.ATTACHMENT_STORAGE_FAILED);
        } finally {
            if (created && !stored) {
                delete(storageKey);
            }
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
