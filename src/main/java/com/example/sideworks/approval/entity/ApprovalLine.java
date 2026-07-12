package com.example.sideworks.approval.entity;

import com.example.sideworks.common.entity.BaseCreatedEntity;
import com.example.sideworks.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "approval_linetbl",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_approval_linetbl_approval_step",
                        columnNames = {"approval_id", "approval_step"}
                ),
                @UniqueConstraint(
                        name = "uk_approval_linetbl_approval_approver",
                        columnNames = {"approval_id", "approver_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalLine extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_line_id")
    private Long approvalLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_id", nullable = false)
    private Approval approval;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id", nullable = false)
    private User approver;

    @Column(name = "approval_step", nullable = false)
    private Integer approvalStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalLineStatus approvalStatus;

    @Column(name = "approval_comment", columnDefinition = "TEXT")
    private String approvalComment;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public static ApprovalLine create(Approval approval, User approver, Integer approvalStep, ApprovalLineStatus approvalStatus) {
        ApprovalLine approvalLine = new ApprovalLine();
        approvalLine.approval = approval;
        approvalLine.approver = approver;
        approvalLine.approvalStep = approvalStep;
        approvalLine.approvalStatus = approvalStatus;

        return approvalLine;
    }

    public boolean isPending() {
        return approvalStatus == ApprovalLineStatus.PENDING;
    }

    public boolean isApprover(Long userId) {
        return approver.getUserId().equals(userId);
    }

    public void approve(String comment, LocalDateTime processedAt) {
        validatePending();
        this.approvalStatus = ApprovalLineStatus.APPROVED;
        this.approvalComment = normalizeComment(comment);
        this.processedAt = processedAt;
    }

    public void reject(String comment, LocalDateTime processedAt) {
        validatePending();
        this.approvalStatus = ApprovalLineStatus.REJECTED;
        this.approvalComment = normalizeComment(comment);
        this.processedAt = processedAt;
    }

    public void activate() {
        if (approvalStatus != ApprovalLineStatus.WAITING) {
            throw new IllegalStateException("대기 중인 결재선만 활성화할 수 있습니다.");
        }

        this.approvalStatus = ApprovalLineStatus.PENDING;
    }

    private void validatePending() {
        if (!isPending()) {
            throw new IllegalStateException("현재 처리할 수 있는 결재선이 아닙니다.");
        }
    }

    private String normalizeComment(String comment) {
        return comment == null || comment.isBlank() ? null : comment.trim();
    }
}
