package com.example.sideworks.approval.attachment.entity;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.common.entity.BaseCreatedEntity;
import com.example.sideworks.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "approval_attachmenttbl")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalAttachment extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_attachment_id")
    private Long approvalAttachmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_id", nullable = false)
    private Approval approval;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "storage_key", nullable = false, length = 500, unique = true)
    private String storageKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_extension", nullable = false, length = 20)
    private String fileExtension;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    public static ApprovalAttachment create(
            Approval approval,
            User uploader,
            String originalFileName,
            String storageKey,
            String contentType,
            String fileExtension,
            long fileSize
    ) {
        ApprovalAttachment attachment = new ApprovalAttachment();
        attachment.approval = approval;
        attachment.uploader = uploader;
        attachment.originalFileName = originalFileName;
        attachment.storageKey = storageKey;
        attachment.contentType = contentType;
        attachment.fileExtension = fileExtension;
        attachment.fileSize = fileSize;
        return attachment;
    }
}
