package com.example.sideworks.user.dto;

import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class UserDetailResponse {

    private Long userId;

    private String loginId;

    private String userName;

    private String userEmail;

    private String userPhone;

    private String employeeNo;

    private Long departmentId;

    private String departmentName;

    private Long positionId;

    private String positionName;

    private UserRole userRole;

    private UserStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static UserDetailResponse from(User user) {
        return new UserDetailResponse(
                user.getUserId(),
                user.getLoginId(),
                user.getUserName(),
                user.getUserEmail(),
                user.getUserPhone(),
                user.getEmployeeNo(),
                user.getDepartment() == null ? null : user.getDepartment().getDepartmentId(),
                user.getDepartment() == null ? null : user.getDepartment().getDepartmentName(),
                user.getPosition() == null ? null : user.getPosition().getPositionId(),
                user.getPosition() == null ? null : user.getPosition().getPositionName(),
                user.getUserRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}