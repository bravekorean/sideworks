package com.example.sideworks.department.dto;

import com.example.sideworks.department.entity.Department;
import com.example.sideworks.department.entity.DepartmentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DepartmentResponse {

    private Long departmentId;

    private Long parentDepartmentId;

    private String departmentName;

    private Long managerUserId;

    private DepartmentStatus status;

    public static DepartmentResponse from(Department department) {
        Long parentDepartmentId = department.getParentDepartment() == null ? null : department.getParentDepartment().getDepartmentId();

        return new DepartmentResponse(
                department.getDepartmentId(),
                parentDepartmentId,
                department.getDepartmentName(),
                department.getManagerUserId(),
                department.getStatus()
        );
    }
}
