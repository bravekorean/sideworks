package com.example.sideworks.approval.entity;

import com.example.sideworks.common.entity.BaseCreatedEntity;
import com.example.sideworks.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "approval_delegationtbl")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalDelegation extends BaseCreatedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_delegation_id")
    private Long approvalDelegationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delegator_id", nullable = false)
    private User delegator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delegatee_id", nullable = false)
    private User delegatee;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User creator;

    public static ApprovalDelegation create(User delegator, User delegatee, LocalDate startDate,
                                            LocalDate endDate, User creator) {
        ApprovalDelegation delegation = new ApprovalDelegation();
        delegation.delegator = delegator;
        delegation.delegatee = delegatee;
        delegation.startDate = startDate;
        delegation.endDate = endDate;
        delegation.creator = creator;
        return delegation;
    }

}
