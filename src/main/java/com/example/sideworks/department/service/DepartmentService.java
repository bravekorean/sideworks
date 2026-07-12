package com.example.sideworks.department.service;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.department.dto.DepartmentResponse;
import com.example.sideworks.department.entity.Department;
import com.example.sideworks.department.entity.DepartmentStatus;
import com.example.sideworks.department.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public List<DepartmentResponse> findAllDepartments() {
        return departmentRepository
                .findAllByStatusOrderByDepartmentNameAscDepartmentIdAsc(DepartmentStatus.ACTIVE)
                .stream()
                .map(DepartmentResponse::from)
                .toList();
    }

    public DepartmentResponse findDepartmentById(Long departmentId) {
        Department department = departmentRepository.findByDepartmentIdAndStatus(departmentId, DepartmentStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));

        return DepartmentResponse.from(department);
    }
}
