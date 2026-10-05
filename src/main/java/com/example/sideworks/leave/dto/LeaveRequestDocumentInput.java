package com.example.sideworks.leave.dto;

import com.example.sideworks.leave.entity.LeavePeriod;

import java.time.LocalDate;
import java.util.List;

public record LeaveRequestDocumentInput(LocalDate startDate, LocalDate endDate, LeavePeriod period,
                                        String reason, Long approverId, List<Long> approverIds,
                                        List<Long> ccUserIds, Long templateId, Long templateVersion) {
    public LeaveRequestDocumentInput(LocalDate startDate, LocalDate endDate, LeavePeriod period,
                                     String reason, Long approverId) {
        this(startDate, endDate, period, reason, approverId, null, null, null, null);
    }

    public List<Long> effectiveApproverIds() {
        if (approverIds != null) return approverIds;
        return approverId == null ? List.of() : List.of(approverId);
    }

    public List<Long> effectiveCcUserIds() {
        return ccUserIds == null ? List.of() : ccUserIds;
    }
}
