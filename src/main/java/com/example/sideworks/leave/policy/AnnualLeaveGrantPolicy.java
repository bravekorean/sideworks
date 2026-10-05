package com.example.sideworks.leave.policy;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class AnnualLeaveGrantPolicy {
    public BigDecimal calculate(LocalDate hireDate, int leaveYear) {
        if (hireDate == null || leaveYear < 2000 || leaveYear > 9999 || leaveYear < hireDate.getYear()) {
            throw new BusinessException(ErrorCode.LEAVE_YEAR_NOT_ELIGIBLE);
        }

        int difference = leaveYear - hireDate.getYear();
        int days;
        if (difference == 0) {
            days = 11;
        } else if (difference <= 2) {
            days = 15;
        } else {
            days = Math.min(13 + difference, 25);
        }
        return BigDecimal.valueOf(days).setScale(1);
    }
}
