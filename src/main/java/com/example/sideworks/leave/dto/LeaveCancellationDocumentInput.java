package com.example.sideworks.leave.dto;

import java.util.List;

public record LeaveCancellationDocumentInput(Long leaveRequestId, String reason, Long approverId,
                                             List<Long> approverIds, List<Long> ccUserIds,
                                             Long templateId, Long templateVersion) {
    public LeaveCancellationDocumentInput(Long leaveRequestId, String reason, Long approverId) {
        this(leaveRequestId, reason, approverId, null, null, null, null);
    }

    public List<Long> effectiveApproverIds() {
        if (approverIds != null) return approverIds;
        return approverId == null ? List.of() : List.of(approverId);
    }

    public List<Long> effectiveCcUserIds() {
        return ccUserIds == null ? List.of() : ccUserIds;
    }
}
