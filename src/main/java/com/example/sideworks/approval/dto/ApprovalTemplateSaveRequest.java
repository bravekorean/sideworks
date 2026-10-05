package com.example.sideworks.approval.dto;

import com.example.sideworks.approval.entity.ApprovalTemplateScope;
import com.example.sideworks.approval.entity.ApprovalTemplateStatus;

import java.util.List;

public record ApprovalTemplateSaveRequest(String templateName, String description,
                                          ApprovalTemplateScope scope, Long departmentId,
                                          ApprovalTemplateStatus status, Long version,
                                          List<Long> approverIds, List<Long> ccUserIds) {
}
