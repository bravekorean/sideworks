package com.example.sideworks.approval.attachment.controller;

import com.example.sideworks.approval.attachment.dto.ApprovalAttachmentResponse;
import com.example.sideworks.approval.attachment.service.ApprovalAttachmentService;
import com.example.sideworks.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApprovalAttachmentController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApprovalAttachmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ApprovalAttachmentService attachmentService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @Test
    void 여러_첨부파일을_업로드한다() throws Exception {
        MockMultipartFile first = new MockMultipartFile("files", "견적서.pdf", "application/pdf", "%PDF-".getBytes());
        MockMultipartFile second = new MockMultipartFile("files", "사진.png", "image/png", new byte[]{1});
        when(attachmentService.upload(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("writer"), anyList()))
                .thenReturn(List.of(new ApprovalAttachmentResponse(10L, "견적서.pdf", "application/pdf", 5L, null)));

        mockMvc.perform(multipart("/api/approvals/{approvalId}/attachments", 1L)
                        .file(first).file(second).principal(authentication()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].attachmentId").value(10L))
                .andExpect(jsonPath("$[0].originalFileName").value("견적서.pdf"));

        verify(attachmentService).upload(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("writer"), anyList());
    }

    @Test
    void 권한이_검증된_첨부파일을_다운로드한다() throws Exception {
        byte[] content = "file-content".getBytes();
        when(attachmentService.download(1L, 10L, "writer"))
                .thenReturn(new ApprovalAttachmentService.DownloadFile(
                        new ByteArrayResource(content), "견적서.pdf", "application/pdf", content.length
                ));

        mockMvc.perform(get("/api/approvals/{approvalId}/attachments/{attachmentId}/download", 1L, 10L)
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(content().bytes(content));
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken("writer", null, List.of());
    }
}
