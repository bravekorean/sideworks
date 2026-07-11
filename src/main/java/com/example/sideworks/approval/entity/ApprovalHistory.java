package com.example.sideworks.approval.entity;

import com.example.sideworks.common.entity.BaseCreatedEntity;
import com.example.sideworks.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "approval_historytbl")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalHistory extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_history_id")
    private Long approvalHistoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_id", nullable = false)
    private Approval approval;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Column(name = "action_step", nullable = false)
    private Integer actionStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ApprovalActionType actionType;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    public static ApprovalHistory create(Approval approval, User actor, Integer actionStep, ApprovalActionType actionType, String comment) {
        ApprovalHistory history = new ApprovalHistory();
        history.approval = approval;
        history.actor = actor;
        history.actionStep = actionStep;
        history.actionType = actionType;
        history.comment = comment;

        return history;
    }
}
