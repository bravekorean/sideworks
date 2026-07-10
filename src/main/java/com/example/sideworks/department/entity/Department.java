package com.example.sideworks.department.entity;

import com.example.sideworks.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "departmenttbl")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "department_id")
    private Long departmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_department_id")
    private Department parentDepartment;

    @Column(name = "department_name", nullable = false, length = 100)
    private String departmentName;

    @Column(name = "manager_user_id")
    private Long managerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DepartmentStatus status;

    public static Department create(String departmentName, Department parentDepartment) {
        Department department = new Department();
        department.departmentName = departmentName;
        department.parentDepartment = parentDepartment;
        department.status = DepartmentStatus.ACTIVE;

        return department;
    }

    public void update(String departmentName, Department parentDepartment) {
        this.departmentName = departmentName;
        this.parentDepartment = parentDepartment;
    }

    public void delete() {
        this.status = DepartmentStatus.DELETED;
    }

    public void assignManager(Long managerUserId) {
        this.managerUserId = managerUserId;
    }

    public void removeManager() {
        this.managerUserId = null;
    }
}
