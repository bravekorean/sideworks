package com.example.sideworks.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResult {

    private LoginResponse loginResponse;

    private String refreshToken;
}
