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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
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
    private final Clock clock;

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

    /**
     * 결재 문서를 만들기 전에 파일을 검증하고 저장한다. 휴가·취소 신청이 사용자 행 잠금을 잡기 전에 호출한다.
     * DB를 읽지 않는다. 잠금 전에 일반 SELECT가 실행되면 REPEATABLE READ 스냅샷이 잠금 전 시점으로 고정되어
     * 잠금 뒤의 잔여 연차 검사가 다른 요청의 커밋을 보지 못한다.
     * 트랜잭션이 커밋되지 않으면(검증 실패, 잠금 대기 초과, 저장 도중 실패 포함) 이미 저장한 파일을 지운다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<PreparedAttachment> prepareUnbound(List<MultipartFile> files) {
        List<AttachmentFileValidator.ValidatedFile> validatedFiles = validateBatch(files, List.of());
        List<String> storedKeys = new ArrayList<>();
        registerRollbackCleanup(storedKeys);

        List<PreparedAttachment> prepared = new ArrayList<>(validatedFiles.size());
        for (AttachmentFileValidator.ValidatedFile validated : validatedFiles) {
            FileStorage.StoredFile stored = fileStorage.storeUnbound(validated.file());
            storedKeys.add(stored.storageKey());
            prepared.add(new PreparedAttachment(validated, stored.storageKey()));
        }
        return prepared;
    }

    /** {@link #prepareUnbound}로 저장한 파일을 같은 트랜잭션에서 만든 새 결재 문서에 연결한다. 파일 I/O는 없다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void attachPrepared(Approval approval, List<PreparedAttachment> prepared) {
        if (prepared.isEmpty()) return;
        attachmentRepository.saveAll(prepared.stream()
                .map(item -> ApprovalAttachment.create(
                        approval,
                        approval.getWriter(),
                        item.file().originalFileName(),
                        item.storageKey(),
                        item.file().contentType(),
                        item.file().extension(),
                        item.file().file().getSize()))
                .toList());
    }

    public record PreparedAttachment(AttachmentFileValidator.ValidatedFile file, String storageKey) {
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
        if (!directlyAccessible && approvalRepository
                .findAccessibleDetailHeader(approvalId, viewer.getUserId(), LocalDate.now(clock)).isEmpty()) {
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
