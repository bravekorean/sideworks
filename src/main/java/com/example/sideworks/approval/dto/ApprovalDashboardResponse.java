package com.example.sideworks.approval.dto;

import java.util.List;

public record ApprovalDashboardResponse(
        Summary summary,
        List<ApprovalListResponse> pendingApprovals,
        List<ApprovalActivityResponse> recentActivities
) {
    public record Summary(long pending, long inProgress, long drafts, long cc) {
    }
}
