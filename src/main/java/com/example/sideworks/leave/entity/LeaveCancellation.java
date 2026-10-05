package com.example.sideworks.leave.entity;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "leave_cancellationtbl")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveCancellation extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leave_cancellation_id")
    private Long leaveCancellationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_request_id", nullable = false)
    private LeaveRequest request;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_id", nullable = false, unique = true)
    private Approval approval;

    @Column(name = "pending_leave_request_id", unique = true)
    private Long pendingLeaveRequestId;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static LeaveCancellation create(LeaveRequest request, Approval approval, String reason) {
        LeaveCancellation cancellation = new LeaveCancellation();
        cancellation.request = Objects.requireNonNull(request);
        cancellation.approval = Objects.requireNonNull(approval);
        cancellation.pendingLeaveRequestId = Objects.requireNonNull(request.getLeaveRequestId());
        cancellation.reason = Objects.requireNonNull(reason);
        return cancellation;
    }

    public void finish() {
        pendingLeaveRequestId = null;
    }
}
