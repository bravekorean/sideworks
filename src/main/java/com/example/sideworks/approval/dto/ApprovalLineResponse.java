package com.example.sideworks.approval.dto;

import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.user.entity.UserStatus;
import lombok.Getter;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ApprovalLineResponse {

    private final Long approvalLineId;
    private final Long approverId;
    private final String approverName;
    private final UserStatus approverStatus;
    private final Long originalApproverId;
    private final String originalApproverName;
    private final Integer approvalStep;
    private final ApprovalLineStatus approvalStatus;
    private final String approvalComment;
    private final LocalDateTime processedAt;
}
