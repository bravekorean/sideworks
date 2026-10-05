package com.example.sideworks.leave.dto;

import com.example.sideworks.leave.entity.AnnualLeaveBalance;

import java.math.BigDecimal;

public record AnnualLeaveBalanceResponse(
        int leaveYear, BigDecimal grantedDays, BigDecimal remainingDays, BigDecimal usedDays) {
    public static AnnualLeaveBalanceResponse from(AnnualLeaveBalance balance) {
        return new AnnualLeaveBalanceResponse(balance.getLeaveYear(), balance.getGrantedDays(),
                balance.getRemainingDays(), balance.getGrantedDays().subtract(balance.getRemainingDays()));
    }
}
