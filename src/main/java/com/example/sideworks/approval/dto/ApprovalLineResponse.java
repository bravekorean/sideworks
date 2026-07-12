package com.example.sideworks.approval.dto;

import com.example.sideworks.approval.entity.ApprovalLineStatus;
import lombok.Getter;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ApprovalLineResponse {

    private final Long approvalLineId;
    private final Long approverId;
    private final String approverName;
    private final Integer approvalStep;
    private final ApprovalLineStatus approvalStatus;
    private final String approvalComment;
    private final LocalDateTime processedAt;
}
