package com.example.sideworks.department.repository;

import com.example.sideworks.department.entity.Department;
import com.example.sideworks.department.entity.DepartmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByParentDepartmentIsNullAndStatus(DepartmentStatus status);

    List<Department> findByParentDepartment_DepartmentIdAndStatus(Long departmentId, DepartmentStatus status);

    @EntityGraph(attributePaths = "parentDepartment")
    List<Department> findAllByStatusOrderByDepartmentNameAscDepartmentIdAsc(DepartmentStatus status);

    @EntityGraph(attributePaths = "parentDepartment")
    Page<Department> findAllByOrderByCreatedAtDescDepartmentIdDesc(Pageable pageable);

    Optional<Department> findByDepartmentIdAndStatus(Long departmentId, DepartmentStatus status);

    boolean existsByParentDepartment_DepartmentIdAndStatusNot(Long departmentId, DepartmentStatus status);


}
