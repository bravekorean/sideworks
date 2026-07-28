package com.example.sideworks.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MyProfileUpdateRequest {

    private String userEmail;

    private String userPhone;
}
