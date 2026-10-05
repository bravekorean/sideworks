package com.example.sideworks.approval.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApprovalDocumentTypeCreateRequest {

    private String typeCode;
    private String typeName;
    private String description;
    private String contentTemplate;
    private Integer sortOrder;
}