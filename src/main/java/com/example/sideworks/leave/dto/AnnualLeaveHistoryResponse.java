package com.example.sideworks.leave.dto;

import com.example.sideworks.leave.entity.AnnualLeaveActionType;
import com.example.sideworks.leave.entity.AnnualLeaveHistory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AnnualLeaveHistoryResponse(
        Long annualLeaveHistoryId, AnnualLeaveActionType actionType, BigDecimal daysDelta,
        BigDecimal balanceAfter, Long leaveRequestId, Long cancellationId, Long actorId,
        LocalDateTime createdAt) {
    public static AnnualLeaveHistoryResponse from(AnnualLeaveHistory history) {
        return new AnnualLeaveHistoryResponse(history.getAnnualLeaveHistoryId(), history.getActionType(),
                history.getDaysDelta(), history.getBalanceAfter(), history.getLeaveRequestId(),
                history.getCancellationId(), history.getActorId(), history.getCreatedAt());
    }
}
