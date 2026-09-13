package com.example.sideworks.organization.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrganizationMemberResponse {

    private Long userId;
    private String userName;
    private String employeeNo;
    private Long positionId;
    private String positionName;
    private boolean manager;
}
