package com.example.sideworks.auth.service;

import com.example.sideworks.auth.dto.LoginRequest;
import com.example.sideworks.auth.dto.LoginResponse;
import com.example.sideworks.auth.dto.LoginResult;
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
        User user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("로그인할 수 없는 계정입니다.");
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
}
