package com.example.sideworks.leave.entity;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.common.entity.BaseTimeEntity;
import com.example.sideworks.user.entity.User;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "leave_requesttbl")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequest extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leave_request_id")
    private Long leaveRequestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_id", nullable = false, unique = true)
    private Approval approval;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "balance_id", nullable = false)
    private AnnualLeaveBalance balance;

    @Column(name = "total_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal totalDays;

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static LeaveRequest create(User user, Approval approval, AnnualLeaveBalance balance,
                                      BigDecimal totalDays, String reason) {
        LeaveRequest request = new LeaveRequest();
        request.user = Objects.requireNonNull(user);
        request.approval = Objects.requireNonNull(approval);
        request.balance = Objects.requireNonNull(balance);
        request.totalDays = Objects.requireNonNull(totalDays);
        request.reason = Objects.requireNonNull(reason);
        return request;
    }

    public boolean isCanceled() {
        return canceledAt != null;
    }

    public void cancel(LocalDateTime canceledAt) {
        if (this.canceledAt != null) throw new IllegalStateException("이미 취소된 휴가입니다.");
        this.canceledAt = Objects.requireNonNull(canceledAt);
    }
}
