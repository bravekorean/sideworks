package com.example.sideworks.attendance.dto;

import com.example.sideworks.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceMemberResponse(
        Long userId, String userName, String employeeNo,
        String departmentName, String positionName,
        LocalDate attendanceDate, LocalDateTime checkInAt, LocalDateTime checkOutAt,
        AttendanceWorkState workState, boolean late, boolean earlyLeave
) {
    public static AttendanceMemberResponse from(User user, AttendanceDailyResponse attendance) {
        return new AttendanceMemberResponse(
                user.getUserId(), user.getUserName(), user.getEmployeeNo(),
                user.getDepartment() == null ? null : user.getDepartment().getDepartmentName(),
                user.getPosition() == null ? null : user.getPosition().getPositionName(),
                attendance.getAttendanceDate(), attendance.getCheckInAt(), attendance.getCheckOutAt(),
                attendance.getWorkState(), attendance.isLate(), attendance.isEarlyLeave()
        );
    }
}
