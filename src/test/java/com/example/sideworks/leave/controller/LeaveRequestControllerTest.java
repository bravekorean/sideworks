package com.example.sideworks.leave.controller;

import com.example.sideworks.auth.jwt.JwtAuthenticationFilter;
import com.example.sideworks.config.SecurityConfig;
import com.example.sideworks.leave.service.LeaveCancellationService;
import com.example.sideworks.leave.service.LeaveRequestService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeaveRequestController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class LeaveRequestControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean LeaveRequestService requests;
    @MockitoBean LeaveCancellationService cancellations;
    @MockitoBean JwtAuthenticationFilter jwtFilter;

    @BeforeEach void passThroughJwtFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test @WithMockUser(username = "employee")
    void 로그인_사용자가_본인_휴가를_상신한다() throws Exception {
        when(requests.submit(eq("employee"), any(), any())).thenReturn(42L);
        mvc.perform(multipart("/api/annual-leave/requests")
                        .file(new MockMultipartFile("request", "", "application/json",
                                "{\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\",\"period\":\"AM\",\"reason\":\"휴식\",\"approverId\":3}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalId").value(42));
        verify(requests).submit(eq("employee"), any(), any());
    }

    @Test
    void 인증되지_않으면_승인된_휴가도_조회할_수_없다() throws Exception {
        mvc.perform(get("/api/annual-leave/requests/approved")).andExpect(status().isForbidden());
        verifyNoInteractions(cancellations);
    }

    @Test @WithMockUser(username = "employee")
    void 본인_휴가_취소도_별도_결재로_상신한다() throws Exception {
        when(cancellations.submit(eq("employee"), any(), any())).thenReturn(43L);
        mvc.perform(multipart("/api/annual-leave/requests/cancellations")
                        .file(new MockMultipartFile("request", "", "application/json",
                                "{\"leaveRequestId\":7,\"reason\":\"일정 변경\",\"approverId\":3}"
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalId").value(43));
        verify(cancellations).submit(eq("employee"), any(), any());
    }
}
