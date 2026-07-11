package com.example.sideworks.approval.service;

import com.example.sideworks.approval.dto.ApprovalDraftRequest;
import com.example.sideworks.approval.dto.ApprovalSubmitRequest;
import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalCc;
import com.example.sideworks.approval.entity.ApprovalHistory;
import com.example.sideworks.approval.entity.ApprovalLine;
import com.example.sideworks.approval.factory.ApprovalSubmissionFactory;
import com.example.sideworks.approval.repository.ApprovalCcRepository;
import com.example.sideworks.approval.repository.ApprovalHistoryRepository;
import com.example.sideworks.approval.repository.ApprovalLineRepository;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.approval.validator.ApprovalSubmissionValidator;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalService {

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

    private void saveSubmissionDetails(
            List<ApprovalLine> approvalLines,
            List<ApprovalCc> approvalCcs,
            ApprovalHistory history
    ) {
        approvalLineRepository.saveAll(approvalLines);

        if (!approvalCcs.isEmpty()) {
            approvalCcRepository.saveAll(approvalCcs);
        }

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
