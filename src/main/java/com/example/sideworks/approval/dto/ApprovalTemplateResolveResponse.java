package com.example.sideworks.approval.dto;

import java.util.List;

public record ApprovalTemplateResolveResponse(Long templateId, Long version, List<Long> approverIds,
                                              List<Long> ccUserIds, boolean authorIsApprover,
                                              List<ApprovalTemplateResponse.Member> approvers,
                                              List<ApprovalTemplateResponse.Member> ccUsers) {
}
