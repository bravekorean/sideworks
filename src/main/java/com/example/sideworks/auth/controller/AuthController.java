package com.example.sideworks.auth.controller;

import com.example.sideworks.auth.dto.LoginRequest;
import com.example.sideworks.auth.dto.LoginResponse;
import com.example.sideworks.auth.service.AuthService;
import com.example.sideworks.auth.dto.LoginResult;
import com.example.sideworks.auth.dto.TokenRefreshResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "로그인, Access Token 재발급 및 로그아웃 API")
public class AuthController {

    private final AuthService authService;

    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "로그인 ID와 비밀번호를 검증하고 Access Token을 응답하며 Refresh Token을 HttpOnly Cookie로 발급합니다.")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResult loginResult = authService.login(request);

        ResponseCookie refreshTokenCookie = createRefreshTokenCookie(loginResult.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(loginResult.getLoginResponse());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Access Token 재발급", description = "HttpOnly Cookie의 Refresh Token을 검증하고 새로운 Access Token을 발급합니다.")
    public ResponseEntity<TokenRefreshResponse> refreshAccessToken(
            @CookieValue(name = "refreshToken", required = false) String refreshToken
    ) {
        return ResponseEntity.ok(authService.refreshAccessToken(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "Refresh Token Cookie를 만료시켜 클라이언트의 로그인 상태를 종료합니다.")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, deleteRefreshTokenCookie().toString())
                .build();
    }

    private ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofDays(7))
                .build();
    }

    private ResponseCookie deleteRefreshTokenCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}
