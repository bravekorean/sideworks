package com.example.sideworks.leave.controller;

import com.example.sideworks.leave.dto.AnnualLeaveBalanceResponse;
import com.example.sideworks.leave.dto.AnnualLeaveAvailabilityResponse;
import com.example.sideworks.leave.dto.AnnualLeaveHistoryResponse;
import com.example.sideworks.leave.dto.LeaveDayResponse;
import com.example.sideworks.leave.service.AnnualLeaveAvailabilityService;
import com.example.sideworks.leave.service.AnnualLeaveBalanceService;
import com.example.sideworks.leave.service.LeaveCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/annual-leave/me")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "연차", description = "본인 연도별 연차 잔액과 증감 이력")
public class AnnualLeaveController {
    private final AnnualLeaveBalanceService service;
    private final AnnualLeaveAvailabilityService availability;
    private final LeaveCalendarService calendar;

    @GetMapping("/days")
    @Operation(summary = "본인 승인 휴가 날짜 조회", description = "취소되지 않은 승인 휴가만 반환합니다.")
    public List<LeaveDayResponse> days(Authentication authentication,
                                      @RequestParam int year, @RequestParam int month) {
        return calendar.getMyApprovedDays(authentication.getName(), year, month);
    }

    @GetMapping("/availability")
    @Operation(summary = "본인 휴가 신청 가능 연차", description = "잔여량에서 진행 중인 휴가 신청량을 제외합니다.")
    public AnnualLeaveAvailabilityResponse availability(Authentication authentication,
                                                        @RequestParam(required = false) Integer year) {
        return availability.getMyAvailability(authentication.getName(), year);
    }

    @GetMapping("/balance")
    @Operation(summary = "본인 연차 잔액 조회", description = "해당 연도를 처음 조회하면 부여량과 GRANT 이력을 함께 생성합니다.")
    public AnnualLeaveBalanceResponse balance(Authentication authentication,
                                              @RequestParam(required = false) Integer year) {
        return service.getMyBalance(authentication.getName(), year);
    }

    @GetMapping("/history")
    @Operation(summary = "본인 연차 증감 이력", description = "해당 연도의 부여·사용·복원 이력을 최근순으로 조회합니다.")
    public List<AnnualLeaveHistoryResponse> history(Authentication authentication,
                                                    @RequestParam(required = false) Integer year) {
        return service.getMyHistory(authentication.getName(), year);
    }
}
