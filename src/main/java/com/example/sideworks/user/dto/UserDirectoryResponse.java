package com.example.sideworks.user.dto;

import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserDirectoryResponse {

    private Long userId;
    private String userName;
    private String departmentName;
    private String positionName;
    private UserRole userRole;


    public static UserDirectoryResponse from (User user)  {
        return new UserDirectoryResponse(
                user.getUserId(),
                user.getUserName(),
                user.getDepartment() == null
                        ? null
                        : user.getDepartment().getDepartmentName(),
                user.getPosition() == null
                        ? null
                        : user.getPosition().getPositionName(),
                user.getUserRole()
        );
    }
}
