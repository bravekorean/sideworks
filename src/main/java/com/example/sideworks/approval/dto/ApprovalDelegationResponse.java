package com.example.sideworks.approval.dto;

import com.example.sideworks.approval.entity.ApprovalDelegation;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ApprovalDelegationResponse(Long approvalDelegationId, Long delegatorId, String delegatorName,
                                         Long delegateeId, String delegateeName, LocalDate startDate,
                                         LocalDate endDate, LocalDateTime canceledAt) {
    public static ApprovalDelegationResponse from(ApprovalDelegation delegation) {
        return new ApprovalDelegationResponse(delegation.getApprovalDelegationId(),
                delegation.getDelegator().getUserId(), delegation.getDelegator().getUserName(),
                delegation.getDelegatee().getUserId(), delegation.getDelegatee().getUserName(),
                delegation.getStartDate(), delegation.getEndDate(), delegation.getCanceledAt());
    }
}
