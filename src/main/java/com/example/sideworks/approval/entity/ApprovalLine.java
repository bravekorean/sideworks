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
}
