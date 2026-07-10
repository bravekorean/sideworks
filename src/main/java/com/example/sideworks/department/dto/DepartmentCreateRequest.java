package com.example.sideworks.department.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DepartmentCreateRequest {

    private String departmentName;

    private Long parentDepartmentId;
}