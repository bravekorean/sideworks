package com.example.sideworks.auth.service;

import com.example.sideworks.auth.dto.LoginRequest;
import com.example.sideworks.auth.dto.LoginResult;
import com.example.sideworks.auth.jwt.JwtTokenProvider;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String RAW_PASSWORD = "1234";

    @Mock
    private UserRepository userRepository;

    private AuthService authService;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        jwtTokenProvider = new JwtTokenProvider(
                "test-secret-key-test-secret-key-test-secret-key-1234567890",
                1_800_000,
                604_800_000
        );

        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider);
    }

    @Test
    void 로그인에_성공하면_LoginResult를_반환한다() {
        // given
        User user = createUser(
                1L,
                "admin",
                passwordEncoder.encode(RAW_PASSWORD),
                "관리자",
                UserRole.SUPER_ADMIN,
                UserStatus.ACTIVE
        );
        LoginRequest request = createLoginRequest("admin", RAW_PASSWORD);

        when(userRepository.findByLoginId("admin")).thenReturn(Optional.of(user));

        // when
        LoginResult result = authService.login(request);

        // then
        assertThat(result.getLoginResponse().getAccessToken()).isNotBlank();
        assertThat(result.getRefreshToken()).isNotBlank();
        assertThat(result.getLoginResponse().getUserId()).isEqualTo(1L);
        assertThat(result.getLoginResponse().getLoginId()).isEqualTo("admin");
        assertThat(result.getLoginResponse().getUserName()).isEqualTo("관리자");
        assertThat(result.getLoginResponse().getUserRole()).isEqualTo(UserRole.SUPER_ADMIN);
    }

    @Test
    void 존재하지_않는_아이디면_예외가_발생한다() {
        // given
        LoginRequest request = createLoginRequest("unknown", RAW_PASSWORD);
        when(userRepository.findByLoginId("unknown")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 비밀번호가_틀리면_예외가_발생한다() {
        // given
        User user = createUser(
                1L,
                "admin",
                passwordEncoder.encode(RAW_PASSWORD),
                "관리자",
                UserRole.SUPER_ADMIN,
                UserStatus.ACTIVE
        );
        LoginRequest request = createLoginRequest("admin", "wrong-password");

        when(userRepository.findByLoginId("admin")).thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 비활성_계정이면_예외가_발생한다() {
        // given
        User user = createUser(
                1L,
                "admin",
                passwordEncoder.encode(RAW_PASSWORD),
                "관리자",
                UserRole.SUPER_ADMIN,
                UserStatus.INACTIVE
        );
        LoginRequest request = createLoginRequest("admin", RAW_PASSWORD);

        when(userRepository.findByLoginId("admin")).thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private LoginRequest createLoginRequest(String loginId, String password) {
        LoginRequest request = new LoginRequest();
        ReflectionTestUtils.setField(request, "loginId", loginId);
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }

    private User createUser(
            Long userId,
            String loginId,
            String password,
            String userName,
            UserRole userRole,
            UserStatus status
    ) {
        try {
            Constructor<User> constructor = User.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            User user = constructor.newInstance();
            ReflectionTestUtils.setField(user, "userId", userId);
            ReflectionTestUtils.setField(user, "loginId", loginId);
            ReflectionTestUtils.setField(user, "password", password);
            ReflectionTestUtils.setField(user, "userName", userName);
            ReflectionTestUtils.setField(user, "userRole", userRole);
            ReflectionTestUtils.setField(user, "status", status);

            return user;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("테스트용 User 객체 생성에 실패했습니다.", e);
        }
    }
}
