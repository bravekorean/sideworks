package com.example.sideworks.approval.controller;

import com.example.sideworks.approval.dto.ApprovalDetailHeaderResponse;
import com.example.sideworks.approval.dto.ApprovalDetailResponse;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.approval.service.ApprovalService;
import com.example.sideworks.auth.jwt.JwtTokenProvider;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApprovalController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApprovalService approvalService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 결재_상세_조회에_성공하면_문서와_하위_목록을_반환한다() throws Exception {
        Long approvalId = 10L;
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 12, 0);
        ApprovalDetailHeaderResponse header = new ApprovalDetailHeaderResponse(
                approvalId,
                1L,
                "작성자",
                "제목",
                "본문",
                ApprovalStatus.IN_PROGRESS,
                1,
                now,
                now,
                now,
                null
        );
        ApprovalDetailResponse response = ApprovalDetailResponse.of(
                header,
                List.of(),
                List.of(),
                List.of()
        );

        when(approvalService.getApprovalDetail(approvalId, "viewer"))
                .thenReturn(response);

        mockMvc.perform(get("/api/approvals/{approvalId}", approvalId)
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalId").value(approvalId))
                .andExpect(jsonPath("$.content").value("본문"))
                .andExpect(jsonPath("$.approvalLines").isArray())
                .andExpect(jsonPath("$.approvalLines").isEmpty())
                .andExpect(jsonPath("$.ccUsers").isEmpty())
                .andExpect(jsonPath("$.histories").isEmpty());
    }

    @Test
    void 존재하지_않거나_권한이_없는_문서는_404를_반환한다() throws Exception {
        Long approvalId = 999L;

        when(approvalService.getApprovalDetail(approvalId, "viewer"))
                .thenThrow(new BusinessException(ErrorCode.APPROVAL_NOT_FOUND));

        mockMvc.perform(get("/api/approvals/{approvalId}", approvalId)
                        .principal(authentication()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_FOUND"));
    }

    @Test
    void 현재_결재자는_문서를_승인할_수_있다() throws Exception {
        Long approvalId = 10L;

        mockMvc.perform(post("/api/approvals/{approvalId}/approve", approvalId)
                        .principal(authentication())
                        .contentType("application/json")
                        .content("{\"comment\":\"확인 완료\"}"))
                .andExpect(status().isNoContent());

        verify(approvalService).approveApproval(
                org.mockito.ArgumentMatchers.eq(approvalId),
                org.mockito.ArgumentMatchers.eq("viewer"),
                org.mockito.ArgumentMatchers.argThat(request -> "확인 완료".equals(request.getComment()))
        );
    }

    @Test
    void 현재_결재자는_반려_사유와_함께_문서를_반려할_수_있다() throws Exception {
        Long approvalId = 10L;

        mockMvc.perform(post("/api/approvals/{approvalId}/reject", approvalId)
                        .principal(authentication())
                        .contentType("application/json")
                        .content("{\"comment\":\"자료 보완 필요\"}"))
                .andExpect(status().isNoContent());

        verify(approvalService).rejectApproval(
                org.mockito.ArgumentMatchers.eq(approvalId),
                org.mockito.ArgumentMatchers.eq("viewer"),
                org.mockito.ArgumentMatchers.argThat(request -> "자료 보완 필요".equals(request.getComment()))
        );
    }

    @Test
    void 작성자는_진행_중인_문서의_상신을_취소할_수_있다() throws Exception {
        Long approvalId = 10L;

        mockMvc.perform(post("/api/approvals/{approvalId}/cancel", approvalId)
                        .principal(authentication()))
                .andExpect(status().isNoContent());

        verify(approvalService).cancelApproval(approvalId, "viewer");
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken("viewer", null, List.of());
    }
}
