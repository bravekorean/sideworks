package com.example.sideworks.organization.service;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.department.entity.Department;
import com.example.sideworks.department.entity.DepartmentStatus;
import com.example.sideworks.department.repository.DepartmentRepository;
import com.example.sideworks.organization.dto.OrganizationDepartmentResponse;
import com.example.sideworks.organization.dto.OrganizationMemberResponse;
import com.example.sideworks.organization.repository.OrganizationQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationQueryRepository organizationQueryRepository;
    @Mock
    private DepartmentRepository departmentRepository;

    private OrganizationService organizationService;

    @BeforeEach
    void setUp() {
        organizationService = new OrganizationService(organizationQueryRepository, departmentRepository);
    }

    @Test
    void 활성_부서_조직도_조회_결과를_반환한다() {
        List<OrganizationDepartmentResponse> expected = List.of(
                new OrganizationDepartmentResponse(1L, null, "개발본부", 10L, "홍길동", 5L),
                new OrganizationDepartmentResponse(2L, 1L, "백엔드팀", 11L, "김개발", 3L)
        );
        when(organizationQueryRepository.findAllActiveDepartments()).thenReturn(expected);

        List<OrganizationDepartmentResponse> result = organizationService.getDepartmentTree();

        assertThat(result).containsExactlyElementsOf(expected);
        verify(organizationQueryRepository).findAllActiveDepartments();
    }

    @Test
    void 활성_부서의_직속_구성원을_조회한다() {
        Long departmentId = 1L;
        PageRequest pageable = PageRequest.of(0, 20);
        Department department = mock(Department.class);
        Page<OrganizationMemberResponse> expected = new PageImpl<>(List.of(
                new OrganizationMemberResponse(10L, "홍길동", "TC-26001", 3L, "과장", true)
        ), pageable, 1);
        when(departmentRepository.findByDepartmentIdAndStatus(departmentId, DepartmentStatus.ACTIVE))
                .thenReturn(Optional.of(department));
        when(organizationQueryRepository.findActiveMembersByDepartmentId(departmentId, pageable))
                .thenReturn(expected);

        Page<OrganizationMemberResponse> result = organizationService.getDepartmentMembers(departmentId, pageable);

        assertThat(result).isSameAs(expected);
        verify(organizationQueryRepository).findActiveMembersByDepartmentId(departmentId, pageable);
    }

    @Test
    void 존재하지_않는_부서의_구성원은_조회할_수_없다() {
        Long departmentId = 999L;
        PageRequest pageable = PageRequest.of(0, 20);
        when(departmentRepository.findByDepartmentIdAndStatus(departmentId, DepartmentStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.getDepartmentMembers(departmentId, pageable))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);

        verifyNoInteractions(organizationQueryRepository);
    }
}
