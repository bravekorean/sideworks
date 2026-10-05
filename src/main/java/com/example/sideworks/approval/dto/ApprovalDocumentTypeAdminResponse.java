package com.example.sideworks.approval.dto;

import com.example.sideworks.approval.entity.ApprovalDocumentType;
import com.example.sideworks.approval.entity.DocumentBehaviorType;

public record ApprovalDocumentTypeAdminResponse(
        Long approvalDocumentTypeId, String typeCode, String typeName, String description,
        String contentTemplate, DocumentBehaviorType behaviorType, boolean system,
        boolean active, int sortOrder, Long version) {
    public static ApprovalDocumentTypeAdminResponse from(ApprovalDocumentType type) {
        return new ApprovalDocumentTypeAdminResponse(type.getApprovalDocumentTypeId(), type.getTypeCode(),
                type.getTypeName(), type.getDescription(), type.getContentTemplate(), type.getBehaviorType(),
                type.isSystem(), type.isActive(), type.getSortOrder(), type.getVersion());
    }
}
