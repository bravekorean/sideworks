package com.example.sideworks.leave.controller;

import com.example.sideworks.auth.jwt.JwtAuthenticationFilter;
import com.example.sideworks.config.SecurityConfig;
import com.example.sideworks.leave.dto.AnnualLeaveBalanceResponse;
import com.example.sideworks.leave.dto.AnnualLeaveAvailabilityResponse;
import com.example.sideworks.leave.dto.LeaveDayResponse;
import com.example.sideworks.leave.entity.LeavePeriod;
import com.example.sideworks.leave.service.AnnualLeaveAvailabilityService;
import com.example.sideworks.leave.service.LeaveCalendarService;
import com.example.sideworks.leave.service.AnnualLeaveBalanceService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnnualLeaveController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class AnnualLeaveControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AnnualLeaveBalanceService service;
    @MockitoBean AnnualLeaveAvailabilityService availability;
    @MockitoBean LeaveCalendarService calendar;
    @MockitoBean JwtAuthenticationFilter jwtFilter;

    @BeforeEach void passThroughJwtFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(), any(), any());
    }

    @Test @WithMockUser(username = "employee")
    void 본인_잔액만_조회한다() throws Exception {
        when(service.getMyBalance("employee", 2026)).thenReturn(new AnnualLeaveBalanceResponse(
                2026, new BigDecimal("11.0"), new BigDecimal("11.0"), BigDecimal.ZERO));
        mvc.perform(get("/api/annual-leave/me/balance").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaveYear").value(2026))
                .andExpect(jsonPath("$.remainingDays").value(11.0));
        verify(service).getMyBalance("employee", 2026);
    }

    @Test @WithMockUser(username = "employee")
    void 이력_연도를_생략하면_서비스에_현재연도_요청을_전달한다() throws Exception {
        when(service.getMyHistory("employee", null)).thenReturn(List.of());
        mvc.perform(get("/api/annual-leave/me/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        verify(service).getMyHistory("employee", null);
    }

    @Test @WithMockUser(username = "employee")
    void 휴가_신청_가능량은_본인에게만_조회된다() throws Exception {
        when(availability.getMyAvailability("employee", 2026)).thenReturn(new AnnualLeaveAvailabilityResponse(
                2026, new BigDecimal("15.0"), new BigDecimal("12.0"),
                new BigDecimal("2.5"), new BigDecimal("9.5")));
        mvc.perform(get("/api/annual-leave/me/availability").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingDays").value(12.0))
                .andExpect(jsonPath("$.pendingDays").value(2.5))
                .andExpect(jsonPath("$.availableDays").value(9.5));
        verify(availability).getMyAvailability("employee", 2026);
    }

    @Test @WithMockUser(username = "employee")
    void 승인된_본인_휴가만_캘린더에_조회한다() throws Exception {
        when(calendar.getMyApprovedDays("employee", 2026, 9)).thenReturn(List.of(
                new LeaveDayResponse(LocalDate.of(2026, 9, 25), LeavePeriod.AM, 7L)));
        mvc.perform(get("/api/annual-leave/me/days").param("year", "2026").param("month", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-09-25"))
                .andExpect(jsonPath("$[0].period").value("AM"));
        verify(calendar).getMyApprovedDays("employee", 2026, 9);
    }

    @Test void 인증되지_않으면_조회하지_못한다() throws Exception {
        mvc.perform(get("/api/annual-leave/me/balance")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
