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
        },
        // 실제 DB는 database/migrations로 반영한다. 이름과 컬럼 순서를 SQL과 같게 유지한다.
        indexes = {
                // 대기함: 원 결재자(본인과 나에게 위임한 사람) 기준으로 대기 단계를 찾는다.
                @Index(
                        name = "idx_approval_linetbl_approver_status",
                        columnList = "approver_id, approval_status"
                ),
                // 처리함: 실제 처리자 기준 최신순 첫 페이지를 정렬 없이 읽는다.
                @Index(
                        name = "idx_approval_linetbl_processor_processed",
                        columnList = "processed_by_id, processed_at DESC, approval_line_id DESC, approval_status, approval_id"
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

    // 상신 때 지정한 원 결재자. 대결 중에도 바꾸지 않는다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id", nullable = false)
    private User approver;

    // 실제로 승인·반려한 사람. 대결이면 대결자, 아니면 원 결재자다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_id")
    private User processedBy;

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

    public void approve(User processor, String comment, LocalDateTime processedAt) {
        validatePending();
        this.processedBy = processor;
        this.approvalStatus = ApprovalLineStatus.APPROVED;
        this.approvalComment = normalizeComment(comment);
        this.processedAt = processedAt;
    }

    public void reject(User processor, String comment, LocalDateTime processedAt) {
        validatePending();
        this.processedBy = processor;
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
