package com.example.sideworks.attendance.controller;

import com.example.sideworks.attendance.dto.AttendanceDailyResponse;
import com.example.sideworks.attendance.service.AttendanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AttendanceControllerTest {

    @Mock
    private AttendanceService attendanceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AttendanceController(attendanceService))
                .build();
    }

    @Test
    void 인증_사용자가_출근한다() throws Exception {
        when(attendanceService.checkIn("employee")).thenReturn(null);

        mockMvc.perform(post("/api/attendances/check-in").principal(authentication()))
                .andExpect(status().isCreated());

        verify(attendanceService).checkIn("employee");
    }

    @Test
    void 인증_사용자가_퇴근한다() throws Exception {
        when(attendanceService.checkOut("employee")).thenReturn(null);

        mockMvc.perform(post("/api/attendances/check-out").principal(authentication()))
                .andExpect(status().isOk());

        verify(attendanceService).checkOut("employee");
    }

    @Test
    void 인증_사용자의_오늘_근태를_조회한다() throws Exception {
        when(attendanceService.getToday("employee")).thenReturn(null);

        mockMvc.perform(get("/api/attendances/me/today").principal(authentication()))
                .andExpect(status().isOk());

        verify(attendanceService).getToday("employee");
    }

    @Test
    void 인증_사용자의_월별_근태를_조회한다() throws Exception {
        when(attendanceService.getMonthly("employee", 2026, 9)).thenReturn(List.<AttendanceDailyResponse>of());

        mockMvc.perform(get("/api/attendances/me")
                        .param("year", "2026")
                        .param("month", "9")
                        .principal(authentication()))
                .andExpect(status().isOk());

        verify(attendanceService).getMonthly("employee", 2026, 9);
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken("employee", null, List.of());
    }
}
