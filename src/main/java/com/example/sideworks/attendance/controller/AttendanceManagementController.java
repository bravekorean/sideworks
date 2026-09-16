package com.example.sideworks.attendance.controller;

import com.example.sideworks.attendance.dto.*;
import com.example.sideworks.attendance.service.AttendanceManagementService;
import com.example.sideworks.common.dto.PageResponse;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/attendances/management")
@SecurityRequirement(name = "bearerAuth")
public class AttendanceManagementController {
    private final AttendanceManagementService service;

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> invalidParameters(Exception exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.from(ErrorCode.INVALID_REQUEST));
    }

    @GetMapping("/scope")
    @Operation(summary = "직원 근태 조회 가능 부서", description = "현재 역할과 부서장 관계로 조회 범위를 계산합니다.")
    public AttendanceManagementScope scope(Authentication authentication) {
        return service.getScope(authentication.getName());
    }

    @GetMapping
    @Operation(summary = "직원 일별 근태 조회", description = "현재 재직자와 현재 조직 기준. 입사일 이전 직원은 제외하며 부서 필터는 해당 부서의 직속 직원입니다.")
    public PageResponse<AttendanceMemberResponse> daily(Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.getDaily(authentication.getName(), date, departmentId, name, page, size));
    }
}
