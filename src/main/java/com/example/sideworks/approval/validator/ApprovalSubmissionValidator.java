package com.example.sideworks.approval.validator;

import com.example.sideworks.approval.dto.ApprovalSubmitRequest;
import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class ApprovalSubmissionValidator {

    public void validateRequest(ApprovalSubmitRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        List<Long> approverIds = request.getApproverIds();
        List<Long> ccUserIds = request.getCcUserIds() == null
                ? List.of()
                : request.getCcUserIds();

        validateParticipantIds(approverIds, ccUserIds);
    }

    public void validateDocument(Approval approval) {
        if (approval.getTitle().isBlank() || approval.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    public void validateParticipants(
            Approval approval,
            List<User> approvers,
            List<User> ccUsers
    ) {
        Long writerId = approval.getWriter().getUserId();

        for (User approver : approvers) {
            validateApprover(writerId, approver);
        }

        for (User ccUser : ccUsers) {
            validateCcUser(writerId, ccUser);
        }
    }

    private void validateParticipantIds(List<Long> approverIds, List<Long> ccUserIds) {
        if (approverIds == null || approverIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (containsNull(approverIds) || containsNull(ccUserIds)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (hasDuplicates(approverIds) || hasDuplicates(ccUserIds)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (hasOverlap(approverIds, ccUserIds)) {
            throw new BusinessException(ErrorCode.INVALID_CC_USER);
        }
    }

    private boolean containsNull(List<Long> userIds) {
        return userIds.stream().anyMatch(Objects::isNull);
    }

    private boolean hasDuplicates(List<Long> userIds) {
        return new HashSet<>(userIds).size() != userIds.size();
    }

    private boolean hasOverlap(List<Long> approverIds, List<Long> ccUserIds) {
        Set<Long> approverIdSet = new HashSet<>(approverIds);
        return ccUserIds.stream().anyMatch(approverIdSet::contains);
    }

    private void validateApprover(Long writerId, User approver) {
        if (Objects.equals(writerId, approver.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_APPROVER);
        }

        if (approver.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_APPROVER);
        }

        if (!hasApproverRole(approver)) {
            throw new BusinessException(ErrorCode.INVALID_APPROVER);
        }
    }

    private boolean hasApproverRole(User approver) {
        UserRole role = approver.getUserRole();
        return role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN;
    }

    private void validateCcUser(Long writerId, User ccUser) {
        if (Objects.equals(writerId, ccUser.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_CC_USER);
        }

        if (ccUser.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_CC_USER);
        }
    }
}
