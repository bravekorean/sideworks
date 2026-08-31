package com.example.sideworks.user.dto;

import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.entity.JobFamily;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class UserCreateRequest {

    private String loginId;

    private String password;

    private String userName;

    private String userEmail;

    private String userPhone;

    private JobFamily jobFamily;

    private LocalDate hireDate;

    private Long departmentId;

    private Long positionId;

    private UserRole userRole;

    private UserStatus status;
}
