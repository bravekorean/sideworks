package com.example.sideworks.auth.dto;

import com.example.sideworks.user.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;

    private Long userId;

    private String loginId;

    private String userName;

    private UserRole userRole;
}
