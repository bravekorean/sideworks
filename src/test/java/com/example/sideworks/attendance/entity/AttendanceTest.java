package com.example.sideworks.attendance.entity;

import com.example.sideworks.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AttendanceTest {

    private static final LocalDate WORK_DATE = LocalDate.of(2026, 9, 15);

    @Test
    void 출근_기준_시각과_같으면_지각이_아니다() {
        Attendance attendance = Attendance.createCheckIn(
                mock(User.class),
                activePolicy(0),
                WORK_DATE.atTime(9, 0)
        );

        assertThat(attendance.isLate()).isFalse();
        assertThat(attendance.getAttendanceDate()).isEqualTo(WORK_DATE);
        assertThat(attendance.getScheduledCheckInAt()).isEqualTo(WORK_DATE.atTime(9, 0));
    }

    @Test
    void 지각_유예시간을_초과하면_지각이다() {
        Attendance attendance = Attendance.createCheckIn(
                mock(User.class),
                activePolicy(5),
                WORK_DATE.atTime(9, 5, 1)
        );

        assertThat(attendance.isLate()).isTrue();
        assertThat(attendance.getLateGraceMinutes()).isEqualTo(5);
    }

    @Test
    void 예정_퇴근보다_빠르면_조퇴다() {
        Attendance attendance = checkedInAt(WORK_DATE.atTime(9, 0));

        attendance.checkOut(WORK_DATE.atTime(17, 59));

        assertThat(attendance.isEarlyLeave()).isTrue();
        assertThat(attendance.isCheckedOut()).isTrue();
    }

    @Test
    void 예정_퇴근과_같으면_조퇴가_아니다() {
        Attendance attendance = checkedInAt(WORK_DATE.atTime(9, 0));

        attendance.checkOut(WORK_DATE.atTime(18, 0));

        assertThat(attendance.isEarlyLeave()).isFalse();
    }

    @Test
    void 이미_퇴근한_근태는_다시_퇴근할_수_없다() {
        Attendance attendance = checkedInAt(WORK_DATE.atTime(9, 0));
        attendance.checkOut(WORK_DATE.atTime(18, 0));

        assertThatThrownBy(() -> attendance.checkOut(WORK_DATE.atTime(18, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 퇴근 처리된 근태입니다.");
    }

    @Test
    void 출근일과_다른_날에는_퇴근할_수_없다() {
        Attendance attendance = checkedInAt(WORK_DATE.atTime(9, 0));

        assertThatThrownBy(() -> attendance.checkOut(WORK_DATE.plusDays(1).atStartOfDay()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("출근일과 같은 날짜에만 퇴근할 수 있습니다.");
    }

    @Test
    void 적용_기간이_아닌_근무정책으로는_출근을_생성할_수_없다() {
        WorkPolicy policy = activePolicy(0);
        ReflectionTestUtils.setField(policy, "effectiveFrom", WORK_DATE.plusDays(1));

        assertThatThrownBy(() -> Attendance.createCheckIn(
                mock(User.class),
                policy,
                WORK_DATE.atTime(9, 0)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("해당 날짜에 적용할 수 없는 근무정책입니다.");
    }

    private Attendance checkedInAt(LocalDateTime checkInAt) {
        return Attendance.createCheckIn(mock(User.class), activePolicy(0), checkInAt);
    }

    private WorkPolicy activePolicy(int lateGraceMinutes) {
        WorkPolicy policy = new WorkPolicy();
        ReflectionTestUtils.setField(policy, "workStartTime", LocalTime.of(9, 0));
        ReflectionTestUtils.setField(policy, "workEndTime", LocalTime.of(18, 0));
        ReflectionTestUtils.setField(policy, "lateGraceMinutes", lateGraceMinutes);
        ReflectionTestUtils.setField(policy, "effectiveFrom", LocalDate.of(2026, 1, 1));
        ReflectionTestUtils.setField(policy, "effectiveTo", null);
        ReflectionTestUtils.setField(policy, "status", WorkPolicyStatus.ACTIVE);
        return policy;
    }
}
