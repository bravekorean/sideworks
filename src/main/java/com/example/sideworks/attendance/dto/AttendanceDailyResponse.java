package com.example.sideworks.attendance.dto;

import com.example.sideworks.attendance.entity.Attendance;
import com.example.sideworks.attendance.entity.WorkPolicy;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AttendanceDailyResponse {

    private final Long attendanceId;
    private final LocalDate attendanceDate;
    private final LocalDateTime scheduledCheckInAt;
    private final LocalDateTime scheduledCheckOutAt;
    private final LocalDateTime checkInAt;
    private final LocalDateTime checkOutAt;
    private final AttendanceWorkState workState;
    private final boolean late;
    private final boolean earlyLeave;
    private final boolean checkInAllowed;
    private final boolean checkOutAllowed;

    public static AttendanceDailyResponse from(Attendance attendance) {
        AttendanceWorkState state = attendance.isCheckedOut()
                ? AttendanceWorkState.COMPLETED
                : AttendanceWorkState.WORKING;

        return new AttendanceDailyResponse(
                attendance.getAttendanceId(),
                attendance.getAttendanceDate(),
                attendance.getScheduledCheckInAt(),
                attendance.getScheduledCheckOutAt(),
                attendance.getCheckInAt(),
                attendance.getCheckOutAt(),
                state,
                attendance.isLate(),
                attendance.isEarlyLeave(),
                false,
                !attendance.isCheckedOut()
        );
    }

    public static AttendanceDailyResponse notStarted(
            LocalDate date,
            WorkPolicy policy,
            boolean checkInAllowed
    ) {
        return new AttendanceDailyResponse(
                null,
                date,
                policy.scheduledCheckInAt(date),
                policy.scheduledCheckOutAt(date),
                null,
                null,
                AttendanceWorkState.NOT_STARTED,
                false,
                false,
                checkInAllowed,
                false
        );
    }

    public static AttendanceDailyResponse absent(
            LocalDate date,
            WorkPolicy policy,
            boolean checkInAllowed
    ) {
        return new AttendanceDailyResponse(
                null,
                date,
                policy.scheduledCheckInAt(date),
                policy.scheduledCheckOutAt(date),
                null,
                null,
                AttendanceWorkState.ABSENT,
                false,
                false,
                checkInAllowed,
                false
        );
    }

    public static AttendanceDailyResponse dayOff(LocalDate date) {
        return new AttendanceDailyResponse(
                null,
                date,
                null,
                null,
                null,
                null,
                AttendanceWorkState.DAY_OFF,
                false,
                false,
                false,
                false
        );
    }
}
