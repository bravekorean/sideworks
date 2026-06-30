package com.example.sideworks.department.repository;

import com.example.sideworks.department.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByParentDepartmentIsNull();

    List<Department> findByParentDepartment_DepartmentId(Long departmentId);


}
