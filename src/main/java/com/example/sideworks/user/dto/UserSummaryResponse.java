package com.example.sideworks.user.dto;

import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserSummaryResponse {

    private Long userId;

    private String loginId;

    private String userName;

    private String employeeNo;

    private Long departmentId;

    private String departmentName;

    private Long positionId;

    private String positionName;

    private UserRole userRole;

    private UserStatus status;

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getUserId(),
                user.getLoginId(),
                user.getUserName(),
                user.getEmployeeNo(),
                user.getDepartment() == null ? null : user.getDepartment().getDepartmentId(),
                user.getDepartment() == null ? null : user.getDepartment().getDepartmentName(),
                user.getPosition() == null ? null : user.getPosition().getPositionId(),
                user.getPosition() == null ? null : user.getPosition().getPositionName(),
                user.getUserRole(),
                user.getStatus()
        );
    }
}