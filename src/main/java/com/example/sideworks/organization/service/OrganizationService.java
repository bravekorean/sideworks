package com.example.sideworks.organization.service;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.department.entity.DepartmentStatus;
import com.example.sideworks.department.repository.DepartmentRepository;
import com.example.sideworks.organization.dto.OrganizationDepartmentResponse;
import com.example.sideworks.organization.dto.OrganizationMemberResponse;
import com.example.sideworks.organization.repository.OrganizationQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationQueryRepository organizationQueryRepository;
    private final DepartmentRepository departmentRepository;

    public List<OrganizationDepartmentResponse> getDepartmentTree() {
        return organizationQueryRepository.findAllActiveDepartments();
    }

    public Page<OrganizationMemberResponse> getDepartmentMembers(Long departmentId, Pageable pageable) {
        departmentRepository.findByDepartmentIdAndStatus(departmentId, DepartmentStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));

        return organizationQueryRepository.findActiveMembersByDepartmentId(departmentId, pageable);
    }
}
