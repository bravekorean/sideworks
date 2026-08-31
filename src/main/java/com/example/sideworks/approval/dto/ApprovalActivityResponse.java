package com.example.sideworks.approval.dto;

import com.example.sideworks.approval.entity.ApprovalActionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ApprovalActivityResponse {

    private final Long approvalHistoryId;
    private final Long approvalId;
    private final String title;
    private final Long actorId;
    private final String actorName;
    private final Integer actionStep;
    private final ApprovalActionType actionType;
    private final LocalDateTime createdAt;
}
