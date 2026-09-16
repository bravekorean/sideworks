package com.example.sideworks.user.controller;

import com.example.sideworks.auth.jwt.JwtAuthenticationFilter;
import com.example.sideworks.config.SecurityConfig;
import com.example.sideworks.user.service.UserAdminService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserAdminController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class UserAdminAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAdminService userAdminService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void passThroughJwtFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void HR_MANAGER는_사용자_목록을_조회할_수_있다() throws Exception {
        when(userAdminService.findAllUsers(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void HR_MANAGER는_사용자_역할을_변경할_수_없다() throws Exception {
        mockMvc.perform(patch("/api/admin/users/1/role")
                        .contentType("application/json")
                        .content("{\"userRole\":\"HR_MANAGER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void SUPER_ADMIN은_사용자_역할을_변경할_수_있다() throws Exception {
        mockMvc.perform(patch("/api/admin/users/1/role")
                        .contentType("application/json")
                        .content("{\"userRole\":\"HR_MANAGER\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void USER는_인사_관리_API에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 폐기된_ADMIN_권한으로_인사_API에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }
}
