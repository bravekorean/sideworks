package com.example.sideworks.approval.attachment.repository;

import com.example.sideworks.approval.attachment.entity.ApprovalAttachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalAttachmentRepository extends JpaRepository<ApprovalAttachment, Long> {

    List<ApprovalAttachment> findAllByApproval_ApprovalIdOrderByApprovalAttachmentIdAsc(Long approvalId);

    @EntityGraph(attributePaths = {"approval", "approval.writer"})
    Optional<ApprovalAttachment> findByApprovalAttachmentIdAndApproval_ApprovalId(
            Long approvalAttachmentId,
            Long approvalId
    );

    long countByApproval_ApprovalId(Long approvalId);

    List<ApprovalAttachment> findAllByApproval_ApprovalId(Long approvalId);
}
