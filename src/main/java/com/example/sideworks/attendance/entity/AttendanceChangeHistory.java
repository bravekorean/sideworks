package com.example.sideworks.attendance.entity;

import com.example.sideworks.common.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name = "attendance_change_historytbl")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceChangeHistory extends BaseCreatedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attendanceChangeHistoryId;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Long actorId;
    private Long approvalId;
    @Column(nullable = false, length = 30) private String source;
    @Column(nullable = false) private LocalDate attendanceDate;
    private LocalDateTime beforeCheckInAt;
    private LocalDateTime beforeCheckOutAt;
    private Boolean beforeLate;
    private Boolean beforeEarlyLeave;
    @Column(nullable = false) private LocalDateTime afterCheckInAt;
    private LocalDateTime afterCheckOutAt;
    @Column(nullable = false) private boolean afterLate;
    @Column(nullable = false) private boolean afterEarlyLeave;
    @Column(nullable = false, length = 1000) private String reason;

    public static AttendanceChangeHistory before(Long userId, Long actorId, Long approvalId,
            LocalDate date, Attendance original, String reason) {
        AttendanceChangeHistory h = new AttendanceChangeHistory();
        h.userId = userId;
        h.actorId = actorId;
        h.approvalId = approvalId;
        h.source = approvalId == null ? "SUPER_ADMIN_DIRECT" : "APPROVAL";
        h.attendanceDate = date;
        h.reason = reason;
        if (original != null) {
            h.beforeCheckInAt = original.getCheckInAt();
            h.beforeCheckOutAt = original.getCheckOutAt();
            h.beforeLate = original.isLate();
            h.beforeEarlyLeave = original.isEarlyLeave();
        }
        return h;
    }
    public void recordAfter(Attendance changed) {
        this.afterCheckInAt = changed.getCheckInAt();
        this.afterCheckOutAt = changed.getCheckOutAt();
        this.afterLate = changed.isLate();
        this.afterEarlyLeave = changed.isEarlyLeave();
    }
}
