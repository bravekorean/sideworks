package com.example.sideworks.attendance.controller;

import com.example.sideworks.attendance.service.AttendanceManagementService;
import com.example.sideworks.common.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttendanceManagementControllerTest {
    AttendanceManagementService service;
    MockMvc mvc;
    final UsernamePasswordAuthenticationToken principal =
            new UsernamePasswordAuthenticationToken("viewer", null, List.of());

    @BeforeEach
    void setup() {
        service = mock(AttendanceManagementService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AttendanceManagementController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void 권한없는_부서조회는_403을_반환한다() throws Exception {
        when(service.getDaily("viewer", LocalDate.of(2026, 9, 16), 20L, "", 0, 20))
                .thenThrow(new BusinessException(ErrorCode.ATTENDANCE_VIEW_FORBIDDEN));
        mvc.perform(get("/api/attendances/management").principal(principal)
                .param("date", "2026-09-16").param("departmentId", "20"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_VIEW_FORBIDDEN"));
    }

    @Test
    void 잘못된_날짜와_날짜누락은_400을_반환한다() throws Exception {
        mvc.perform(get("/api/attendances/management").principal(principal).param("date", "invalid"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/attendances/management").principal(principal))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void 페이지와_검색조건을_서버에_전달한다() throws Exception {
        when(service.getDaily("viewer", LocalDate.of(2026, 9, 16), null, "직원", 1, 20))
                .thenReturn(Page.empty());
        mvc.perform(get("/api/attendances/management").principal(principal)
                .param("date", "2026-09-16").param("name", "직원").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
}
