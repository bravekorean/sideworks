package com.example.sideworks.user.service;

import com.example.sideworks.user.entity.User;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.dto.MyProfileResponse;
import com.example.sideworks.user.dto.MyProfileUpdateRequest;
import com.example.sideworks.user.dto.AccountWithdrawalRequest;
import com.example.sideworks.user.dto.PasswordChangeRequest;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final int MAX_EMAIL_LENGTH = 200;
    private static final int MAX_PHONE_LENGTH = 100;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public MyProfileResponse getMyProfile(String loginId) {
        User user = findActiveUser(loginId);

        return MyProfileResponse.from(user);
    }

    @Transactional
    public void updateMyProfile(String loginId, MyProfileUpdateRequest request) {
        validateProfileUpdateRequest(request);
        User user = findActiveUser(loginId);

        user.updateContactInfo(
                normalizeNullable(request.getUserEmail()),
                normalizeNullable(request.getUserPhone())
        );
    }

    @Transactional
    public void withdrawMyAccount(String loginId, AccountWithdrawalRequest request) {
        validateWithdrawalRequest(request);
        User user = findActiveUser(loginId);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        user.withdraw();
    }

    @Transactional
    public void changeMyPassword(String loginId, PasswordChangeRequest request) {
        validatePasswordChangeRequest(request);
        User user = findActiveUser(loginId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        user.changePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    private User findActiveUser(String loginId) {
        User user = userRepository.findProfileByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return user;
    }

    private void validateProfileUpdateRequest(MyProfileUpdateRequest request) {
        if (request == null
                || exceedsLength(request.getUserEmail(), MAX_EMAIL_LENGTH)
                || exceedsLength(request.getUserPhone(), MAX_PHONE_LENGTH)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private void validateWithdrawalRequest(AccountWithdrawalRequest request) {
        if (request == null || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private void validatePasswordChangeRequest(PasswordChangeRequest request) {
        if (request == null
                || request.getCurrentPassword() == null
                || request.getCurrentPassword().isBlank()
                || request.getNewPassword() == null
                || request.getNewPassword().length() < MIN_PASSWORD_LENGTH
                || request.getNewPassword().length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION);
        }
    }

    private boolean exceedsLength(String value, int maxLength) {
        return value != null && value.trim().length() > maxLength;
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
