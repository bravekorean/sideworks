package com.example.sideworks.user.entity;

import com.example.sideworks.common.entity.BaseTimeEntity;
import com.example.sideworks.department.entity.Department;
import com.example.sideworks.position.entity.Position;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "usertbl",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_usertbl_login_id", columnNames = "login_id"),
                @UniqueConstraint(name = "uk_usertbl_employee_no", columnNames = "employee_no")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "login_id", nullable = false, length = 200)
    private String loginId;

    @Column(name = "user_pass", nullable = false, length = 200)
    private String password;

    @Column(name = "user_name", nullable = false, length = 100)
    private String userName;

    @Column(name = "user_email", length = 200)
    private String userEmail;

    @Column(name = "user_phone", length = 100)
    private String userPhone;

    @Column(name = "employee_no", nullable = false, length = 100)
    private String employeeNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 50)
    private UserRole userRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 45)
    private UserStatus status;


    public void assignDepartment(Department department) {
        this.department = department;
    }

    public void assignPosition(Position position) {
        this.position = position;
    }

    public void changeStatus(UserStatus status) {
        this.status = status;
    }

    public void changeRole(UserRole userRole) {
        this.userRole = userRole;
    }

    public static User create(String loginId, String password, String userName, String userEmail, String userPhone, String employeeNo,
                              Department department, Position position, UserRole userRole, UserStatus status) {

        User user = new User();

        user.loginId = loginId;
        user.password = password;
        user.userName = userName;
        user.userEmail = userEmail;
        user.userPhone = userPhone;
        user.employeeNo = employeeNo;
        user.department = department;
        user.position = position;
        user.userRole = userRole;
        user.status = status;

        return user;
    }


    public void updateBasicInfo(String userName, String userEmail, String userPhone, String employeeNo) {

        this.userName = userName;
        this.userEmail = userEmail;
        this.userPhone = userPhone;
        this.employeeNo = employeeNo;

    }
}
