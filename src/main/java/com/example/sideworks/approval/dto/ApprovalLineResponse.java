package com.example.sideworks.approval.dto;

import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.user.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * approver*는 상신 때 지정한 원 결재자다.
 * processor*는 처리된 단계면 실제 처리자, 현재 대기 단계면 지금 처리할 사람, 그 외에는 null이다.
 */
@Getter
@AllArgsConstructor
public class ApprovalLineResponse {

    private final Long approvalLineId;
    private final Long approverId;
    private final String approverName;
    private final UserStatus approverStatus;
    private final Long processorId;
    private final String processorName;
    private final ApprovalDelegationState delegationState;
    private final Integer approvalStep;
    private final ApprovalLineStatus approvalStatus;
    private final String approvalComment;
    private final LocalDateTime processedAt;
}
