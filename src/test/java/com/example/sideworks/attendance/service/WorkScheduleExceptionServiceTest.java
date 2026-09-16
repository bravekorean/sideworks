package com.example.sideworks.attendance.service;

import com.example.sideworks.attendance.repository.WorkScheduleExceptionRepository;
import com.example.sideworks.common.exception.*;
import com.example.sideworks.user.entity.*;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkScheduleExceptionServiceTest {
    @Mock WorkScheduleExceptionRepository repository;
    @Mock UserRepository users;
    WorkScheduleExceptionService service;
    @BeforeEach void setUp() { service = new WorkScheduleExceptionService(repository, users); }

    @Test void 인사관리자는_회사휴무일을_등록할수있다() {
        User actor = user(UserRole.HR_MANAGER);
        when(users.findByLoginId("hr")).thenReturn(Optional.of(actor));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.create("hr", new WorkScheduleExceptionService.Input(
                LocalDate.of(2026, 10, 2), "창립기념일", false, true, null));
        assertThat(result.working()).isFalse();
        assertThat(result.active()).isTrue();
        verify(repository).saveAndFlush(any());
    }

    @Test void 일반사용자는_휴무일을_관리할수없다() {
        User ordinary = user(UserRole.USER);
        when(users.findByLoginId("user")).thenReturn(Optional.of(ordinary));
        assertThatThrownBy(() -> service.list("user", 2026))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(ErrorCode.ATTENDANCE_CHANGE_FORBIDDEN);
        verifyNoInteractions(repository);
    }

    private User user(UserRole role) {
        User u = mock(User.class);
        when(u.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(u.getUserRole()).thenReturn(role);
        return u;
    }
}
