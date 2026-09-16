package com.example.sideworks.attendance.entity;

import com.example.sideworks.common.entity.BaseTimeEntity;
import com.example.sideworks.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "attendancetbl",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_attendance_user_date", columnNames = {"user_id", "attendance_date"})
        }
        )
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendance_id")
    private Long attendanceId;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_policy_id", nullable = false)
    private WorkPolicy workPolicy;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in_at", nullable = false)
    private LocalDateTime checkInAt;

    @Column(name = "check_out_at")
    private LocalDateTime checkOutAt;

    @Column(name = "scheduled_check_in_at", nullable = false)
    private LocalDateTime scheduledCheckInAt;

    @Column(name = "scheduled_check_out_at", nullable = false)
    private LocalDateTime scheduledCheckOutAt;

    @Column(name = "late_grace_minutes", nullable = false)
    private int lateGraceMinutes;

    @Column(name = "is_late", nullable = false)
    private boolean late;

    @Column(name = "is_early_leave", nullable = false)
    private boolean earlyLeave;

    public static Attendance createCheckIn(User user, WorkPolicy workPolicy, LocalDateTime checkInAt) {
        Objects.requireNonNull(user);
        Objects.requireNonNull(workPolicy);
        Objects.requireNonNull(checkInAt);

        LocalDate attendanceDate = checkInAt.toLocalDate();

        if (!workPolicy.isApplicableOn(attendanceDate)) {
            throw new IllegalStateException("해당 날짜에 적용할 수 없는 근무정책입니다.");
        }

        Attendance attendance = new Attendance();
        attendance.user = user;
        attendance.workPolicy = workPolicy;
        attendance.attendanceDate = attendanceDate;
        attendance.checkInAt = checkInAt;
        attendance.scheduledCheckInAt =
                workPolicy.scheduledCheckInAt(attendanceDate);
        attendance.scheduledCheckOutAt =
                workPolicy.scheduledCheckOutAt(attendanceDate);
        attendance.lateGraceMinutes =
                workPolicy.getLateGraceMinutes();
        attendance.late = checkInAt.isAfter(attendance.scheduledCheckInAt.plusMinutes(attendance.lateGraceMinutes));
        attendance.earlyLeave = false;

        return attendance;
    }

    public void checkOut(LocalDateTime checkOutAt) {
        Objects.requireNonNull(checkOutAt);

        if (this.checkOutAt != null) {
            throw new IllegalStateException("이미 퇴근 처리된 근태입니다.");
        }

        if (!checkOutAt.toLocalDate().equals(attendanceDate)) {
            throw new IllegalStateException("출근일과 같은 날짜에만 퇴근할 수 있습니다.");
        }

        if (checkOutAt.isBefore(checkInAt)) {
            throw new IllegalStateException("퇴근 시각은 출근 시각보다 빠를 수 없습니다.");
        }

        this.checkOutAt = checkOutAt;
        this.earlyLeave = checkOutAt.isBefore(scheduledCheckOutAt);
    }

    public boolean isCheckedOut() {
        return checkOutAt != null;
    }

    public void correct(LocalDateTime checkInAt, LocalDateTime checkOutAt) {
        if (checkInAt == null || !checkInAt.toLocalDate().equals(attendanceDate)
                || (checkOutAt != null && (!checkOutAt.toLocalDate().equals(attendanceDate)
                || checkOutAt.isBefore(checkInAt)))) {
            throw new IllegalArgumentException("같은 날짜의 유효한 출퇴근 시각이 필요합니다.");
        }
        this.checkInAt = checkInAt;
        this.checkOutAt = checkOutAt;
        this.late = checkInAt.isAfter(scheduledCheckInAt.plusMinutes(lateGraceMinutes));
        this.earlyLeave = checkOutAt != null && checkOutAt.isBefore(scheduledCheckOutAt);
    }
}
