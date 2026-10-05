package com.example.sideworks.approval.dto;

import com.example.sideworks.approval.attachment.dto.ApprovalAttachmentResponse;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.approval.entity.DocumentBehaviorType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApprovalDetailResponse {

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
    private final Long documentTypeId;
    private final String documentTypeName;
    private final DocumentBehaviorType documentBehaviorType;
    private final List<ApprovalLineResponse> approvalLines;
    private final List<ApprovalCcResponse> ccUsers;
    private final List<ApprovalHistoryResponse> histories;
    private final List<ApprovalAttachmentResponse> attachments;

    public static ApprovalDetailResponse of(
                                            ApprovalDetailHeaderResponse header,
                                            List<ApprovalLineResponse> approvalLines,
                                            List<ApprovalCcResponse> ccUsers,
                                            List<ApprovalHistoryResponse> histories,
                                            List<ApprovalAttachmentResponse> attachments) {
        return new ApprovalDetailResponse(
                header.getApprovalId(),
                header.getWriterId(),
                header.getWriterName(),
                header.getTitle(),
                header.getContent(),
                header.getApprovalStatus(),
                header.getCurrentStep(),
                header.getCreatedAt(),
                header.getUpdatedAt(),
                header.getSubmittedAt(),
                header.getCompletedAt(),
                header.getDocumentTypeId(),
                header.getDocumentTypeName(),
                header.getDocumentBehaviorType(),
                List.copyOf(approvalLines),
                List.copyOf(ccUsers),
                List.copyOf(histories),
                List.copyOf(attachments)
        );
    }
}
