package com.example.sideworks.approval.entity;

import com.example.sideworks.common.entity.BaseTimeEntity;
import com.example.sideworks.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "approvaltbl")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Approval extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_id")
    private Long approvalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writer_id", nullable = false)
    private User writer;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus;

    @Column(name = "current_step")
    private Integer currentStep;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public static Approval createDraft(User writer, String title, String content) {
        Approval approval = new Approval();
        approval.writer = writer;
        approval.title = title == null ? "" : title.trim();
        approval.content = content == null ? "" : content;
        approval.approvalStatus = ApprovalStatus.DRAFT;
        approval.currentStep = null;

        return approval;
    }

    public boolean isDraft() {
        return approvalStatus == ApprovalStatus.DRAFT;
    }

    public void updateDraft(String title, String content) {
        this.title = title == null ? "" : title.trim();
        this.content = content == null ? "" : content;
    }

    public void submit(LocalDateTime submittedAt) {
        this.approvalStatus = ApprovalStatus.IN_PROGRESS;
        this.currentStep = 1;
        this.submittedAt = submittedAt;
        this.completedAt = null;
    }
}
