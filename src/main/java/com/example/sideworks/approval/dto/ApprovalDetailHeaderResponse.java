package com.example.sideworks.approval.dto;

import com.example.sideworks.approval.entity.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ApprovalDetailHeaderResponse {

    private final Long approvalId;
    private final Long writerId;
    private final String writerName;
    private final String title;
    private final String content;
    private final ApprovalStatus approvalStatus;
    private final Integer currentStep;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final LocalDateTime submittedAt;
    private final LocalDateTime completedAt;
}
