package com.example.sideworks.auth.controller;

import com.example.sideworks.auth.dto.LoginRequest;
import com.example.sideworks.auth.dto.LoginResponse;
import com.example.sideworks.auth.service.AuthService;
import com.example.sideworks.auth.dto.LoginResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResult loginResult = authService.login(request);

        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", loginResult.getRefreshToken())
                .httpOnly(true) // Javascript에서 쿠키 접근 불가
                .secure(false) // 로컬 환경이라서 false로 해뒀지만 배포시 true로 변경
                .sameSite("Lax") // 외부 사이트에서 무분별하게 쿠키가 전송되는 것을 줄임
                .path("/api/auth")
                .maxAge(Duration.ofDays(7)) // 쿠키 유지 기간 7일
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(loginResult.getLoginResponse());
    }
}
