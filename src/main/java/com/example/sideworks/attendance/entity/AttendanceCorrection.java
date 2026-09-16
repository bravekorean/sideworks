package com.example.sideworks.attendance.entity;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.common.entity.BaseCreatedEntity;
import com.example.sideworks.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.Objects;

@Entity @Table(name = "attendance_correctiontbl",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "pending_date"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceCorrection extends BaseCreatedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attendanceCorrectionId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "approval_id", nullable = false, unique = true)
    private Approval approval;
    @Column(nullable = false) private LocalDate attendanceDate;
    // 진행 중인 신청만 날짜를 유지한다. MySQL nullable UNIQUE로 동시 중복 신청도 방어한다.
    private LocalDate pendingDate;
    private Long originalAttendanceId;
    private Long originalVersion;
    private LocalDateTime originalCheckInAt;
    private LocalDateTime originalCheckOutAt;
    @Column(nullable = false) private LocalDateTime requestedCheckInAt;
    private LocalDateTime requestedCheckOutAt;
    @Column(nullable = false, length = 1000) private String reason;

    public static AttendanceCorrection create(User user, Approval approval, LocalDate date,
            Attendance original, LocalDateTime checkIn, LocalDateTime checkOut, String reason) {
        AttendanceCorrection c = new AttendanceCorrection();
        c.user = user;
        c.approval = approval;
        c.attendanceDate = date;
        c.pendingDate = date;
        c.originalAttendanceId = original == null ? null : original.getAttendanceId();
        c.originalVersion = original == null ? null : original.getVersion();
        c.originalCheckInAt = original == null ? null : original.getCheckInAt();
        c.originalCheckOutAt = original == null ? null : original.getCheckOutAt();
        c.requestedCheckInAt = checkIn;
        c.requestedCheckOutAt = checkOut;
        c.reason = reason;
        return c;
    }

    public boolean matches(Attendance current) {
        return current == null ? originalAttendanceId == null
                : Objects.equals(originalAttendanceId, current.getAttendanceId())
                && Objects.equals(originalVersion, current.getVersion())
                && Objects.equals(originalCheckInAt, current.getCheckInAt())
                && Objects.equals(originalCheckOutAt, current.getCheckOutAt());
    }
    public void finish() { pendingDate = null; }
}
