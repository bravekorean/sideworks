package com.example.sideworks.user.dto;

import com.example.sideworks.user.entity.UserStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserStatusUpdateRequest {

    private UserStatus status;
}
