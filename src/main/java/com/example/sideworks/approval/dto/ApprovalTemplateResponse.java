package com.example.sideworks.approval.dto;

import com.example.sideworks.approval.entity.ApprovalTemplateScope;
import com.example.sideworks.approval.entity.ApprovalTemplateStatus;
import com.example.sideworks.approval.entity.ApprovalTemplateValidationStatus;

import java.util.List;

public record ApprovalTemplateResponse(Long approvalTemplateId, String templateName, String description,
                                       ApprovalTemplateScope scope, Long departmentId, String departmentName,
                                       ApprovalTemplateStatus status, ApprovalTemplateValidationStatus validationStatus,
                                       Long version, String blockReason, List<Member> approvers, List<Member> ccUsers) {
    public record Member(Long userId, String userName, String departmentName, String positionName,
                         String status, int approvalStep) {
    }
}
