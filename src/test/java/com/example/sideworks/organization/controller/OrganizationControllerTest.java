package com.example.sideworks.organization.controller;

import com.example.sideworks.organization.dto.OrganizationDepartmentResponse;
import com.example.sideworks.organization.dto.OrganizationMemberResponse;
import com.example.sideworks.organization.service.OrganizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationControllerTest {

    @Mock
    private OrganizationService organizationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrganizationController(organizationService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void 활성_부서_조직도를_조회한다() throws Exception {
        when(organizationService.getDepartmentTree()).thenReturn(List.of(
                new OrganizationDepartmentResponse(1L, null, "개발본부", 10L, "홍길동", 5L),
                new OrganizationDepartmentResponse(2L, 1L, "백엔드팀", 11L, "김개발", 3L)
        ));

        mockMvc.perform(get("/api/organization/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].departmentId").value(1L))
                .andExpect(jsonPath("$[0].parentDepartmentId").doesNotExist())
                .andExpect(jsonPath("$[0].departmentName").value("개발본부"))
                .andExpect(jsonPath("$[0].managerUserId").value(10L))
                .andExpect(jsonPath("$[0].managerName").value("홍길동"))
                .andExpect(jsonPath("$[0].memberCount").value(5L))
                .andExpect(jsonPath("$[0].userEmail").doesNotExist())
                .andExpect(jsonPath("$[0].userPhone").doesNotExist())
                .andExpect(jsonPath("$[1].parentDepartmentId").value(1L));
    }

    @Test
    void 활성_부서의_직속_구성원을_페이지로_조회한다() throws Exception {
        PageRequest pageable = PageRequest.of(0, 20);
        when(organizationService.getDepartmentMembers(1L, pageable)).thenReturn(
                new PageImpl<>(List.of(
                        new OrganizationMemberResponse(10L, "홍길동", "TC-26001", 3L, "과장", true)
                ), pageable, 1)
        );

        mockMvc.perform(get("/api/organization/departments/1/members")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(10L))
                .andExpect(jsonPath("$.content[0].userName").value("홍길동"))
                .andExpect(jsonPath("$.content[0].employeeNo").value("TC-26001"))
                .andExpect(jsonPath("$.content[0].positionId").value(3L))
                .andExpect(jsonPath("$.content[0].positionName").value("과장"))
                .andExpect(jsonPath("$.content[0].manager").value(true))
                .andExpect(jsonPath("$.content[0].userEmail").doesNotExist())
                .andExpect(jsonPath("$.content[0].userPhone").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
