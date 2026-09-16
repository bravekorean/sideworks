package com.example.sideworks.department.service;

import com.example.sideworks.department.dto.DepartmentManagerUpdateRequest;
import com.example.sideworks.department.entity.Department;
import com.example.sideworks.department.entity.DepartmentStatus;
import com.example.sideworks.department.repository.DepartmentRepository;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentAdminServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private UserRepository userRepository;

    private DepartmentAdminService departmentAdminService;

    @BeforeEach
    void setUp() {
        departmentAdminService = new DepartmentAdminService(departmentRepository, userRepository);
    }

    @Test
    void 활성_일반_사용자도_소속_부서의_TEAM_LEADER로_배정할_수_있다() {
        Department department = Department.create("개발팀", null);
        ReflectionTestUtils.setField(department, "departmentId", 10L);
        User manager = User.create(
                "leader",
                "password",
                "팀장",
                null,
                null,
                "TC-26001",
                null,
                null,
                department,
                null,
                UserRole.USER,
                UserStatus.ACTIVE
        );
        ReflectionTestUtils.setField(manager, "userId", 20L);
        DepartmentManagerUpdateRequest request = new DepartmentManagerUpdateRequest();
        ReflectionTestUtils.setField(request, "managerUserId", 20L);
        when(departmentRepository.findByDepartmentIdAndStatus(10L, DepartmentStatus.ACTIVE))
                .thenReturn(Optional.of(department));
        when(userRepository.findById(20L)).thenReturn(Optional.of(manager));

        departmentAdminService.updateDepartmentManager(10L, request);

        assertThat(department.getManagerUserId()).isEqualTo(20L);
        assertThat(manager.getUserRole()).isEqualTo(UserRole.USER);
    }
}
