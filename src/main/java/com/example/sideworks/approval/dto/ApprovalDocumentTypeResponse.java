package com.example.sideworks.approval.dto;


import com.example.sideworks.approval.entity.ApprovalDocumentType;
import com.example.sideworks.approval.entity.DocumentBehaviorType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApprovalDocumentTypeResponse {

    private final Long approvalDocumentTypeId;
    private final String typeCode;
    private final String typeName;
    private final String description;
    private final String contentTemplate;
    private final DocumentBehaviorType behaviorType;


    public static ApprovalDocumentTypeResponse from(ApprovalDocumentType type) {
        return new ApprovalDocumentTypeResponse(
                type.getApprovalDocumentTypeId(),
                type.getTypeCode(),
                type.getTypeName(),
                type.getDescription(),
                type.getContentTemplate(),
                type.getBehaviorType()
        );
    }
}
