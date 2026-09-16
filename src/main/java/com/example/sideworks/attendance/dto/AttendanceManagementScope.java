package com.example.sideworks.attendance.dto;

import java.time.LocalDate;
import java.util.List;

public record AttendanceManagementScope(
        boolean allDepartments,
        LocalDate today,
        List<DepartmentOption> departments
) {
    public record DepartmentOption(Long departmentId, String departmentName) {}
}
