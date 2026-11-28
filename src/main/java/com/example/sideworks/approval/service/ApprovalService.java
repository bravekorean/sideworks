package com.example.sideworks.approval.service;

import com.example.sideworks.approval.attachment.dto.ApprovalAttachmentResponse;
import com.example.sideworks.approval.attachment.repository.ApprovalAttachmentRepository;
import com.example.sideworks.approval.attachment.storage.FileStorage;
import com.example.sideworks.approval.dto.ApprovalCcResponse;
import com.example.sideworks.approval.dto.ApprovalActivityResponse;
import com.example.sideworks.approval.dto.ApprovalDashboardResponse;
import com.example.sideworks.approval.dto.ApprovalDetailHeaderResponse;
import com.example.sideworks.approval.dto.ApprovalDetailResponse;
import com.example.sideworks.approval.dto.ApprovalDecisionRequest;
import com.example.sideworks.approval.dto.ApprovalDraftRequest;
import com.example.sideworks.approval.dto.ApprovalHistoryResponse;
import com.example.sideworks.approval.dto.ApprovalLineResponse;
import com.example.sideworks.approval.dto.ApprovalListResponse;
import com.example.sideworks.approval.dto.ApprovalSubmitRequest;
import com.example.sideworks.approval.dto.ApprovalTerminateRequest;
import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.DocumentBehaviorType;
import com.example.sideworks.approval.entity.ApprovalActionType;
import com.example.sideworks.approval.entity.ApprovalCc;
import com.example.sideworks.approval.entity.ApprovalHistory;
import com.example.sideworks.approval.entity.ApprovalLine;
import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.approval.factory.ApprovalSubmissionFactory;
import com.example.sideworks.approval.repository.ApprovalCcRepository;
import com.example.sideworks.approval.repository.ApprovalHistoryRepository;
import com.example.sideworks.approval.repository.ApprovalLineRepository;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.approval.validator.ApprovalSubmissionValidator;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import com.example.sideworks.notification.service.ApprovalNotificationWorkflow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalService {

    private static final int MAX_APPROVAL_COMMENT_LENGTH = 2000;
    private static final int DASHBOARD_PENDING_SIZE = 4;
    private static final int DASHBOARD_ACTIVITY_SIZE = 5;

    private final ApprovalRepository approvalRepository;
    private final UserRepository userRepository;
    private final ApprovalLineRepository approvalLineRepository;
    private final ApprovalCcRepository approvalCcRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final ApprovalAttachmentRepository attachmentRepository;
    private final FileStorage fileStorage;
    private final ApprovalSubmissionValidator submissionValidator;
    private final ApprovalSubmissionFactory submissionFactory;
    private final com.example.sideworks.attendance.service.AttendanceCorrectionService attendanceCorrections;
    private final com.example.sideworks.leave.service.LeaveRequestService leaveRequests;
    private final com.example.sideworks.leave.service.LeaveCancellationService leaveCancellations;
    private final ApprovalDocumentTypeService documentTypes;
    private final ApprovalNotificationWorkflow notificationWorkflow;
    private final ApprovalTemplateService approvalTemplateService;
    private final ApprovalDelegationService delegationService;
    // 모든 시각·날짜는 애플리케이션 시간대(app.time-zone, 기본 Asia/Seoul) 기준이다.
    private final Clock clock;

    // 최근 활동은 최신 이력 ID 조회와 접근 판정을 두 문장으로 나눠 실행하므로 같은 스냅샷이 필요하다.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ApprovalDashboardResponse getDashboard(String loginId) {
        User user = findUserByLoginId(loginId);
        Long userId = user.getUserId();
        Page<ApprovalListResponse> pending = approvalRepository.findPendingByProcessorId(
                userId, today(), null, PageRequest.of(0, DASHBOARD_PENDING_SIZE));
        ApprovalDashboardResponse.Summary summary = new ApprovalDashboardResponse.Summary(
                pending.getTotalElements(),
                approvalRepository.countInProgressByWriterId(userId),
                approvalRepository.countDraftsByWriterId(userId),
                approvalRepository.countCcByUserId(userId));
        return new ApprovalDashboardResponse(summary, pending.getContent(),
                approvalRepository.findLatestActivitiesByUserId(userId, DASHBOARD_ACTIVITY_SIZE));
    }

    public Page<ApprovalListResponse> getManagedApprovals(String loginId, String keyword,
                                                         ApprovalStatus status, boolean blockedOnly, Pageable pageable) {
        User actor = findUserByLoginId(loginId);
        if (actor.getUserRole() != UserRole.SUPER_ADMIN) {
            throw new BusinessException(ErrorCode.APPROVAL_MANAGEMENT_FORBIDDEN);
        }
        return approvalRepository.findForManagement(keyword, status, blockedOnly, today(), pageable);
    }

    @Transactional
    public Long createDraft(String loginId, ApprovalDraftRequest request) {
        validateDraftRequest(request);

        User writer = findUserByLoginId(loginId);
        Approval approval = Approval.createDraft(
                writer,
                request.getTitle(),
                request.getContent(),
                documentTypes.requireActiveGeneral(request.getDocumentTypeId())
        );

        return approvalRepository.save(approval).getApprovalId();
    }

    @Transactional
    public void updateDraft(Long approvalId, String loginId, ApprovalDraftRequest request) {
        Approval approval = findEditableDraft(approvalId, loginId);

        validateDraftRequest(request);
        validateGeneralDraft(approval);
        approval.updateDraft(request.getTitle(), request.getContent(),
                documentTypes.requireActiveGeneral(request.getDocumentTypeId()));
    }

    @Transactional
    public void deleteDraft(Long approvalId, String loginId) {
        Approval approval = findEditableDraft(approvalId, loginId);
        List<String> storageKeys = attachmentRepository.findAllByApproval_ApprovalId(approvalId)
                .stream()
                .map(attachment -> attachment.getStorageKey())
                .toList();

        approvalRepository.delete(approval);
        if (!storageKeys.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storageKeys.forEach(fileStorage::delete);
                }
            });
        }
    }

    @Transactional
    public void submitApproval(Long approvalId, String loginId, ApprovalSubmitRequest request) {
        Approval approval = findEditableDraft(approvalId, loginId);
        validateGeneralDraft(approval);
        documentTypes.requireActiveGeneral(approval.getDocumentType() == null ? null
                : approval.getDocumentType().getApprovalDocumentTypeId());

        submissionValidator.validateRequest(request);
        submissionValidator.validateDocument(approval);

        if ((request.getTemplateId() == null) != (request.getTemplateVersion() == null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (request.getTemplateId() != null) {
            approvalTemplateService.validateSubmission(loginId, request.getTemplateId(), request.getTemplateVersion());
        }

        List<Long> approverIds = request.getApproverIds();
        List<Long> ccUserIds = normalizeCcUserIds(request.getCcUserIds());

        List<User> approvers = findUsersInOrder(approverIds);
        List<User> ccUsers = findUsersInOrder(ccUserIds);

        submissionValidator.validateParticipants(approval, approvers, ccUsers);

        List<ApprovalLine> approvalLines = submissionFactory.createLines(approval, approvers);
        List<ApprovalCc> approvalCcs = submissionFactory.createCcs(
                approval,
                ccUsers
        );
        ApprovalHistory history = submissionFactory.createHistory(approval);

        approval.submit(LocalDateTime.now(clock));
        saveSubmissionDetails(approvalLines, approvalCcs, history);
        notificationWorkflow.onSubmitted(approval, approvalLines.getFirst(), ccUsers);
    }

    private void validateGeneralDraft(Approval approval) {
        if (approval.getDocumentType() != null
                && approval.getDocumentType().getBehaviorType() != DocumentBehaviorType.GENERAL) {
            throw new BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_ALLOWED);
        }
        if (approval.getDocumentType() == null && !"GENERAL_PROPOSAL".equals(approval.getLegacyDocumentType())) {
            throw new BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_ALLOWED);
        }
    }

    @Transactional
    public void approveApproval(Long approvalId, String loginId, ApprovalDecisionRequest request) {
        User actor = findUserByLoginId(loginId);
        Approval approval = findApprovalForDecision(approvalId);
        // 문서 잠금을 얻은 뒤의 서버 시각으로 기간과 권한을 판단한다. 잠금을 기다리는 동안 위임이 끝났으면 거부된다.
        LocalDateTime processedAt = LocalDateTime.now(clock);
        ApprovalLine currentLine = findCurrentApprovalLine(approval);

        validateCurrentProcessor(approval, currentLine, actor, processedAt.toLocalDate());
        String comment = normalizeDecisionComment(request, false);
        Optional<ApprovalLine> nextLine = approvalLineRepository
                .findFirstByApproval_ApprovalIdAndApprovalStepGreaterThanOrderByApprovalStepAsc(
                        approval.getApprovalId(), currentLine.getApprovalStep());

        attendanceCorrections.onDecision(approval, actor, true);
        if (nextLine.isEmpty()) {
            // 연차 사용·복원은 모든 결재 단계가 끝날 때 한 번만 처리한다.
            leaveRequests.onDecision(approval, actor, true);
            leaveCancellations.onDecision(approval, actor, true);
        }
        currentLine.approve(actor, comment, processedAt);
        advanceApproval(approval, nextLine, processedAt);
        saveDecisionHistory(approval, currentLine, actor, ApprovalActionType.APPROVED, comment);
        notificationWorkflow.onApproved(approval, currentLine.getApprovalStep());
    }

    @Transactional
    public void rejectApproval(Long approvalId, String loginId, ApprovalDecisionRequest request) {
        User actor = findUserByLoginId(loginId);
        Approval approval = findApprovalForDecision(approvalId);
        // 승인과 같이 잠금 이후 서버 시각으로 판단한다.
        LocalDateTime processedAt = LocalDateTime.now(clock);
        ApprovalLine currentLine = findCurrentApprovalLine(approval);

        validateCurrentProcessor(approval, currentLine, actor, processedAt.toLocalDate());
        String comment = normalizeDecisionComment(request, true);

        attendanceCorrections.onDecision(approval, actor, false);
        leaveRequests.onDecision(approval, actor, false);
        leaveCancellations.onDecision(approval, actor, false);
        currentLine.reject(actor, comment, processedAt);
        approval.reject(processedAt);
        saveDecisionHistory(approval, currentLine, actor, ApprovalActionType.REJECTED, comment);
        notificationWorkflow.onRejected(approval);
    }

    @Transactional
    public void cancelApproval(Long approvalId, String loginId) {
        User actor = findUserByLoginId(loginId);
        Approval approval = findApprovalForDecision(approvalId);

        validateCancellation(approval, actor);
        LocalDateTime canceledAt = LocalDateTime.now(clock);
        ApprovalLine currentLine = findCurrentApprovalLine(approval);

        attendanceCorrections.onCancel(approvalId);
        leaveCancellations.onCancel(approvalId);
        approval.cancel(canceledAt);
        approvalHistoryRepository.save(ApprovalHistory.create(
                approval,
                actor,
                approval.getCurrentStep(),
                ApprovalActionType.CANCELED,
                null
        ));
        notificationWorkflow.onCanceled(approval, currentLine);
    }

    @Transactional
    public void terminateApproval(Long approvalId, String loginId, ApprovalTerminateRequest request) {
        User actor = findUserByLoginId(loginId);
        if (actor.getUserRole() != UserRole.SUPER_ADMIN) {
            throw new BusinessException(ErrorCode.APPROVAL_TERMINATE_FORBIDDEN);
        }
        String reason = request == null ? null : request.reason();
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.APPROVAL_TERMINATE_REASON_REQUIRED);
        }
        reason = reason.trim();
        if (reason.length() > MAX_APPROVAL_COMMENT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Approval approval = findApprovalForDecision(approvalId);
        LocalDateTime terminatedAt = LocalDateTime.now(clock);
        ApprovalLine currentLine = findCurrentApprovalLine(approval);
        if (!currentLine.isPending()) {
            throw new BusinessException(ErrorCode.APPROVAL_LINE_NOT_PROCESSABLE);
        }
        // 원 결재자가 비활성이어도 유효한 대결자가 있으면 문서를 계속 처리할 수 있으므로 종료하지 않는다.
        User currentProcessor = delegationService.resolveProcessor(approval, currentLine, terminatedAt.toLocalDate());
        if (currentProcessor.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.APPROVAL_TERMINATE_NOT_ALLOWED);
        }

        attendanceCorrections.onCancel(approvalId);
        leaveCancellations.onCancel(approvalId);
        approval.terminate(terminatedAt);
        approvalHistoryRepository.save(ApprovalHistory.create(
                approval, actor, currentLine.getApprovalStep(), ApprovalActionType.TERMINATED, reason));
        notificationWorkflow.onTerminated(approval);
    }

    public Page<ApprovalListResponse> getDraftApprovals(String loginId, String keyword, Pageable pageable) {
        User writer = findUserByLoginId(loginId);

        return approvalRepository.findDraftsByWriterId(writer.getUserId(), keyword, pageable);
    }

    public Page<ApprovalListResponse> getSentApprovals(String loginId, String keyword, ApprovalStatus status, Pageable pageable) {
        User writer = findUserByLoginId(loginId);

        return approvalRepository.findSentByWriterId(writer.getUserId(), keyword, status, pageable);
    }

    public Page<ApprovalListResponse> getPendingApprovals(String loginId, String keyword, Pageable pageable) {
        User approver = findUserByLoginId(loginId);

        return approvalRepository.findPendingByProcessorId(approver.getUserId(), today(), keyword, pageable);
    }

    public Page<ApprovalListResponse> getProcessedApprovals(String loginId, String keyword, ApprovalStatus status, Pageable pageable) {
        User approver = findUserByLoginId(loginId);

        return approvalRepository.findProcessedByProcessorId(approver.getUserId(), keyword, status, pageable);
    }

    public Page<ApprovalListResponse> getCcApprovals(String loginId, String keyword, ApprovalStatus status, Pageable pageable) {
        User ccUser = findUserByLoginId(loginId);

        return approvalRepository.findCcByUserId(ccUser.getUserId(), keyword, status, pageable);
    }

    // 최근 활동은 최신 이력 ID 조회와 접근 판정을 두 문장으로 나눠 실행하므로 같은 스냅샷이 필요하다.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ApprovalActivityResponse> getRecentActivities(String loginId, Pageable pageable) {
        User user = findUserByLoginId(loginId);

        return approvalRepository.findRecentActivitiesByUserId(user.getUserId(), pageable);
    }

    public Page<ApprovalListResponse> searchApprovals(
            String loginId,
            String keyword,
            Pageable pageable
    ) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        User user = findUserByLoginId(loginId);
        boolean searchAll = user.getUserRole() == UserRole.SUPER_ADMIN;

        return approvalRepository.searchApprovals(
                user.getUserId(),
                searchAll,
                keyword.trim(),
                pageable
        );
    }

    public ApprovalDetailResponse getApprovalDetail(Long approvalId, String loginId) {
        User viewer = findUserByLoginId(loginId);
        LocalDate today = today();
        ApprovalDetailHeaderResponse header = findAccessibleDetailHeader(approvalId, viewer, today);

        List<ApprovalLineResponse> approvalLines = approvalRepository
                .findDetailLinesByApprovalId(approvalId, today);
        List<ApprovalCcResponse> ccUsers = approvalRepository
                .findDetailCcsByApprovalId(approvalId);
        List<ApprovalHistoryResponse> histories = approvalRepository
                .findDetailHistoriesByApprovalId(approvalId);
        List<ApprovalAttachmentResponse> attachments = attachmentRepository
                .findAllByApproval_ApprovalIdOrderByApprovalAttachmentIdAsc(approvalId)
                .stream()
                .map(ApprovalAttachmentResponse::from)
                .toList();

        return ApprovalDetailResponse.of(
                header,
                approvalLines,
                ccUsers,
                histories,
                attachments,
                canDecide(header, approvalLines, viewer)
        );
    }

    // 목록과 같은 규칙으로 계산한 현재 처리자(processorId)가 조회자이면 승인·반려 버튼을 보여준다.
    private boolean canDecide(ApprovalDetailHeaderResponse header, List<ApprovalLineResponse> lines, User viewer) {
        return header.getApprovalStatus() == ApprovalStatus.IN_PROGRESS && lines.stream()
                .anyMatch(line -> line.getApprovalStatus() == ApprovalLineStatus.PENDING
                        && viewer.getUserId().equals(line.getProcessorId()));
    }

    private ApprovalDetailHeaderResponse findAccessibleDetailHeader(
            Long approvalId,
            User viewer,
            LocalDate today
    ) {
        if (viewer.getUserRole() == UserRole.SUPER_ADMIN) {
            return approvalRepository
                    .findDetailHeaderByApprovalId(approvalId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));
        }

        return approvalRepository
                .findAccessibleDetailHeader(approvalId, viewer.getUserId(), today)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));
    }

    private void validateDraftRequest(ApprovalDraftRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        String title = request.getTitle();
        if (title != null && title.trim().length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private List<Long> normalizeCcUserIds(List<Long> ccUserIds) {
        return ccUserIds == null ? List.of() : ccUserIds;
    }

    private List<User> findUsersInOrder(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }

        List<User> users = userRepository.findAllById(userIds);

        if (users.size() != userIds.size()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Map<Long, User> userMap = new HashMap<>();
        for (User user : users) {
            userMap.put(user.getUserId(), user);
        }

        List<User> orderedUsers = new ArrayList<>(userIds.size());
        for (Long userId : userIds) {
            orderedUsers.add(userMap.get(userId));
        }

        return orderedUsers;
    }

    private void saveSubmissionDetails(List<ApprovalLine> approvalLines, List<ApprovalCc> approvalCcs, ApprovalHistory history) {
        approvalLineRepository.saveAll(approvalLines);

        if (!approvalCcs.isEmpty()) {
            approvalCcRepository.saveAll(approvalCcs);
        }

        approvalHistoryRepository.save(history);
    }

    private Approval findApprovalForDecision(Long approvalId) {
        Approval approval = approvalRepository.findByIdForUpdate(approvalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

        if (!approval.isInProgress()) {
            throw new BusinessException(ErrorCode.APPROVAL_NOT_IN_PROGRESS);
        }

        return approval;
    }

    private ApprovalLine findCurrentApprovalLine(Approval approval) {
        if (approval.getCurrentStep() == null) {
            throw new BusinessException(ErrorCode.APPROVAL_LINE_NOT_PROCESSABLE);
        }

        return approvalLineRepository
                .findByApproval_ApprovalIdAndApprovalStep(
                        approval.getApprovalId(),
                        approval.getCurrentStep()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_LINE_NOT_PROCESSABLE));
    }

    // 위임 기간 중에는 대결자만, 그 밖에는 원 결재자만 처리할 수 있다.
    private void validateCurrentProcessor(Approval approval, ApprovalLine approvalLine, User actor, LocalDate today) {
        User processor = delegationService.resolveProcessor(approval, approvalLine, today);
        if (!processor.getUserId().equals(actor.getUserId())) {
            throw new BusinessException(ErrorCode.APPROVAL_DECISION_FORBIDDEN);
        }

        if (!approvalLine.isPending()) {
            throw new BusinessException(ErrorCode.APPROVAL_LINE_NOT_PROCESSABLE);
        }
    }

    private void validateCancellation(Approval approval, User actor) {
        if (!approval.isWriter(actor.getUserId())) {
            throw new BusinessException(ErrorCode.APPROVAL_CANCEL_FORBIDDEN);
        }

        boolean hasProcessedLine = approvalLineRepository
                .existsByApproval_ApprovalIdAndProcessedAtIsNotNull(approval.getApprovalId());
        if (hasProcessedLine) {
            throw new BusinessException(ErrorCode.APPROVAL_CANCEL_NOT_ALLOWED);
        }
    }

    private String normalizeDecisionComment(ApprovalDecisionRequest request, boolean required) {
        String comment = request == null ? null : request.getComment();

        if (required && (comment == null || comment.isBlank())) {
            throw new BusinessException(ErrorCode.REJECTION_COMMENT_REQUIRED);
        }

        if (comment == null || comment.isBlank()) {
            return null;
        }

        String normalizedComment = comment.trim();
        if (normalizedComment.length() > MAX_APPROVAL_COMMENT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        return normalizedComment;
    }

    private void advanceApproval(
            Approval approval,
            Optional<ApprovalLine> nextLine,
            LocalDateTime processedAt
    ) {
        if (nextLine.isEmpty()) {
            approval.complete(processedAt);
            return;
        }

        ApprovalLine line = nextLine.get();
        line.activate();
        approval.moveToNextStep(line.getApprovalStep());
    }

    private void saveDecisionHistory(
            Approval approval,
            ApprovalLine currentLine,
            User actor,
            ApprovalActionType actionType,
            String comment
    ) {
        ApprovalHistory history = ApprovalHistory.create(
                approval,
                actor,
                currentLine.getApprovalStep(),
                actionType,
                comment
        );

        approvalHistoryRepository.save(history);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private User findUserByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Approval findEditableDraft(Long approvalId, String loginId) {
        Approval approval = approvalRepository
                .findByApprovalIdAndWriter_LoginId(approvalId, loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

        if (!approval.isDraft()) {
            throw new BusinessException(ErrorCode.APPROVAL_NOT_EDITABLE);
        }

        return approval;
    }

}
