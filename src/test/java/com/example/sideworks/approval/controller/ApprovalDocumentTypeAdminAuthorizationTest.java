package com.example.sideworks.approval.controller;

import com.example.sideworks.auth.jwt.JwtAuthenticationFilter;
import com.example.sideworks.config.SecurityConfig;
import com.example.sideworks.approval.service.ApprovalDocumentTypeAdminService;
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
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApprovalDocumentTypeAdminController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class ApprovalDocumentTypeAdminAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean ApprovalDocumentTypeAdminService service;
    @MockitoBean JwtAuthenticationFilter jwtFilter;

    @BeforeEach void passThrough() throws Exception {
        doAnswer(i -> {
            FilterChain chain = i.getArgument(2);
            chain.doFilter(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test @WithMockUser(roles = "SUPER_ADMIN")
    void 최고관리자는_목록조회와_수정이_가능하다() throws Exception {
        when(service.findAll()).thenReturn(List.of());
        mvc.perform(get("/api/admin/approval-document-types")).andExpect(status().isOk());
        mvc.perform(put("/api/admin/approval-document-types/1").contentType("application/json").content("{}"))
                .andExpect(status().isOk());
        verify(service).update(eq(1L), any());
    }

    @Test @WithMockUser(roles = "HR_MANAGER")
    void 인사관리자는_조회_생성_수정이_불가능하다() throws Exception { assertForbidden(); }

    @Test @WithMockUser(roles = "SUPER_ADMIN")
    void 최고관리자는_생성할_수_있다() throws Exception {
        var response = com.example.sideworks.approval.dto.ApprovalDocumentTypeAdminResponse.from(
                com.example.sideworks.approval.entity.ApprovalDocumentTypeFixtures.general());
        when(service.create(any())).thenReturn(response);
        mvc.perform(post("/api/admin/approval-document-types").contentType("application/json")
                .content("{\"typeCode\":\"GENERAL_PROPOSAL\",\"typeName\":\"품의서\",\"sortOrder\":0}"))
                .andExpect(status().isCreated());
        verify(service).create(any());
    }

    @Test @WithMockUser(roles = "USER")
    void 일반사용자는_조회_생성_수정이_불가능하다() throws Exception { assertForbidden(); }

    @Test void 미인증_접근을_차단한다() throws Exception { assertForbidden(); }

    private void assertForbidden() throws Exception {
        mvc.perform(get("/api/admin/approval-document-types")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/approval-document-types").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/approval-document-types/1").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
