package com.example.sideworks.auth.controller;

import com.example.sideworks.auth.dto.LoginResponse;
import com.example.sideworks.auth.dto.LoginResult;
import com.example.sideworks.auth.jwt.JwtTokenProvider;
import com.example.sideworks.auth.service.AuthService;
import com.example.sideworks.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 로그인에_성공하면_accessToken과_refreshToken_쿠키를_반환한다() throws Exception {
        // given
        LoginResponse loginResponse = new LoginResponse(
                "access-token",
                1L,
                "admin",
                "관리자",
                UserRole.SUPER_ADMIN
        );
        LoginResult loginResult = new LoginResult(loginResponse, "refresh-token");

        when(authService.login(any())).thenReturn(loginResult);

        // when & then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "admin",
                                  "password": "1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.loginId").value("admin"))
                .andExpect(jsonPath("$.userName").value("관리자"))
                .andExpect(jsonPath("$.userRole").value("SUPER_ADMIN"))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().path("refreshToken", "/api/auth"))
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("SameSite=Lax"))));
    }

    @Test
    void login_fail_returns_error_response() throws Exception {
        // given
        when(authService.login(any())).thenThrow(new IllegalArgumentException("invalid login"));

        // when & then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "admin",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN"))
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }
}
