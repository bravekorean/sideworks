package com.example.sideworks.department.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DepartmentUpdateRequest {

    private String departmentName;

    private Long parentDepartmentId;
}