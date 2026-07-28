package com.example.sideworks.user.controller;

import com.example.sideworks.auth.jwt.JwtTokenProvider;
import com.example.sideworks.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 본인_연락처_수정에_성공하면_204를_반환한다() throws Exception {
        mockMvc.perform(patch("/api/users/mypage")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userEmail": "user@example.com",
                                  "userPhone": "010-1234-5678"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(userService).updateMyProfile(
                eq("user"),
                argThat(request -> "user@example.com".equals(request.getUserEmail())
                        && "010-1234-5678".equals(request.getUserPhone()))
        );
    }

    @Test
    void 비밀번호_재확인_후_탈퇴에_성공하면_204를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/users/mypage")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "current-password"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(userService).withdrawMyAccount(
                eq("user"),
                argThat(request -> "current-password".equals(request.getPassword()))
        );
    }

    @Test
    void 현재_비밀번호를_확인해_비밀번호를_변경한다() throws Exception {
        mockMvc.perform(patch("/api/users/mypage/password")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "current-password",
                                  "newPassword": "new-password"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(userService).changeMyPassword(
                eq("user"),
                argThat(request -> "current-password".equals(request.getCurrentPassword())
                        && "new-password".equals(request.getNewPassword()))
        );
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken("user", null, List.of());
    }
}
