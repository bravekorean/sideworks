package com.example.sideworks.leave.dto;

import java.math.BigDecimal;

public record AnnualLeaveAvailabilityResponse(
        int leaveYear, BigDecimal grantedDays, BigDecimal remainingDays,
        BigDecimal pendingDays, BigDecimal availableDays) {
    public static AnnualLeaveAvailabilityResponse of(AnnualLeaveBalanceResponse balance, BigDecimal pendingDays) {
        BigDecimal pending = pendingDays == null ? BigDecimal.ZERO.setScale(1) : pendingDays;
        return new AnnualLeaveAvailabilityResponse(balance.leaveYear(), balance.grantedDays(),
                balance.remainingDays(), pending, balance.remainingDays().subtract(pending));
    }
}
