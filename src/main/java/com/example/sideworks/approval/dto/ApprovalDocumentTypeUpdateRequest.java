package com.example.sideworks.approval.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApprovalDocumentTypeUpdateRequest {

    private String typeName;
    private String description;
    private String contentTemplate;
    private Integer sortOrder;
    private Boolean active;
    private Long version;
}