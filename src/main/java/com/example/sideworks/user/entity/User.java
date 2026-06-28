package com.example.sideworks.user.entity;

import com.example.sideworks.department.entity.Department;
import com.example.sideworks.position.entity.Position;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

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
@EntityListeners(AuditingEntityListener.class)
public class User {

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
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id", nullable = false)
    private Position position;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 50)
    private UserRole userRole;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 45)
    private UserStatus status;
}
