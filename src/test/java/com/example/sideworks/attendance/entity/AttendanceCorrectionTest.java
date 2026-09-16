package com.example.sideworks.attendance.entity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class AttendanceCorrectionTest {
    private Attendance record() {
        Attendance a = BeanUtils.instantiateClass(Attendance.class);
        ReflectionTestUtils.setField(a, "attendanceDate", LocalDateTime.parse("2026-09-16T09:00").toLocalDate());
        ReflectionTestUtils.setField(a, "scheduledCheckInAt", LocalDateTime.parse("2026-09-16T09:00"));
        ReflectionTestUtils.setField(a, "scheduledCheckOutAt", LocalDateTime.parse("2026-09-16T18:00"));
        return a;
    }
    @Test void 정정시_지각과_조퇴를_재계산한다() {
        Attendance a = record();
        a.correct(LocalDateTime.parse("2026-09-16T10:00"), LocalDateTime.parse("2026-09-16T17:00"));
        assertThat(a.isLate()).isTrue();
        assertThat(a.isEarlyLeave()).isTrue();
        a.correct(LocalDateTime.parse("2026-09-16T09:00"), LocalDateTime.parse("2026-09-16T18:00"));
        assertThat(a.isLate()).isFalse();
        assertThat(a.isEarlyLeave()).isFalse();
    }
    @Test void 출근보다_빠른_퇴근과_다른날짜를_거절한다() {
        Attendance a = record();
        assertThatIllegalArgumentException().isThrownBy(() -> a.correct(
                LocalDateTime.parse("2026-09-16T09:00"), LocalDateTime.parse("2026-09-16T08:59")));
        assertThatIllegalArgumentException().isThrownBy(() -> a.correct(
                LocalDateTime.parse("2026-09-17T09:00"), null));
    }
}
