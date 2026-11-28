package com.example.sideworks.approval.dto;

import com.example.sideworks.approval.entity.ApprovalActionType;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * actor*는 실제로 행동한 사람이다. 대결로 승인·반려한 이력이면 onBehalfOf*에 원 결재자를 채운다.
 */
@Getter
public class ApprovalHistoryResponse {

    private final Long approvalHistoryId;
    private final Long actorId;
    private final String actorName;
    private final Integer actionStep;
    private final ApprovalActionType actionType;
    private final String comment;
    private final LocalDateTime createdAt;
    private final Long onBehalfOfId;
    private final String onBehalfOfName;

    public ApprovalHistoryResponse(Long approvalHistoryId, Long actorId, String actorName, Integer actionStep,
                                   ApprovalActionType actionType, String comment, LocalDateTime createdAt,
                                   Long stepApproverId, String stepApproverName) {
        boolean delegatedDecision = isDecision(actionType) && stepApproverId != null
                && !Objects.equals(stepApproverId, actorId);
        this.approvalHistoryId = approvalHistoryId;
        this.actorId = actorId;
        this.actorName = actorName;
        this.actionStep = actionStep;
        this.actionType = actionType;
        this.comment = comment;
        this.createdAt = createdAt;
        this.onBehalfOfId = delegatedDecision ? stepApproverId : null;
        this.onBehalfOfName = delegatedDecision ? stepApproverName : null;
    }

    private static boolean isDecision(ApprovalActionType actionType) {
        return actionType == ApprovalActionType.APPROVED || actionType == ApprovalActionType.REJECTED;
    }
}
