package com.example.sideworks.approval.attachment.dto;

import com.example.sideworks.approval.attachment.entity.ApprovalAttachment;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ApprovalAttachmentResponse {

    private Long attachmentId;
    private String originalFileName;
    private String contentType;
    private long fileSize;
    private LocalDateTime createdAt;

    public static ApprovalAttachmentResponse from(ApprovalAttachment attachment) {
        return new ApprovalAttachmentResponse(
                attachment.getApprovalAttachmentId(),
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getCreatedAt()
        );
    }
}
