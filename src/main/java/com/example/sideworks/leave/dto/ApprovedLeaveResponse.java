package com.example.sideworks.leave.dto;

import com.example.sideworks.leave.entity.LeavePeriod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ApprovedLeaveResponse(Long leaveRequestId, LocalDate startDate, LocalDate endDate,
                                    LeavePeriod period, BigDecimal totalDays) {}
