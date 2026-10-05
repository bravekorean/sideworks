package com.example.sideworks.leave.entity;

import com.example.sideworks.common.entity.BaseCreatedEntity;
import com.example.sideworks.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "annual_leave_historytbl")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnualLeaveHistory extends BaseCreatedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "annual_leave_history_id")
    private Long annualLeaveHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "balance_id", nullable = false)
    private AnnualLeaveBalance balance;

    @Column(name = "leave_request_id")
    private Long leaveRequestId;

    @Column(name = "cancellation_id")
    private Long cancellationId;

    @Column(name = "actor_id")
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private AnnualLeaveActionType actionType;

    @Column(name = "days_delta", nullable = false, precision = 4, scale = 1)
    private BigDecimal daysDelta;

    @Column(name = "balance_after", nullable = false, precision = 4, scale = 1)
    private BigDecimal balanceAfter;

    @Column(name = "source_key", nullable = false, length = 100, unique = true)
    private String sourceKey;

    public static AnnualLeaveHistory grant(AnnualLeaveBalance balance) {
        Objects.requireNonNull(balance);
        if (balance.getUser() == null || balance.getUser().getUserId() == null
                || balance.getAnnualLeaveBalanceId() == null) {
            throw new IllegalArgumentException("저장된 연차 잔액에 대해서만 부여 이력을 생성할 수 있습니다.");
        }
        AnnualLeaveHistory history = new AnnualLeaveHistory();
        history.balance = balance;
        history.actionType = AnnualLeaveActionType.GRANT;
        history.daysDelta = balance.getGrantedDays();
        history.balanceAfter = balance.getRemainingDays();
        history.sourceKey = "GRANT:" + balance.getUser().getUserId() + ":" + balance.getLeaveYear();
        return history;
    }

    public static AnnualLeaveHistory use(AnnualLeaveBalance balance, LeaveRequest request, User actor) {
        AnnualLeaveHistory history = new AnnualLeaveHistory();
        history.balance = Objects.requireNonNull(balance);
        history.leaveRequestId = Objects.requireNonNull(request.getLeaveRequestId());
        history.actorId = Objects.requireNonNull(actor.getUserId());
        history.actionType = AnnualLeaveActionType.USE;
        history.daysDelta = request.getTotalDays().negate();
        history.balanceAfter = balance.getRemainingDays();
        history.sourceKey = "USE:" + request.getLeaveRequestId();
        return history;
    }

    public static AnnualLeaveHistory restore(AnnualLeaveBalance balance, LeaveCancellation cancellation, User actor) {
        AnnualLeaveHistory history = new AnnualLeaveHistory();
        history.balance = Objects.requireNonNull(balance);
        history.leaveRequestId = Objects.requireNonNull(cancellation.getRequest().getLeaveRequestId());
        history.cancellationId = Objects.requireNonNull(cancellation.getLeaveCancellationId());
        history.actorId = Objects.requireNonNull(actor.getUserId());
        history.actionType = AnnualLeaveActionType.RESTORE;
        history.daysDelta = cancellation.getRequest().getTotalDays();
        history.balanceAfter = balance.getRemainingDays();
        history.sourceKey = "RESTORE:" + cancellation.getLeaveCancellationId();
        return history;
    }
}
