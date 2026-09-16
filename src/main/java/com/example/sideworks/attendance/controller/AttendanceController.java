package com.example.sideworks.attendance.controller;

import com.example.sideworks.attendance.dto.AttendanceDailyResponse;
import com.example.sideworks.attendance.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/attendances")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "근태", description = "본인 출퇴근 처리 및 근태 조회 API")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/check-in")
    @Operation(summary = "출근", description = "현재 서버 시각으로 인증 사용자의 출근을 기록합니다.")
    public ResponseEntity<AttendanceDailyResponse> checkIn(Authentication authentication) {
        AttendanceDailyResponse response = attendanceService.checkIn(authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/check-out")
    @Operation(summary = "퇴근", description = "현재 서버 시각으로 인증 사용자의 퇴근을 기록합니다.")
    public ResponseEntity<AttendanceDailyResponse> checkOut(Authentication authentication) {
        return ResponseEntity.ok(attendanceService.checkOut(authentication.getName()));
    }

    @GetMapping("/me/today")
    @Operation(summary = "오늘 근태 조회", description = "인증 사용자의 오늘 출퇴근 및 근무 상태를 조회합니다.")
    public ResponseEntity<AttendanceDailyResponse> getToday(Authentication authentication) {
        return ResponseEntity.ok(attendanceService.getToday(authentication.getName()));
    }

    @GetMapping("/me")
    @Operation(summary = "월별 근태 조회", description = "인증 사용자의 지정 연월에 저장된 근태 기록을 조회합니다.")
    public ResponseEntity<List<AttendanceDailyResponse>> getMonthly(
            Authentication authentication,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ResponseEntity.ok(attendanceService.getMonthly(authentication.getName(), year, month));
    }
}
