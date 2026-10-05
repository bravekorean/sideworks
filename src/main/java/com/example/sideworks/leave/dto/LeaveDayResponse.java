package com.example.sideworks.leave.dto;

import com.example.sideworks.leave.entity.LeavePeriod;
import com.example.sideworks.leave.entity.LeaveRequestDay;

import java.time.LocalDate;

public record LeaveDayResponse(LocalDate date, LeavePeriod period, Long leaveRequestId) {
    public static LeaveDayResponse from(LeaveRequestDay day) {
        return new LeaveDayResponse(day.getLeaveDate(), day.getLeavePeriod(),
                day.getRequest().getLeaveRequestId());
    }
}
