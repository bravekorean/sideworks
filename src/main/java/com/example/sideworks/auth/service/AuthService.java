package com.example.sideworks.auth.service;

import com.example.sideworks.auth.dto.LoginRequest;
import com.example.sideworks.auth.dto.LoginResponse;
import com.example.sideworks.auth.dto.LoginResult;
import com.example.sideworks.auth.dto.TokenRefreshResponse;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.auth.jwt.JwtTokenProvider;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResult login(LoginRequest request) {
        if (request == null || request.getLoginId() == null || request.getPassword() == null) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }

        User user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LOGIN));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }

        String accessToken = jwtTokenProvider.createAccessToken(
                user.getUserId(),
                user.getLoginId(),
                user.getUserRole()
        );

        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        LoginResponse loginResponse = new LoginResponse(
                accessToken,
                user.getUserId(),
                user.getLoginId(),
                user.getUserName(),
                user.getUserRole()
        );

        return new LoginResult(loginResponse, refreshToken);
    }

    public TokenRefreshResponse refreshAccessToken(String refreshToken) {
        if (refreshToken == null || !jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        String accessToken = jwtTokenProvider.createAccessToken(
                user.getUserId(),
                user.getLoginId(),
                user.getUserRole()
        );

        return new TokenRefreshResponse(accessToken);
    }
}
