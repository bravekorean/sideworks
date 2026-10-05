package com.example.sideworks.notification.controller;

import com.example.sideworks.auth.jwt.JwtAuthenticationFilter;
import com.example.sideworks.config.SecurityConfig;
import com.example.sideworks.notification.service.NotificationService;
import com.example.sideworks.notification.service.NotificationStream;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class NotificationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean NotificationService notifications;
    @MockitoBean NotificationStream stream;
    @MockitoBean JwtAuthenticationFilter jwtFilter;

    @BeforeEach
    void passThrough() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "owner")
    void 본인_알림_개수와_읽음을_요청한다() throws Exception {
        when(notifications.unreadCount("owner")).thenReturn(2L);
        mvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
        mvc.perform(patch("/api/notifications/9/read"))
                .andExpect(status().isNoContent());
        verify(notifications).markRead("owner", 9L);
    }

    @Test
    @WithMockUser(username = "owner")
    void SSE_연결은_로그인_사용자_ID로_등록한다() throws Exception {
        when(notifications.currentUserId("owner")).thenReturn(5L);
        when(stream.connect(5L)).thenReturn(new SseEmitter());

        mvc.perform(get("/api/notifications/stream"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Buffering", "no"));
        verify(stream).connect(5L);
    }

    @Test
    void 미인증_알림_접근을_막는다() throws Exception {
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/notifications/9/read")).andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications/stream")).andExpect(status().isForbidden());
        verifyNoInteractions(notifications);
    }
}
