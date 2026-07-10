package com.example.sideworks.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@NoArgsConstructor
public class UserAssignmentRequest {

    private Long departmentId;
    private Long positionId;

}
