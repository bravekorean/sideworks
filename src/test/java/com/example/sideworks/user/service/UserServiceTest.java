package com.example.sideworks.user.service;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.dto.AccountWithdrawalRequest;
import com.example.sideworks.user.dto.MyProfileUpdateRequest;
import com.example.sideworks.user.dto.PasswordChangeRequest;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String LOGIN_ID = "user";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void 본인은_이메일과_전화번호를_수정할_수_있다() {
        User user = activeUser();
        MyProfileUpdateRequest request = profileRequest(" user@example.com ", " 010-1234-5678 ");
        when(userRepository.findProfileByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));

        userService.updateMyProfile(LOGIN_ID, request);

        assertThat(user.getUserEmail()).isEqualTo("user@example.com");
        assertThat(user.getUserPhone()).isEqualTo("010-1234-5678");
    }

    @Test
    void 공백_연락처는_null로_저장한다() {
        User user = activeUser();
        MyProfileUpdateRequest request = profileRequest(" ", "  ");
        when(userRepository.findProfileByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));

        userService.updateMyProfile(LOGIN_ID, request);

        assertThat(user.getUserEmail()).isNull();
        assertThat(user.getUserPhone()).isNull();
    }

    @Test
    void 현재_비밀번호가_일치하면_계정을_탈퇴_상태로_변경한다() {
        User user = activeUser();
        AccountWithdrawalRequest request = withdrawalRequest("current-password");
        when(userRepository.findProfileByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "encoded-password")).thenReturn(true);

        userService.withdrawMyAccount(LOGIN_ID, request);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
    }

    @Test
    void 현재_비밀번호가_틀리면_탈퇴할_수_없다() {
        User user = activeUser();
        AccountWithdrawalRequest request = withdrawalRequest("wrong-password");
        when(userRepository.findProfileByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> userService.withdrawMyAccount(LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void 비활성_계정은_마이페이지를_사용할_수_없다() {
        User user = activeUser();
        user.changeStatus(UserStatus.INACTIVE);
        when(userRepository.findProfileByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.getMyProfile(LOGIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 현재_비밀번호를_확인하고_새_비밀번호로_변경한다() {
        User user = activeUser();
        PasswordChangeRequest request = passwordChangeRequest("current-password", "new-password");
        when(userRepository.findProfileByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "encoded-password")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "encoded-password")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-encoded-password");

        userService.changeMyPassword(LOGIN_ID, request);

        assertThat(user.getPassword()).isEqualTo("new-encoded-password");
    }

    @Test
    void 현재_비밀번호와_같은_비밀번호로_변경할_수_없다() {
        User user = activeUser();
        PasswordChangeRequest request = passwordChangeRequest("current-password", "current-password");
        when(userRepository.findProfileByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> userService.changeMyPassword(LOGIN_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SAME_AS_CURRENT_PASSWORD);

        assertThat(user.getPassword()).isEqualTo("encoded-password");
    }

    private User activeUser() {
        return User.create(
                LOGIN_ID,
                "encoded-password",
                "사용자",
                "old@example.com",
                "010-0000-0000",
                "EMP001",
                null,
                null,
                UserRole.USER,
                UserStatus.ACTIVE
        );
    }

    private MyProfileUpdateRequest profileRequest(String email, String phone) {
        MyProfileUpdateRequest request = new MyProfileUpdateRequest();
        ReflectionTestUtils.setField(request, "userEmail", email);
        ReflectionTestUtils.setField(request, "userPhone", phone);
        return request;
    }

    private AccountWithdrawalRequest withdrawalRequest(String password) {
        AccountWithdrawalRequest request = new AccountWithdrawalRequest();
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }

    private PasswordChangeRequest passwordChangeRequest(String currentPassword, String newPassword) {
        PasswordChangeRequest request = new PasswordChangeRequest();
        ReflectionTestUtils.setField(request, "currentPassword", currentPassword);
        ReflectionTestUtils.setField(request, "newPassword", newPassword);
        return request;
    }
}
