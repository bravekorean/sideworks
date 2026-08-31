package com.example.sideworks.user.service;

import com.example.sideworks.department.repository.DepartmentRepository;
import com.example.sideworks.position.repository.PositionRepository;
import com.example.sideworks.user.dto.UserCreateRequest;
import com.example.sideworks.user.dto.UserCreateResponse;
import com.example.sideworks.user.dto.UserUpdateRequest;
import com.example.sideworks.user.entity.JobFamily;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmployeeNumberGenerator employeeNumberGenerator;

    private UserAdminService userAdminService;

    @BeforeEach
    void setUp() {
        userAdminService = new UserAdminService(
                userRepository,
                departmentRepository,
                positionRepository,
                passwordEncoder,
                employeeNumberGenerator
        );
    }

    @Test
    void 사용자_생성시_직렬과_입사일로_사번을_자동_발급한다() {
        UserCreateRequest request = createRequest();
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(employeeNumberGenerator.generate(
                JobFamily.TECHNICAL,
                LocalDate.of(2026, 8, 31)
        )).thenReturn("TC-26001");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "userId", 10L);
            return user;
        });

        UserCreateResponse response = userAdminService.createUser(request);

        assertThat(response.getUserId()).isEqualTo(10L);
        assertThat(response.getEmployeeNo()).isEqualTo("TC-26001");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmployeeNo()).isEqualTo("TC-26001");
        assertThat(userCaptor.getValue().getJobFamily()).isEqualTo(JobFamily.TECHNICAL);
        assertThat(userCaptor.getValue().getHireDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void 기본정보를_수정해도_발급된_사번은_변하지_않는다() {
        User user = User.create(
                "user",
                "encoded-password",
                "기존 이름",
                null,
                null,
                "TC-26001",
                JobFamily.TECHNICAL,
                LocalDate.of(2026, 8, 31),
                null,
                null,
                UserRole.USER,
                UserStatus.ACTIVE
        );
        UserUpdateRequest request = new UserUpdateRequest();
        ReflectionTestUtils.setField(request, "userName", "변경 이름");
        ReflectionTestUtils.setField(request, "userEmail", "new@example.com");
        ReflectionTestUtils.setField(request, "userPhone", "010-1234-5678");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userAdminService.updateUser(1L, request);

        assertThat(user.getUserName()).isEqualTo("변경 이름");
        assertThat(user.getEmployeeNo()).isEqualTo("TC-26001");
    }

    private UserCreateRequest createRequest() {
        UserCreateRequest request = new UserCreateRequest();
        ReflectionTestUtils.setField(request, "loginId", "new-user");
        ReflectionTestUtils.setField(request, "password", "password123");
        ReflectionTestUtils.setField(request, "userName", "신규 사용자");
        ReflectionTestUtils.setField(request, "jobFamily", JobFamily.TECHNICAL);
        ReflectionTestUtils.setField(request, "hireDate", LocalDate.of(2026, 8, 31));
        ReflectionTestUtils.setField(request, "userRole", UserRole.USER);
        ReflectionTestUtils.setField(request, "status", UserStatus.ACTIVE);
        return request;
    }
}
