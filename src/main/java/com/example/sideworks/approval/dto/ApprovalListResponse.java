package com.example.sideworks.approval.dto;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalStatus;
import lombok.Getter;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ApprovalListResponse {
    private Long approvalId;
    private String title;
    private Long writerId;
    private String writerName;
    private ApprovalStatus approvalStatus;
    private Integer currentStep;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;

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
