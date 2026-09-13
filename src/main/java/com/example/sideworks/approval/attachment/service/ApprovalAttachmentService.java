package com.example.sideworks.approval.attachment.service;

import com.example.sideworks.approval.attachment.config.AttachmentProperties;
import com.example.sideworks.approval.attachment.dto.ApprovalAttachmentResponse;
import com.example.sideworks.approval.attachment.entity.ApprovalAttachment;
import com.example.sideworks.approval.attachment.repository.ApprovalAttachmentRepository;
import com.example.sideworks.approval.attachment.storage.FileStorage;
import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalAttachmentService {

    private final ApprovalAttachmentRepository attachmentRepository;
    private final ApprovalRepository approvalRepository;
    private final UserRepository userRepository;
    private final FileStorage fileStorage;
    private final AttachmentFileValidator fileValidator;
    private final AttachmentProperties properties;

    @Transactional
    public List<ApprovalAttachmentResponse> upload(Long approvalId, String loginId, List<MultipartFile> files) {
        Approval approval = findEditableApprovalForUpdate(approvalId, loginId);
        List<ApprovalAttachment> existing = attachmentRepository
                .findAllByApproval_ApprovalIdOrderByApprovalAttachmentIdAsc(approvalId);
        List<AttachmentFileValidator.ValidatedFile> validatedFiles = validateBatch(files, existing);
        List<String> storedKeys = new ArrayList<>();
        registerRollbackCleanup(storedKeys);

        List<ApprovalAttachment> attachments = new ArrayList<>(validatedFiles.size());
        for (AttachmentFileValidator.ValidatedFile validated : validatedFiles) {
            FileStorage.StoredFile stored = fileStorage.store(approvalId, validated.file());
            storedKeys.add(stored.storageKey());
            attachments.add(ApprovalAttachment.create(
                    approval,
                    approval.getWriter(),
                    validated.originalFileName(),
                    stored.storageKey(),
                    validated.contentType(),
                    validated.extension(),
                    validated.file().getSize()
            ));
        }

        return attachmentRepository.saveAll(attachments).stream()
                .map(ApprovalAttachmentResponse::from)
                .toList();
    }

    public List<ApprovalAttachmentResponse> findAll(Long approvalId, String loginId) {
        requireAccessibleApproval(approvalId, loginId);
        return attachmentRepository.findAllByApproval_ApprovalIdOrderByApprovalAttachmentIdAsc(approvalId)
                .stream()
                .map(ApprovalAttachmentResponse::from)
                .toList();
    }

    public DownloadFile download(Long approvalId, Long attachmentId, String loginId) {
        requireAccessibleApproval(approvalId, loginId);
        ApprovalAttachment attachment = findAttachment(approvalId, attachmentId);
        Resource resource = fileStorage.load(attachment.getStorageKey());
        return new DownloadFile(resource, attachment.getOriginalFileName(), attachment.getContentType(), attachment.getFileSize());
    }

    @Transactional
    public void delete(Long approvalId, Long attachmentId, String loginId) {
        findEditableApprovalForUpdate(approvalId, loginId);
        ApprovalAttachment attachment = findAttachment(approvalId, attachmentId);
        attachmentRepository.delete(attachment);
        registerAfterCommitDeletion(attachment.getStorageKey());
    }

    private Approval findEditableApprovalForUpdate(Long approvalId, String loginId) {
        Approval approval = approvalRepository.findByIdForUpdate(approvalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));
        if (!approval.getWriter().getLoginId().equals(loginId)) {
            throw new BusinessException(ErrorCode.APPROVAL_NOT_FOUND);
        }
        if (!approval.isDraft()) {
            throw new BusinessException(ErrorCode.APPROVAL_NOT_EDITABLE);
        }
        return approval;
    }

    private void requireAccessibleApproval(Long approvalId, String loginId) {
        User viewer = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

        boolean directlyAccessible = approval.isWriter(viewer.getUserId())
                || viewer.getUserRole() == UserRole.SUPER_ADMIN;
        if (!directlyAccessible && approvalRepository.findAccessibleDetailHeader(approvalId, viewer.getUserId()).isEmpty()) {
            throw new BusinessException(ErrorCode.APPROVAL_NOT_FOUND);
        }
    }

    private List<AttachmentFileValidator.ValidatedFile> validateBatch(
            List<MultipartFile> files,
            List<ApprovalAttachment> existing
    ) {
        if (files == null || files.isEmpty()
                || existing.size() + files.size() > properties.getMaxFileCount()) {
            throw new BusinessException(ErrorCode.ATTACHMENT_LIMIT_EXCEEDED);
        }

        long totalSize = existing.stream().mapToLong(ApprovalAttachment::getFileSize).sum();
        List<AttachmentFileValidator.ValidatedFile> validated = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            if (file == null || file.getSize() <= 0 || file.getSize() > properties.getMaxFileSize().toBytes()) {
                throw new BusinessException(ErrorCode.ATTACHMENT_LIMIT_EXCEEDED);
            }
            totalSize += file.getSize();
            if (totalSize > properties.getMaxTotalSize().toBytes()) {
                throw new BusinessException(ErrorCode.ATTACHMENT_LIMIT_EXCEEDED);
            }
            validated.add(fileValidator.validate(file));
        }
        return validated;
    }

    private ApprovalAttachment findAttachment(Long approvalId, Long attachmentId) {
        return attachmentRepository.findByApprovalAttachmentIdAndApproval_ApprovalId(attachmentId, approvalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
    }

    private void registerRollbackCleanup(List<String> storedKeys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storedKeys.forEach(fileStorage::delete);
                }
            }
        });
    }

    private void registerAfterCommitDeletion(String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileStorage.delete(storageKey);
            }
        });
    }

    public record DownloadFile(Resource resource, String originalFileName, String contentType, long fileSize) {
    }
}
