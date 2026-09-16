package com.example.sideworks.attendance.service;

import com.example.sideworks.attendance.dto.*;
import com.example.sideworks.attendance.entity.*;
import com.example.sideworks.attendance.repository.*;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.department.entity.Department;
import com.example.sideworks.department.entity.DepartmentStatus;
import com.example.sideworks.department.repository.DepartmentRepository;
import com.example.sideworks.user.entity.*;
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceManagementService {
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final WorkPolicyRepository workPolicyRepository;
    private final WorkPolicyDayRepository workPolicyDayRepository;
    private final Clock clock;
    private final WorkScheduleExceptionRepository scheduleExceptions;

    public AttendanceManagementScope getScope(String loginId) {
        User viewer = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (viewer.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ATTENDANCE_VIEW_FORBIDDEN);
        }
        boolean all = viewer.getUserRole() == UserRole.HR_MANAGER
                || viewer.getUserRole() == UserRole.SUPER_ADMIN;
        List<Department> departments = departmentRepository
                .findAllByStatusOrderByDepartmentNameAscDepartmentIdAsc(DepartmentStatus.ACTIVE);
        Set<Long> allowed = new HashSet<>();
        for (Department department : departments) {
            if (all || Objects.equals(department.getManagerUserId(), viewer.getUserId())) {
                allowed.add(department.getDepartmentId());
            }
        }
        // 활성 부서 트리를 한 번 읽고 하위 범위를 확장한다. 방문 집합으로 순환도 방어한다.
        boolean expanded;
        do {
            expanded = false;
            for (Department department : departments) {
                Department parent = department.getParentDepartment();
                if (parent != null && allowed.contains(parent.getDepartmentId())) {
                    expanded |= allowed.add(department.getDepartmentId());
                }
            }
        } while (expanded);
        return new AttendanceManagementScope(all, LocalDate.now(clock), departments.stream()
                .filter(d -> allowed.contains(d.getDepartmentId()))
                .map(d -> new AttendanceManagementScope.DepartmentOption(d.getDepartmentId(), d.getDepartmentName()))
                .toList());
    }

    public Page<AttendanceMemberResponse> getDaily(
            String loginId, LocalDate date, Long departmentId, String name, int page, int size
    ) {
        AttendanceManagementScope scope = getScope(loginId);
        if (!scope.allDepartments() && scope.departments().isEmpty()) {
            throw new BusinessException(ErrorCode.ATTENDANCE_VIEW_FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (date == null || date.isAfter(now.toLocalDate()) || page < 0 || size < 1 || size > 100
                || name == null || name.length() > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        List<Long> ids = scope.departments().stream().map(AttendanceManagementScope.DepartmentOption::departmentId).toList();
        if (departmentId != null && !ids.contains(departmentId)) {
            throw new BusinessException(ErrorCode.ATTENDANCE_VIEW_FORBIDDEN);
        }
        Page<User> members = userRepository.findAttendanceMembers(UserStatus.ACTIVE, date,
                scope.allDepartments(), ids.isEmpty() ? List.of(-1L) : ids,
                departmentId, name.trim(), PageRequest.of(page, size));
        if (members.isEmpty()) return new PageImpl<>(List.of(), members.getPageable(), members.getTotalElements());

        Map<Long, Attendance> records = attendanceRepository
                .findAllByAttendanceDateAndUser_UserIdIn(date, members.stream().map(User::getUserId).toList())
                .stream().collect(Collectors.toMap(a -> a.getUser().getUserId(), Function.identity()));
        AttendanceDailyResponse empty = members.stream().anyMatch(u -> !records.containsKey(u.getUserId()))
                ? getUnrecordedStatus(date, now) : null;
        return members.map(user -> AttendanceMemberResponse.from(user,
                records.containsKey(user.getUserId()) ? AttendanceDailyResponse.from(records.get(user.getUserId())) : empty));
    }

    private AttendanceDailyResponse getUnrecordedStatus(LocalDate date, LocalDateTime now) {
        List<WorkPolicy> policies = workPolicyRepository.findApplicablePolicies(WorkPolicyStatus.ACTIVE, date);
        if (policies.isEmpty()) throw new BusinessException(ErrorCode.WORK_POLICY_NOT_CONFIGURED);
        if (policies.size() != 1) throw new BusinessException(ErrorCode.WORK_POLICY_CONFIGURATION_CONFLICT);
        WorkPolicy policy = policies.getFirst();
        boolean working = scheduleExceptions.findByExceptionDateAndActiveTrue(date)
                .map(WorkScheduleException::isWorking).orElseGet(() -> workPolicyDayRepository.findByWorkPolicy_WorkPolicyIdAndDayOfWeek(
                policy.getWorkPolicyId(), date.getDayOfWeek())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORK_POLICY_DAY_NOT_CONFIGURED)).isWorking());
        if (!working) return AttendanceDailyResponse.dayOff(date);
        return now.isBefore(policy.scheduledCheckOutAt(date))
                ? AttendanceDailyResponse.notStarted(date, policy, false)
                : AttendanceDailyResponse.absent(date, policy, false);
    }
}
