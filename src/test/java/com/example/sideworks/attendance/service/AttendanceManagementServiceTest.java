package com.example.sideworks.attendance.service;

import com.example.sideworks.attendance.dto.AttendanceWorkState;
import com.example.sideworks.attendance.entity.*;
import com.example.sideworks.attendance.repository.*;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.department.entity.*;
import com.example.sideworks.department.repository.DepartmentRepository;
import com.example.sideworks.user.entity.*;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceManagementServiceTest {
    @Mock UserRepository users;
    @Mock DepartmentRepository departments;
    @Mock AttendanceRepository attendance;
    @Mock WorkPolicyRepository policies;
    @Mock WorkPolicyDayRepository days;
    AttendanceManagementService service;
    final LocalDate date = LocalDate.of(2026, 9, 16);
    Department parent, child, other;

    @BeforeEach
    void setup() {
        service = new AttendanceManagementService(users, departments, attendance, policies, days,
                Clock.fixed(Instant.parse("2026-09-16T09:00:00Z"), ZoneId.of("Asia/Seoul")),
                org.mockito.Mockito.mock(WorkScheduleExceptionRepository.class));
        parent = department(10L, null);
        child = department(11L, parent);
        other = department(20L, null);
    }

    @Test
    void 부서장은_담당부서와_하위부서만_조회한다() {
        viewer(UserRole.USER);
        parent.assignManager(1L);
        assertThat(service.getScope("viewer").departments())
                .extracting("departmentId").containsExactly(10L, 11L);
    }

    @Test
    void 일반직원은_근태목록에_접근할_수_없다() {
        viewer(UserRole.USER);
        assertThatThrownBy(() -> service.getDaily("viewer", date, null, "", 0, 20))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.ATTENDANCE_VIEW_FORBIDDEN);
        verifyNoInteractions(attendance, policies, days);
        verify(users, never()).findAttendanceMembers(any(), any(), anyBoolean(), any(), any(), any(), any());
    }

    @Test
    void 부서ID를_변조해도_다른부서를_조회할_수_없다() {
        viewer(UserRole.USER);
        parent.assignManager(1L);
        assertThatThrownBy(() -> service.getDaily("viewer", date, 20L, "", 0, 20))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.ATTENDANCE_VIEW_FORBIDDEN);
        verifyNoInteractions(attendance);
    }

    @Test
    void 부서필터가_없어도_서버쿼리에_담당범위를_전달한다() {
        viewer(UserRole.USER);
        parent.assignManager(1L);
        when(users.findAttendanceMembers(UserStatus.ACTIVE, date, false, List.of(10L, 11L), null, "", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.getDaily("viewer", date, null, "", 0, 20)).isEmpty();
        verifyNoInteractions(attendance);
    }

    @Test
    void 인사관리자는_전체범위와_서버날짜를_받는다() {
        viewer(UserRole.HR_MANAGER);
        var scope = service.getScope("viewer");
        assertThat(scope.allDepartments()).isTrue();
        assertThat(scope.today()).isEqualTo(date);
        assertThat(scope.departments()).hasSize(3);
    }

    @Test
    void 시스템관리자도_전체범위를_받는다() {
        viewer(UserRole.SUPER_ADMIN);
        assertThat(service.getScope("viewer").allDepartments()).isTrue();
    }

    @Test
    void 비활성관리자는_조회할_수_없다() {
        User viewer = user(1L, UserRole.HR_MANAGER);
        viewer.changeStatus(UserStatus.INACTIVE);
        when(users.findByLoginId("viewer")).thenReturn(Optional.of(viewer));
        assertThatThrownBy(() -> service.getScope("viewer")).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ATTENDANCE_VIEW_FORBIDDEN);
        verifyNoInteractions(departments);
    }

    @Test
    void 미래날짜와_과도한_페이지크기는_거절한다() {
        viewer(UserRole.HR_MANAGER);
        assertThatThrownBy(() -> service.getDaily("viewer", date.plusDays(1), null, "", 0, 20))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getDaily("viewer", date, null, "", 0, 101))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(attendance);
    }

    @Test
    void 근태행이_없는_재직자도_18시부터_결근으로_표시한다() {
        prepareMember();
        preparePolicy(true);
        var response = service.getDaily("viewer", date, null, "", 0, 20).getContent().getFirst();
        assertThat(response.workState()).isEqualTo(AttendanceWorkState.ABSENT);
        assertThat(response.checkInAt()).isNull();
        verify(attendance).findAllByAttendanceDateAndUser_UserIdIn(date, List.of(2L));
    }

    @Test
    void 비근무일의_기록없는_직원은_결근이_아니다() {
        prepareMember();
        preparePolicy(false);
        assertThat(service.getDaily("viewer", date, null, "", 0, 20).getContent().getFirst().workState())
                .isEqualTo(AttendanceWorkState.DAY_OFF);
    }

    @Test
    void 실제기록은_정책이_없어도_그대로_조회한다() {
        User member = prepareMember();
        WorkPolicy policy = org.springframework.beans.BeanUtils.instantiateClass(WorkPolicy.class);
        ReflectionTestUtils.setField(policy, "status", WorkPolicyStatus.ACTIVE);
        ReflectionTestUtils.setField(policy, "effectiveFrom", date);
        ReflectionTestUtils.setField(policy, "workStartTime", LocalTime.of(9, 0));
        ReflectionTestUtils.setField(policy, "workEndTime", LocalTime.of(18, 0));
        Attendance record = Attendance.createCheckIn(member, policy, date.atTime(9, 1));
        record.checkOut(date.atTime(18, 0));
        when(attendance.findAllByAttendanceDateAndUser_UserIdIn(date, List.of(2L))).thenReturn(List.of(record));
        var response = service.getDaily("viewer", date, null, "", 0, 20).getContent().getFirst();
        assertThat(response.workState()).isEqualTo(AttendanceWorkState.COMPLETED);
        assertThat(response.late()).isTrue();
        assertThat(response.checkOutAt()).isEqualTo(date.atTime(18, 0));
        verifyNoInteractions(policies, days);
    }

    private User prepareMember() {
        viewer(UserRole.HR_MANAGER);
        User member = user(2L, UserRole.USER);
        when(users.findAttendanceMembers(UserStatus.ACTIVE, date, true, List.of(10L, 11L, 20L), null, "", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(member)));
        return member;
    }

    private void preparePolicy(boolean working) {
        WorkPolicy policy = org.springframework.beans.BeanUtils.instantiateClass(WorkPolicy.class);
        ReflectionTestUtils.setField(policy, "workPolicyId", 5L);
        ReflectionTestUtils.setField(policy, "workStartTime", LocalTime.of(9, 0));
        ReflectionTestUtils.setField(policy, "workEndTime", LocalTime.of(18, 0));
        WorkPolicyDay day = org.springframework.beans.BeanUtils.instantiateClass(WorkPolicyDay.class);
        ReflectionTestUtils.setField(day, "working", working);
        when(policies.findApplicablePolicies(WorkPolicyStatus.ACTIVE, date)).thenReturn(List.of(policy));
        when(days.findByWorkPolicy_WorkPolicyIdAndDayOfWeek(5L, date.getDayOfWeek())).thenReturn(Optional.of(day));
    }

    private void viewer(UserRole role) {
        when(users.findByLoginId("viewer")).thenReturn(Optional.of(user(1L, role)));
        when(departments.findAllByStatusOrderByDepartmentNameAscDepartmentIdAsc(DepartmentStatus.ACTIVE))
                .thenReturn(List.of(parent, child, other));
    }

    private User user(Long id, UserRole role) {
        User user = User.create("user" + id, "hash", "직원" + id, null, null, "EMP" + id,
                null, date, parent, null, role, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }

    private Department department(Long id, Department parent) {
        Department department = Department.create("부서" + id, parent);
        ReflectionTestUtils.setField(department, "departmentId", id);
        return department;
    }
}
