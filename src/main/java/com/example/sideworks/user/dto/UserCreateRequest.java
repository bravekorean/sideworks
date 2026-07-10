package com.example.sideworks.user.dto;

import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserCreateRequest {

    private String loginId;

    private String password;

    private String userName;

    private String userEmail;

    private String userPhone;

    private String employeeNo;

    private Long departmentId;

    private Long positionId;

    private UserRole userRole;

    private UserStatus status;
}