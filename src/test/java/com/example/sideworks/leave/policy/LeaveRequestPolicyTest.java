package com.example.sideworks.leave.policy;

import com.example.sideworks.attendance.entity.WorkPolicy;
import com.example.sideworks.attendance.entity.WorkPolicyDay;
import com.example.sideworks.attendance.entity.WorkPolicyStatus;
import com.example.sideworks.attendance.entity.WorkScheduleException;
import com.example.sideworks.attendance.repository.WorkPolicyDayRepository;
import com.example.sideworks.attendance.repository.WorkPolicyRepository;
import com.example.sideworks.attendance.repository.WorkScheduleExceptionRepository;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.leave.entity.LeavePeriod;
import com.example.sideworks.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveRequestPolicyTest {
    @Mock WorkPolicyRepository policies;
    @Mock WorkPolicyDayRepository days;
    @Mock WorkScheduleExceptionRepository exceptions;

    private LeaveRequestPolicy policy() {
        return new LeaveRequestPolicy(policies, days, exceptions,
                Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    void 다일_연차는_휴무일을_제외하고_근무일만_계산한다() {
        User user = mock(User.class);
        when(user.getHireDate()).thenReturn(LocalDate.of(2020, 1, 1));
        WorkPolicy workPolicy = mock(WorkPolicy.class);
        when(workPolicy.getWorkPolicyId()).thenReturn(1L);
        when(workPolicy.isApplicableOn(any())).thenReturn(true);
        when(policies.findPoliciesOverlapping(eq(WorkPolicyStatus.ACTIVE), any(), any()))
                .thenReturn(List.of(workPolicy));
        WorkPolicyDay friday = day(workPolicy, DayOfWeek.FRIDAY, true);
        WorkPolicyDay saturday = day(workPolicy, DayOfWeek.SATURDAY, false);
        WorkPolicyDay sunday = day(workPolicy, DayOfWeek.SUNDAY, false);
        WorkPolicyDay monday = day(workPolicy, DayOfWeek.MONDAY, true);
        when(days.findAllByWorkPolicy_WorkPolicyIdIn(List.of(1L)))
                .thenReturn(List.of(friday, saturday, sunday, monday));
        when(exceptions.findAllByExceptionDateBetweenOrderByExceptionDateAsc(any(), any()))
                .thenReturn(List.of());

        var plan = policy().plan(user, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 28),
                LeavePeriod.FULL);

        assertThat(plan.days()).extracting(LeaveRequestPolicy.Day::date)
                .containsExactly(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 28));
        assertThat(plan.totalDays()).isEqualByComparingTo("2.0");
    }

    @Test
    void 반차는_하루에만_신청한다() {
        User user = mock(User.class);
        assertThatThrownBy(() -> policy().plan(user, LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 9, 26), LeavePeriod.AM)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(policies, days, exceptions);
    }

    @Test
    void 예외_근무일은_기본_요일_설정보다_우선한다() {
        User user = mock(User.class);
        when(user.getHireDate()).thenReturn(LocalDate.of(2020, 1, 1));
        WorkPolicy workPolicy = mock(WorkPolicy.class);
        when(workPolicy.getWorkPolicyId()).thenReturn(1L);
        when(workPolicy.isApplicableOn(any())).thenReturn(true);
        LocalDate date = LocalDate.of(2026, 9, 26);
        when(policies.findPoliciesOverlapping(WorkPolicyStatus.ACTIVE, date, date))
                .thenReturn(List.of(workPolicy));
        when(days.findAllByWorkPolicy_WorkPolicyIdIn(List.of(1L))).thenReturn(List.of());
        WorkScheduleException exception = mock(WorkScheduleException.class);
        when(exception.isActive()).thenReturn(true);
        when(exception.getExceptionDate()).thenReturn(date);
        when(exception.isWorking()).thenReturn(true);
        when(exceptions.findAllByExceptionDateBetweenOrderByExceptionDateAsc(date, date))
                .thenReturn(List.of(exception));

        var plan = policy().plan(user, date, date, LeavePeriod.FULL);

        assertThat(plan.totalDays()).isEqualByComparingTo("1.0");
    }

    private WorkPolicyDay day(WorkPolicy policy, DayOfWeek weekday, boolean working) {
        WorkPolicyDay day = mock(WorkPolicyDay.class);
        when(day.getWorkPolicy()).thenReturn(policy);
        when(day.getDayOfWeek()).thenReturn(weekday);
        when(day.isWorking()).thenReturn(working);
        return day;
    }
}
