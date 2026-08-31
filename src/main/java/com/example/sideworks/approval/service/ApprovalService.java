package com.example.sideworks.approval.service;

import com.example.sideworks.approval.dto.ApprovalCcResponse;
import com.example.sideworks.approval.dto.ApprovalActivityResponse;
import com.example.sideworks.approval.dto.ApprovalDetailHeaderResponse;
import com.example.sideworks.approval.dto.ApprovalDetailResponse;
import com.example.sideworks.approval.dto.ApprovalDecisionRequest;
import com.example.sideworks.approval.dto.ApprovalDraftRequest;
import com.example.sideworks.approval.dto.ApprovalHistoryResponse;
import com.example.sideworks.approval.dto.ApprovalLineResponse;
import com.example.sideworks.approval.dto.ApprovalListResponse;
import com.example.sideworks.approval.dto.ApprovalSubmitRequest;
import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalActionType;
import com.example.sideworks.approval.entity.ApprovalCc;
import com.example.sideworks.approval.entity.ApprovalHistory;
import com.example.sideworks.approval.entity.ApprovalLine;
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
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final ApprovalRepository approvalRepository;
    private final UserRepository userRepository;
    private final ApprovalLineRepository approvalLineRepository;
    private final ApprovalCcRepository approvalCcRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final ApprovalSubmissionValidator submissionValidator;
    private final ApprovalSubmissionFactory submissionFactory;

    @Transactional
    public Long createDraft(String loginId, ApprovalDraftRequest request) {
        validateDraftRequest(request);

        User writer = findUserByLoginId(loginId);
        Approval approval = Approval.createDraft(
                writer,
                request.getTitle(),
                request.getContent()
        );

        return approvalRepository.save(approval).getApprovalId();
    }

    @Transactional
    public void updateDraft(Long approvalId, String loginId, ApprovalDraftRequest request) {
        Approval approval = findEditableDraft(approvalId, loginId);

        validateDraftRequest(request);
        approval.updateDraft(request.getTitle(), request.getContent());
    }

    @Transactional
    public void deleteDraft(Long approvalId, String loginId) {
        Approval approval = findEditableDraft(approvalId, loginId);

        approvalRepository.delete(approval);
    }

    @Transactional
    public void submitApproval(Long approvalId, String loginId, ApprovalSubmitRequest request) {
        Approval approval = findEditableDraft(approvalId, loginId);

        submissionValidator.validateRequest(request);
        submissionValidator.validateDocument(approval);

        List<Long> approverIds = request.getApproverIds();
        List<Long> ccUserIds = normalizeCcUserIds(request.getCcUserIds());

        List<User> approvers = findUsersInOrder(approverIds);
        List<User> ccUsers = findUsersInOrder(ccUserIds);

        submissionValidator.validateParticipants(approval, approvers, ccUsers);

        List<ApprovalLine> approvalLines = submissionFactory.createLines(
                approval,
                approvers
        );
        List<ApprovalCc> approvalCcs = submissionFactory.createCcs(
                approval,
                ccUsers
        );
        ApprovalHistory history = submissionFactory.createHistory(approval);

        approval.submit(LocalDateTime.now());
        saveSubmissionDetails(approvalLines, approvalCcs, history);
    }

    @Transactional
    public void approveApproval(Long approvalId, String loginId, ApprovalDecisionRequest request) {
        User actor = findUserByLoginId(loginId);
        Approval approval = findApprovalForDecision(approvalId);
        ApprovalLine currentLine = findCurrentApprovalLine(approval);

        validateCurrentApprover(currentLine, actor);
        String comment = normalizeDecisionComment(request, false);
        LocalDateTime processedAt = LocalDateTime.now();

        currentLine.approve(comment, processedAt);
        advanceApproval(approval, currentLine, processedAt);
        saveDecisionHistory(approval, currentLine, actor, ApprovalActionType.APPROVED, comment);
    }

    @Transactional
    public void rejectApproval(Long approvalId, String loginId, ApprovalDecisionRequest request) {
        User actor = findUserByLoginId(loginId);
        Approval approval = findApprovalForDecision(approvalId);
        ApprovalLine currentLine = findCurrentApprovalLine(approval);

        validateCurrentApprover(currentLine, actor);
        String comment = normalizeDecisionComment(request, true);
        LocalDateTime processedAt = LocalDateTime.now();

        currentLine.reject(comment, processedAt);
        approval.reject(processedAt);
        saveDecisionHistory(approval, currentLine, actor, ApprovalActionType.REJECTED, comment);
    }

    @Transactional
    public void cancelApproval(Long approvalId, String loginId) {
        User actor = findUserByLoginId(loginId);
        Approval approval = findApprovalForDecision(approvalId);

        validateCancellation(approval, actor);
        LocalDateTime canceledAt = LocalDateTime.now();

        approval.cancel(canceledAt);
        approvalHistoryRepository.save(ApprovalHistory.create(
                approval,
                actor,
                approval.getCurrentStep(),
                ApprovalActionType.CANCELED,
                null
        ));
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

        return approvalRepository.findPendingByApproverId(approver.getUserId(), keyword, pageable);
    }

    public Page<ApprovalListResponse> getProcessedApprovals(String loginId, String keyword, ApprovalStatus status, Pageable pageable) {
        User approver = findUserByLoginId(loginId);

        return approvalRepository.findProcessedByApproverId(approver.getUserId(), keyword, status, pageable);
    }

    public Page<ApprovalListResponse> getCcApprovals(String loginId, String keyword, ApprovalStatus status, Pageable pageable) {
        User ccUser = findUserByLoginId(loginId);

        return approvalRepository.findCcByUserId(ccUser.getUserId(), keyword, status, pageable);
    }

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
        ApprovalDetailHeaderResponse header = findAccessibleDetailHeader(approvalId, viewer);

        List<ApprovalLineResponse> approvalLines = approvalRepository
                .findDetailLinesByApprovalId(approvalId);
        List<ApprovalCcResponse> ccUsers = approvalRepository
                .findDetailCcsByApprovalId(approvalId);
        List<ApprovalHistoryResponse> histories = approvalRepository
                .findDetailHistoriesByApprovalId(approvalId);

        return ApprovalDetailResponse.of(
                header,
                approvalLines,
                ccUsers,
                histories
        );
    }

    private ApprovalDetailHeaderResponse findAccessibleDetailHeader(
            Long approvalId,
            User viewer
    ) {
        if (viewer.getUserRole() == UserRole.SUPER_ADMIN) {
            return approvalRepository
                    .findDetailHeaderByApprovalId(approvalId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));
        }

        return approvalRepository
                .findAccessibleDetailHeader(approvalId, viewer.getUserId())
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

    private void validateCurrentApprover(ApprovalLine approvalLine, User actor) {
        if (!approvalLine.isApprover(actor.getUserId())) {
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
            ApprovalLine currentLine,
            LocalDateTime processedAt
    ) {
        Optional<ApprovalLine> nextLine = approvalLineRepository
                .findFirstByApproval_ApprovalIdAndApprovalStepGreaterThanOrderByApprovalStepAsc(
                        approval.getApprovalId(),
                        currentLine.getApprovalStep()
                );

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
