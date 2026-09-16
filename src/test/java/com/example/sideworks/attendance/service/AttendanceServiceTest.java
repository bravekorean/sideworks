package com.example.sideworks.attendance.service;

import com.example.sideworks.attendance.config.AttendanceProperties;
import com.example.sideworks.attendance.dto.AttendanceDailyResponse;
import com.example.sideworks.attendance.dto.AttendanceWorkState;
import com.example.sideworks.attendance.entity.Attendance;
import com.example.sideworks.attendance.entity.WorkPolicy;
import com.example.sideworks.attendance.entity.WorkPolicyDay;
import com.example.sideworks.attendance.entity.WorkPolicyStatus;
import com.example.sideworks.attendance.entity.WorkScheduleException;
import com.example.sideworks.attendance.repository.AttendanceRepository;
import com.example.sideworks.attendance.repository.WorkPolicyDayRepository;
import com.example.sideworks.attendance.repository.WorkPolicyRepository;
import com.example.sideworks.attendance.repository.WorkScheduleExceptionRepository;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final String LOGIN_ID = "employee";
    private static final Long USER_ID = 1L;
    private static final Long POLICY_ID = 10L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 15);

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private WorkPolicyRepository workPolicyRepository;
    @Mock
    private WorkPolicyDayRepository workPolicyDayRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WorkScheduleExceptionRepository scheduleExceptionRepository;

    private AttendanceService attendanceService;
    private User user;
    private WorkPolicy policy;
    private WorkPolicyDay policyDay;

    @BeforeEach
    void setUp() {
        attendanceService = serviceAt("2026-09-15T00:00:00Z");
        user = mock(User.class);
        policy = mock(WorkPolicy.class);
        policyDay = mock(WorkPolicyDay.class);
    }

    @Test
    void 근무일에_출근하면_근무중_상태를_반환한다() {
        prepareActiveUser();
        when(user.getUserId()).thenReturn(USER_ID);
        preparePolicy(true);
        preparePolicyForAttendanceCreation();
        when(attendanceRepository.existsByUser_UserIdAndAttendanceDate(USER_ID, TODAY)).thenReturn(false);
        when(attendanceRepository.saveAndFlush(any(Attendance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AttendanceDailyResponse response = attendanceService.checkIn(LOGIN_ID);

        assertThat(response.getWorkState()).isEqualTo(AttendanceWorkState.WORKING);
        assertThat(response.getCheckInAt()).isEqualTo(TODAY.atTime(9, 0));
        assertThat(response.isLate()).isFalse();
        assertThat(response.isCheckInAllowed()).isFalse();
        assertThat(response.isCheckOutAllowed()).isTrue();
        verify(attendanceRepository).saveAndFlush(any(Attendance.class));
    }

    @Test
    void 비근무일에는_출근할_수_없다() {
        prepareActiveUser();
        preparePolicy(false);

        assertThatThrownBy(() -> attendanceService.checkIn(LOGIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ATTENDANCE_NOT_WORKING_DAY);

        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void 이미_출근한_날에는_다시_출근할_수_없다() {
        prepareActiveUser();
        when(user.getUserId()).thenReturn(USER_ID);
        preparePolicy(true);
        when(attendanceRepository.existsByUser_UserIdAndAttendanceDate(USER_ID, TODAY)).thenReturn(true);

        assertThatThrownBy(() -> attendanceService.checkIn(LOGIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ATTENDANCE_ALREADY_CHECKED_IN);
    }

    @Test
    void 출근_마감_검증이_비활성화되면_18시_이후에도_출근할_수_있다() {
        attendanceService = serviceAt("2026-09-15T13:26:00Z", false);
        prepareSuccessfulCheckIn();

        AttendanceDailyResponse response = attendanceService.checkIn(LOGIN_ID);

        assertThat(response.getWorkState()).isEqualTo(AttendanceWorkState.WORKING);
        assertThat(response.getCheckInAt()).isEqualTo(TODAY.atTime(22, 26));
    }

    @Test
    void 출근_마감_검증이_활성화되어도_18시_전에는_출근할_수_있다() {
        attendanceService = serviceAt("2026-09-15T08:59:00Z", true);
        prepareSuccessfulCheckIn();

        AttendanceDailyResponse response = attendanceService.checkIn(LOGIN_ID);

        assertThat(response.getCheckInAt()).isEqualTo(TODAY.atTime(17, 59));
    }

    @Test
    void 출근_마감_검증이_활성화되면_18시부터_출근할_수_없다() {
        attendanceService = serviceAt("2026-09-15T09:00:00Z", true);
        prepareActiveUser();
        when(user.getUserId()).thenReturn(USER_ID);
        preparePolicy(true);
        when(policy.scheduledCheckOutAt(TODAY)).thenReturn(TODAY.atTime(18, 0));
        when(attendanceRepository.existsByUser_UserIdAndAttendanceDate(USER_ID, TODAY)).thenReturn(false);

        assertThatThrownBy(() -> attendanceService.checkIn(LOGIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ATTENDANCE_CHECK_IN_CLOSED);

        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void 출근_기록이_없으면_퇴근할_수_없다() {
        prepareActiveUser();
        when(user.getUserId()).thenReturn(USER_ID);
        when(attendanceRepository.findByUserIdAndAttendanceDateForUpdate(USER_ID, TODAY))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.checkOut(LOGIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ATTENDANCE_NOT_CHECKED_IN);
    }

    @Test
    void 출근한_사용자는_퇴근할_수_있다() {
        prepareActiveUser();
        when(user.getUserId()).thenReturn(USER_ID);
        Attendance attendance = attendanceAt(TODAY.atTime(8, 50));
        when(attendanceRepository.findByUserIdAndAttendanceDateForUpdate(USER_ID, TODAY))
                .thenReturn(Optional.of(attendance));

        AttendanceDailyResponse response = attendanceService.checkOut(LOGIN_ID);

        assertThat(response.getWorkState()).isEqualTo(AttendanceWorkState.COMPLETED);
        assertThat(response.getCheckOutAt()).isEqualTo(TODAY.atTime(9, 0));
        assertThat(response.isEarlyLeave()).isTrue();
        assertThat(response.isCheckOutAllowed()).isFalse();
    }

    @Test
    void 근무일_18시_전까지_출근_기록이_없으면_출근전_상태다() {
        attendanceService = serviceAt("2026-09-15T08:59:00Z", true);
        prepareEmptyToday(true);

        AttendanceDailyResponse response = attendanceService.getToday(LOGIN_ID);

        assertThat(response.getWorkState()).isEqualTo(AttendanceWorkState.NOT_STARTED);
        assertThat(response.isCheckInAllowed()).isTrue();
    }

    @Test
    void 근무일_18시부터_출근_기록이_없으면_결근이다() {
        attendanceService = serviceAt("2026-09-15T09:00:00Z", true);
        prepareEmptyToday(true);

        AttendanceDailyResponse response = attendanceService.getToday(LOGIN_ID);

        assertThat(response.getWorkState()).isEqualTo(AttendanceWorkState.ABSENT);
        assertThat(response.isCheckInAllowed()).isFalse();
    }

    @Test
    void 출근_마감이_비활성화되면_결근_표시_후에도_로컬_출근_테스트가_가능하다() {
        attendanceService = serviceAt("2026-09-15T13:26:00Z", false);
        prepareEmptyToday(true);

        AttendanceDailyResponse response = attendanceService.getToday(LOGIN_ID);

        assertThat(response.getWorkState()).isEqualTo(AttendanceWorkState.ABSENT);
        assertThat(response.isCheckInAllowed()).isTrue();
    }

    @Test
    void 오늘이_비근무일이면_DAY_OFF를_반환한다() {
        prepareActiveUser();
        when(user.getUserId()).thenReturn(USER_ID);
        when(attendanceRepository.findByUser_UserIdAndAttendanceDate(USER_ID, TODAY))
                .thenReturn(Optional.empty());
        preparePolicy(false);

        AttendanceDailyResponse response = attendanceService.getToday(LOGIN_ID);

        assertThat(response.getWorkState()).isEqualTo(AttendanceWorkState.DAY_OFF);
        assertThat(response.getCheckInAt()).isNull();
        assertThat(response.isCheckInAllowed()).isFalse();
    }

    @Test
    void 날짜별_휴무일은_요일별_근무설정보다_우선한다() {
        prepareActiveUser();
        when(user.getUserId()).thenReturn(USER_ID);
        when(attendanceRepository.findByUser_UserIdAndAttendanceDate(USER_ID, TODAY))
                .thenReturn(Optional.empty());
        when(workPolicyRepository.findApplicablePolicies(WorkPolicyStatus.ACTIVE, TODAY))
                .thenReturn(List.of(policy));
        WorkScheduleException holiday = mock(WorkScheduleException.class);
        when(holiday.isWorking()).thenReturn(false);
        when(scheduleExceptionRepository.findByExceptionDateAndActiveTrue(TODAY))
                .thenReturn(Optional.of(holiday));

        AttendanceDailyResponse response = attendanceService.getToday(LOGIN_ID);

        assertThat(response.getWorkState()).isEqualTo(AttendanceWorkState.DAY_OFF);
        verify(workPolicyDayRepository, never())
                .findByWorkPolicy_WorkPolicyIdAndDayOfWeek(any(), any());
    }

    @Test
    void 월별_조회는_출근_기록이_없는_지난_근무일을_결근으로_반환한다() {
        LocalDate septemberFirst = LocalDate.of(2026, 9, 1);
        attendanceService = serviceAt("2026-09-01T09:00:00Z", true);
        prepareActiveUser();
        when(user.getUserId()).thenReturn(USER_ID);
        when(attendanceRepository.findAllByUser_UserIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                USER_ID,
                septemberFirst,
                septemberFirst.withDayOfMonth(30)
        )).thenReturn(List.of());
        when(workPolicyRepository.findPoliciesOverlapping(
                WorkPolicyStatus.ACTIVE,
                septemberFirst,
                septemberFirst.withDayOfMonth(30)
        )).thenReturn(List.of(policy));
        when(policy.getWorkPolicyId()).thenReturn(POLICY_ID);
        when(policy.isApplicableOn(septemberFirst)).thenReturn(true);
        when(policy.scheduledCheckInAt(septemberFirst)).thenReturn(septemberFirst.atTime(9, 0));
        when(policy.scheduledCheckOutAt(septemberFirst)).thenReturn(septemberFirst.atTime(18, 0));
        WorkPolicyDay monthlyPolicyDay = mock(WorkPolicyDay.class);
        when(monthlyPolicyDay.getWorkPolicy()).thenReturn(policy);
        when(monthlyPolicyDay.getDayOfWeek()).thenReturn(septemberFirst.getDayOfWeek());
        when(monthlyPolicyDay.isWorking()).thenReturn(true);
        when(workPolicyDayRepository.findAllByWorkPolicy_WorkPolicyIdIn(List.of(POLICY_ID)))
                .thenReturn(List.of(monthlyPolicyDay));

        List<AttendanceDailyResponse> responses = attendanceService.getMonthly(LOGIN_ID, 2026, 9);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.getAttendanceDate()).isEqualTo(septemberFirst);
            assertThat(response.getWorkState()).isEqualTo(AttendanceWorkState.ABSENT);
            assertThat(response.isCheckInAllowed()).isFalse();
        });
    }

    @Test
    void 적용_가능한_정책이_여러_개면_설정_오류를_반환한다() {
        prepareActiveUser();
        when(workPolicyRepository.findApplicablePolicies(WorkPolicyStatus.ACTIVE, TODAY))
                .thenReturn(List.of(policy, mock(WorkPolicy.class)));

        assertThatThrownBy(() -> attendanceService.checkIn(LOGIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WORK_POLICY_CONFIGURATION_CONFLICT);
    }

    @Test
    void 올바르지_않은_월은_조회할_수_없다() {
        prepareActiveUser();

        assertThatThrownBy(() -> attendanceService.getMonthly(LOGIN_ID, 2026, 13))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 미래_휴무일은_표시하고_미래_근무일은_판정하지_않는다() {
        prepareActiveUser();
        when(user.getUserId()).thenReturn(USER_ID);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        LocalDate holidayDate = LocalDate.of(2026, 9, 24);
        LocalDate workingDate = LocalDate.of(2026, 9, 25);
        when(workPolicyRepository.findPoliciesOverlapping(WorkPolicyStatus.ACTIVE, from, to))
                .thenReturn(List.of(policy));
        when(policy.getWorkPolicyId()).thenReturn(POLICY_ID);
        when(policy.isApplicableOn(any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(0);
            return date.equals(holidayDate) || date.equals(workingDate);
        });
        when(scheduleExceptionRepository.findAllByExceptionDateBetweenOrderByExceptionDateAsc(from, to))
                .thenReturn(List.of(
                        WorkScheduleException.create(holidayDate, "추석 휴무", false),
                        WorkScheduleException.create(workingDate, "예외 근무일", true)));

        List<AttendanceDailyResponse> responses = attendanceService.getMonthly(LOGIN_ID, 2026, 9);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.getAttendanceDate()).isEqualTo(holidayDate);
            assertThat(response.getWorkState()).isEqualTo(AttendanceWorkState.DAY_OFF);
            assertThat(response.isCheckInAllowed()).isFalse();
        });
    }

    private AttendanceService serviceAt(String instant) {
        return serviceAt(instant, false);
    }

    private AttendanceService serviceAt(String instant, boolean checkInDeadlineEnabled) {
        Clock clock = Clock.fixed(
                Instant.parse(instant),
                ZoneId.of("Asia/Seoul")
        );
        AttendanceProperties attendanceProperties = new AttendanceProperties();
        attendanceProperties.setCheckInDeadlineEnabled(checkInDeadlineEnabled);
        return new AttendanceService(
                attendanceRepository,
                workPolicyRepository,
                workPolicyDayRepository,
                userRepository,
                clock,
                attendanceProperties,
                scheduleExceptionRepository
        );
    }

    private void prepareSuccessfulCheckIn() {
        prepareActiveUser();
        when(user.getUserId()).thenReturn(USER_ID);
        preparePolicy(true);
        preparePolicyForAttendanceCreation();
        when(attendanceRepository.existsByUser_UserIdAndAttendanceDate(USER_ID, TODAY)).thenReturn(false);
        when(attendanceRepository.saveAndFlush(any(Attendance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void prepareEmptyToday(boolean working) {
        prepareActiveUser();
        when(user.getUserId()).thenReturn(USER_ID);
        when(attendanceRepository.findByUser_UserIdAndAttendanceDate(USER_ID, TODAY))
                .thenReturn(Optional.empty());
        preparePolicy(working);
        if (working) {
            when(policy.scheduledCheckInAt(TODAY)).thenReturn(TODAY.atTime(9, 0));
            when(policy.scheduledCheckOutAt(TODAY)).thenReturn(TODAY.atTime(18, 0));
        }
    }

    private void prepareActiveUser() {
        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
    }

    private void preparePolicyForAttendanceCreation() {
        when(policy.isApplicableOn(TODAY)).thenReturn(true);
        when(policy.scheduledCheckInAt(TODAY)).thenReturn(TODAY.atTime(9, 0));
        when(policy.scheduledCheckOutAt(TODAY)).thenReturn(TODAY.atTime(18, 0));
        when(policy.getLateGraceMinutes()).thenReturn(0);
    }

    private void preparePolicy(boolean working) {
        when(workPolicyRepository.findApplicablePolicies(WorkPolicyStatus.ACTIVE, TODAY))
                .thenReturn(List.of(policy));
        when(policy.getWorkPolicyId()).thenReturn(POLICY_ID);
        when(workPolicyDayRepository.findByWorkPolicy_WorkPolicyIdAndDayOfWeek(
                POLICY_ID,
                TODAY.getDayOfWeek()
        )).thenReturn(Optional.of(policyDay));
        when(policyDay.isWorking()).thenReturn(working);
    }

    private Attendance attendanceAt(LocalDateTime checkInAt) {
        when(policy.isApplicableOn(TODAY)).thenReturn(true);
        when(policy.scheduledCheckInAt(TODAY)).thenReturn(TODAY.atTime(9, 0));
        when(policy.scheduledCheckOutAt(TODAY)).thenReturn(TODAY.atTime(18, 0));
        when(policy.getLateGraceMinutes()).thenReturn(0);
        return Attendance.createCheckIn(user, policy, checkInAt);
    }
}
