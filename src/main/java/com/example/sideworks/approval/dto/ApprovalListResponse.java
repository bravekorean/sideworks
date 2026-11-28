package com.example.sideworks.approval.dto;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ApprovalListResponse {
    private final Long approvalId;
    private final String title;
    private final Long writerId;
    private final String writerName;
    private final ApprovalStatus approvalStatus;
    private final Integer currentStep;
    private final LocalDateTime createdAt;
    private final LocalDateTime submittedAt;
    private final LocalDateTime completedAt;
    // 대기함에서만 채운다. 대결로 받은 문서면 DELEGATED와 원 결재자 이름, 대결이 막힌 내 문서면 DELEGATION_BLOCKED.
    private final ApprovalDelegationState delegationState;
    private final String originalApproverName;

    public ApprovalListResponse(Long approvalId, String title, Long writerId, String writerName,
                                ApprovalStatus approvalStatus, Integer currentStep, LocalDateTime createdAt,
                                LocalDateTime submittedAt, LocalDateTime completedAt) {
        this(approvalId, title, writerId, writerName, approvalStatus, currentStep, createdAt, submittedAt,
                completedAt, null, null);
    }

    private ApprovalListResponse(Long approvalId, String title, Long writerId, String writerName,
                                 ApprovalStatus approvalStatus, Integer currentStep, LocalDateTime createdAt,
                                 LocalDateTime submittedAt, LocalDateTime completedAt,
                                 ApprovalDelegationState delegationState, String originalApproverName) {
        this.approvalId = approvalId;
        this.title = title;
        this.writerId = writerId;
        this.writerName = writerName;
        this.approvalStatus = approvalStatus;
        this.currentStep = currentStep;
        this.createdAt = createdAt;
        this.submittedAt = submittedAt;
        this.completedAt = completedAt;
        this.delegationState = delegationState;
        this.originalApproverName = originalApproverName;
    }

    public ApprovalListResponse withDelegation(ApprovalDelegationState state, String originalApproverName) {
        return new ApprovalListResponse(approvalId, title, writerId, writerName, approvalStatus, currentStep,
                createdAt, submittedAt, completedAt, state, originalApproverName);
    }

    public static ApprovalListResponse from(Approval approval) {
        return new ApprovalListResponse(
                approval.getApprovalId(),
                approval.getTitle(),
                approval.getWriter().getUserId(),
                approval.getWriter().getUserName(),
                approval.getApprovalStatus(),
                approval.getCurrentStep(),
                approval.getCreatedAt(),
                approval.getSubmittedAt(),
                approval.getCompletedAt()
        );
    }
}
