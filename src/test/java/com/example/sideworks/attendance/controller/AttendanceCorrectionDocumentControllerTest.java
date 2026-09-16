package com.example.sideworks.attendance.controller;

import com.example.sideworks.attendance.service.AttendanceCorrectionService;
import com.example.sideworks.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttendanceCorrectionDocumentControllerTest {
    AttendanceCorrectionService service;
    MockMvc mvc;
    final UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken("employee", null, List.of());

    @BeforeEach void setup() {
        service = mock(AttendanceCorrectionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AttendanceCorrectionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void 파일없는_multipart_상신도_제목과_정정내용을_전달한다() throws Exception {
        when(service.submitDocument(eq("employee"), any(), isNull())).thenReturn(30L);
        mvc.perform(multipart("/api/attendances/corrections/documents").file(request())
                        .principal(principal))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approvalId").value(30));
        verify(service).submitDocument(eq("employee"), argThat(input ->
                input.title().equals("근태 정정") && input.correction().approverId().equals(2L)), isNull());
    }

    @Test void 파일과_JSON_파트를_함께_받는다() throws Exception {
        when(service.submitDocument(eq("employee"), any(), anyList())).thenReturn(31L);
        mvc.perform(multipart("/api/attendances/corrections/documents").file(request())
                        .file(new MockMultipartFile("files", "evidence.pdf", "application/pdf", new byte[]{1}))
                        .principal(principal))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approvalId").value(31));
        verify(service).submitDocument(eq("employee"), any(), argThat(files ->
                files.size() == 1 && files.getFirst().getOriginalFilename().equals("evidence.pdf")));
    }

    @Test void 잘못된_JSON은_400을_반환한다() throws Exception {
        mvc.perform(multipart("/api/attendances/corrections/documents")
                        .file(new MockMultipartFile("request", "", "application/json", "{".getBytes(StandardCharsets.UTF_8)))
                        .principal(principal)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void 요청파트_누락은_400을_반환한다() throws Exception {
        mvc.perform(multipart("/api/attendances/corrections/documents").principal(principal))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private MockMultipartFile request() {
        return new MockMultipartFile("request", "", "application/json", """
                {"title":"근태 정정","correction":{"date":"2026-09-15","checkInAt":"2026-09-15T09:00:00",
                "reason":"출근 누락","approverId":2}}
                """.getBytes(StandardCharsets.UTF_8));
    }
}
