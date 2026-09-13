package com.example.sideworks.organization.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrganizationDepartmentResponse {

    private Long departmentId;
    private Long parentDepartmentId;
    private String departmentName;
    private Long managerUserId;
    private String managerName;
    private long memberCount;
}