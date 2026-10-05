package com.example.sideworks.leave.policy;

import com.example.sideworks.attendance.entity.WorkPolicy;
import com.example.sideworks.attendance.entity.WorkPolicyDay;
import com.example.sideworks.attendance.entity.WorkPolicyStatus;
import com.example.sideworks.attendance.entity.WorkScheduleException;
import com.example.sideworks.attendance.repository.WorkPolicyDayRepository;
import com.example.sideworks.attendance.repository.WorkPolicyRepository;
import com.example.sideworks.attendance.repository.WorkScheduleExceptionRepository;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.leave.entity.LeavePeriod;
import com.example.sideworks.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LeaveRequestPolicy {
    private final WorkPolicyRepository workPolicies;
    private final WorkPolicyDayRepository policyDays;
    private final WorkScheduleExceptionRepository exceptions;
    private final Clock clock;

    public record Day(LocalDate date, LeavePeriod period) {}
    public record Plan(List<Day> days, BigDecimal totalDays) {}

    public Plan plan(User user, LocalDate start, LocalDate end, LeavePeriod period) {
        if (start == null || end == null || period == null || start.isAfter(end)
                || !start.isAfter(LocalDate.now(clock)) || start.getYear() != end.getYear()
                || (period != LeavePeriod.FULL && !start.equals(end))
                || user.getHireDate() == null || start.isBefore(user.getHireDate())) {
            throw new BusinessException(ErrorCode.LEAVE_INVALID_DATE);
        }

        List<WorkPolicy> policies = workPolicies.findPoliciesOverlapping(WorkPolicyStatus.ACTIVE, start, end);
        if (policies.isEmpty()) throw new BusinessException(ErrorCode.WORK_POLICY_NOT_CONFIGURED);
        Map<Long, Map<java.time.DayOfWeek, WorkPolicyDay>> daysByPolicy = policyDays
                .findAllByWorkPolicy_WorkPolicyIdIn(policies.stream().map(WorkPolicy::getWorkPolicyId).toList())
                .stream().collect(Collectors.groupingBy(day -> day.getWorkPolicy().getWorkPolicyId(),
                        Collectors.toMap(WorkPolicyDay::getDayOfWeek, Function.identity())));
        Map<LocalDate, WorkScheduleException> exceptionsByDate = exceptions
                .findAllByExceptionDateBetweenOrderByExceptionDateAsc(start, end).stream()
                .filter(WorkScheduleException::isActive)
                .collect(Collectors.toMap(WorkScheduleException::getExceptionDate, Function.identity()));

        List<Day> selected = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            LocalDate current = date;
            List<WorkPolicy> applicable = policies.stream().filter(policy -> policy.isApplicableOn(current)).toList();
            if (applicable.isEmpty()) throw new BusinessException(ErrorCode.WORK_POLICY_NOT_CONFIGURED);
            if (applicable.size() != 1) throw new BusinessException(ErrorCode.WORK_POLICY_CONFIGURATION_CONFLICT);
            WorkPolicy policy = applicable.getFirst();
            WorkScheduleException exception = exceptionsByDate.get(date);
            boolean working;
            if (exception != null) {
                working = exception.isWorking();
            } else {
                WorkPolicyDay day = daysByPolicy.getOrDefault(policy.getWorkPolicyId(), Map.of())
                        .get(date.getDayOfWeek());
                if (day == null) throw new BusinessException(ErrorCode.WORK_POLICY_DAY_NOT_CONFIGURED);
                working = day.isWorking();
            }
            if (working) selected.add(new Day(date, period));
        }

        if (selected.isEmpty() || (period != LeavePeriod.FULL && selected.size() != 1)) {
            throw new BusinessException(ErrorCode.LEAVE_NOT_WORKING_DAY);
        }
        BigDecimal total = period.days().multiply(BigDecimal.valueOf(selected.size()));
        if (total.compareTo(new BigDecimal("25.0")) > 0) {
            throw new BusinessException(ErrorCode.LEAVE_INSUFFICIENT_BALANCE);
        }
        return new Plan(List.copyOf(selected), total);
    }
}
