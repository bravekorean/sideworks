package com.example.sideworks.attendance.service;

import com.example.sideworks.attendance.config.AttendanceProperties;
import com.example.sideworks.attendance.dto.AttendanceDailyResponse;
import com.example.sideworks.attendance.entity.Attendance;
import com.example.sideworks.attendance.entity.WorkPolicy;
import com.example.sideworks.attendance.entity.WorkPolicyDay;
import com.example.sideworks.attendance.entity.WorkPolicyStatus;
import com.example.sideworks.attendance.repository.AttendanceRepository;
import com.example.sideworks.attendance.repository.WorkPolicyDayRepository;
import com.example.sideworks.attendance.repository.WorkPolicyRepository;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final WorkPolicyRepository workPolicyRepository;
    private final WorkPolicyDayRepository workPolicyDayRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final AttendanceProperties attendanceProperties;
    private final com.example.sideworks.attendance.repository.WorkScheduleExceptionRepository scheduleExceptions;

    @Transactional
    public AttendanceDailyResponse checkIn(String loginId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        User user = getActiveUser(loginId);
        userRepository.lockAttendanceOwner(user.getUserId());
        WorkPolicy policy = getApplicablePolicy(today);

        if (!isWorking(policy, today)) {
            throw new BusinessException(ErrorCode.ATTENDANCE_NOT_WORKING_DAY);
        }

        if (attendanceRepository.existsByUser_UserIdAndAttendanceDate(user.getUserId(), today)) {
            throw new BusinessException(ErrorCode.ATTENDANCE_ALREADY_CHECKED_IN);
        }

        if (attendanceProperties.isCheckInDeadlineEnabled()
                && !now.isBefore(policy.scheduledCheckOutAt(today))) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CHECK_IN_CLOSED);
        }

        Attendance attendance = Attendance.createCheckIn(user, policy, now);

        try {
            return AttendanceDailyResponse.from(attendanceRepository.saveAndFlush(attendance));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.ATTENDANCE_ALREADY_CHECKED_IN);
        }
    }

    @Transactional
    public AttendanceDailyResponse checkOut(String loginId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        User user = getActiveUser(loginId);
        userRepository.lockAttendanceOwner(user.getUserId());

        Attendance attendance = attendanceRepository
                .findByUserIdAndAttendanceDateForUpdate(user.getUserId(), today)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_NOT_CHECKED_IN));

        if (attendance.isCheckedOut()) {
            throw new BusinessException(ErrorCode.ATTENDANCE_ALREADY_CHECKED_OUT);
        }

        attendance.checkOut(now);
        return AttendanceDailyResponse.from(attendance);
    }

    public AttendanceDailyResponse getToday(String loginId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        User user = getActiveUser(loginId);

        return attendanceRepository.findByUser_UserIdAndAttendanceDate(user.getUserId(), today)
                .map(AttendanceDailyResponse::from)
                .orElseGet(() -> getEmptyToday(now));
    }

    public List<AttendanceDailyResponse> getMonthly(String loginId, int year, int month) {
        User user = getActiveUser(loginId);
        YearMonth yearMonth = getYearMonth(year, month);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate rangeStart = yearMonth.atDay(1);
        // 미래 휴무일도 월 전체에 표시하고, 결근 판정만 오늘까지 적용
        LocalDate rangeEnd = yearMonth.atEndOfMonth();
        // 캘린더에서 오늘 날짜를 기준으로 휴무일 표시, 이전 휴무일만 표시됨.
//        LocalDate rangeEnd = yearMonth.atEndOfMonth().isBefore(now.toLocalDate())
//                ? yearMonth.atEndOfMonth()
//                : now.toLocalDate();

        // 월 시작일이 월말보다 늦을 경우 체크
//        if (rangeStart.isAfter(rangeEnd)) {
//            return List.of();
//        }

        // 입사일 이전 달을 조회하는 경우
        LocalDate firstRelevantDate = getFirstRelevantDate(user, rangeStart);
        if (firstRelevantDate.isAfter(rangeEnd)) {
            return List.of();
        }

        Map<LocalDate, Attendance> attendanceByDate = attendanceRepository
                .findAllByUser_UserIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                        user.getUserId(),
                        firstRelevantDate,
                        rangeEnd
                )
                .stream()
                .collect(Collectors.toMap(Attendance::getAttendanceDate, Function.identity()));

        List<WorkPolicy> policies = workPolicyRepository.findPoliciesOverlapping(
                WorkPolicyStatus.ACTIVE,
                firstRelevantDate,
                rangeEnd
        );
        Map<Long, Map<DayOfWeek, WorkPolicyDay>> policyDays = getPolicyDays(policies);
        Map<LocalDate, Boolean> exceptions = scheduleExceptions
                .findAllByExceptionDateBetweenOrderByExceptionDateAsc(firstRelevantDate, rangeEnd).stream()
                .filter(com.example.sideworks.attendance.entity.WorkScheduleException::isActive)
                .collect(Collectors.toMap(com.example.sideworks.attendance.entity.WorkScheduleException::getExceptionDate,
                        com.example.sideworks.attendance.entity.WorkScheduleException::isWorking));

        return firstRelevantDate.datesUntil(rangeEnd.plusDays(1))
                .map(date -> getMonthlyAttendance(
                        date,
                        now,
                        attendanceByDate.get(date),
                        policies,
                        policyDays,
                        exceptions
                ))
                .flatMap(Optional::stream)
                .toList();
    }

    private AttendanceDailyResponse getEmptyToday(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        WorkPolicy policy = getApplicablePolicy(today);

        if (!isWorking(policy, today)) {
            return AttendanceDailyResponse.dayOff(today);
        }

        if (!now.isBefore(policy.scheduledCheckOutAt(today))) {
            return AttendanceDailyResponse.absent(
                    today,
                    policy,
                    !attendanceProperties.isCheckInDeadlineEnabled()
            );
        }

        return AttendanceDailyResponse.notStarted(today, policy, true);
    }

    private Optional<AttendanceDailyResponse> getMonthlyAttendance(
            LocalDate date,
            LocalDateTime now,
            Attendance attendance,
            List<WorkPolicy> policies,
            Map<Long, Map<DayOfWeek, WorkPolicyDay>> policyDays,
            Map<LocalDate, Boolean> exceptions
    ) {
        if (attendance != null) {
            return Optional.of(AttendanceDailyResponse.from(attendance));
        }

        Optional<WorkPolicy> applicablePolicy = findApplicablePolicy(policies, date);
        if (applicablePolicy.isEmpty()) {
            return Optional.empty();
        }

        WorkPolicy policy = applicablePolicy.get();
        boolean working = exceptions.containsKey(date) ? exceptions.get(date) : getPolicyDay(policyDays, policy, date).isWorking();
        if (!working) {
            return Optional.of(AttendanceDailyResponse.dayOff(date));
        }

        // 미래 휴무일은 표시하되, 미래 근무일은 출퇴근 상태를 판정하지 않는다.
        if (date.isAfter(now.toLocalDate())) {
            return Optional.empty();
        }

        boolean deadlineReached = date.isBefore(now.toLocalDate())
                || !now.isBefore(policy.scheduledCheckOutAt(date));
        if (deadlineReached) {
            boolean checkInAllowed = date.equals(now.toLocalDate())
                    && !attendanceProperties.isCheckInDeadlineEnabled();
            return Optional.of(AttendanceDailyResponse.absent(date, policy, checkInAllowed));
        }

        return Optional.of(AttendanceDailyResponse.notStarted(date, policy, true));
    }

    private LocalDate getFirstRelevantDate(User user, LocalDate rangeStart) {
        LocalDate hireDate = user.getHireDate();
        return hireDate != null && hireDate.isAfter(rangeStart) ? hireDate : rangeStart;
    }

    private Map<Long, Map<DayOfWeek, WorkPolicyDay>> getPolicyDays(List<WorkPolicy> policies) {
        List<Long> policyIds = policies.stream()
                .map(WorkPolicy::getWorkPolicyId)
                .toList();

        if (policyIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Map<DayOfWeek, WorkPolicyDay>> result = new HashMap<>();
        for (WorkPolicyDay policyDay : workPolicyDayRepository
                .findAllByWorkPolicy_WorkPolicyIdIn(policyIds)) {
            result.computeIfAbsent(
                    policyDay.getWorkPolicy().getWorkPolicyId(),
                    ignored -> new EnumMap<>(DayOfWeek.class)
            ).put(policyDay.getDayOfWeek(), policyDay);
        }
        return result;
    }

    private Optional<WorkPolicy> findApplicablePolicy(
            Collection<WorkPolicy> policies,
            LocalDate date
    ) {
        List<WorkPolicy> applicablePolicies = policies.stream()
                .filter(policy -> policy.isApplicableOn(date))
                .toList();

        if (applicablePolicies.size() > 1) {
            throw new BusinessException(ErrorCode.WORK_POLICY_CONFIGURATION_CONFLICT);
        }

        return applicablePolicies.stream().findFirst();
    }

    private WorkPolicyDay getPolicyDay(
            Map<Long, Map<DayOfWeek, WorkPolicyDay>> policyDays,
            WorkPolicy policy,
            LocalDate date
    ) {
        return Optional.ofNullable(policyDays.get(policy.getWorkPolicyId()))
                .map(days -> days.get(date.getDayOfWeek()))
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_POLICY_DAY_NOT_CONFIGURED));
    }

    private User getActiveUser(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        return user;
    }

    private WorkPolicy getApplicablePolicy(LocalDate date) {
        List<WorkPolicy> policies = workPolicyRepository.findApplicablePolicies(
                WorkPolicyStatus.ACTIVE,
                date
        );

        if (policies.isEmpty()) {
            throw new BusinessException(ErrorCode.WORK_POLICY_NOT_CONFIGURED);
        }

        if (policies.size() > 1) {
            throw new BusinessException(ErrorCode.WORK_POLICY_CONFIGURATION_CONFLICT);
        }

        return policies.getFirst();
    }

    private WorkPolicyDay getPolicyDay(WorkPolicy policy, LocalDate date) {
        return workPolicyDayRepository
                .findByWorkPolicy_WorkPolicyIdAndDayOfWeek(
                        policy.getWorkPolicyId(),
                        date.getDayOfWeek()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_POLICY_DAY_NOT_CONFIGURED));
    }

    private boolean isWorking(WorkPolicy policy, LocalDate date) {
        return scheduleExceptions.findByExceptionDateAndActiveTrue(date)
                .map(com.example.sideworks.attendance.entity.WorkScheduleException::isWorking)
                .orElseGet(() -> getPolicyDay(policy, date).isWorking());
    }

    private YearMonth getYearMonth(int year, int month) {
        try {
            return YearMonth.of(year, month);
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
