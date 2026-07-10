package com.example.sideworks.user.repository;

import com.example.sideworks.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmployeeNo(String employeeNo);

    List<User> findAllByDepartmentIsNull();

    List<User> findAllByPositionIsNull();

    List<User> findAllByDepartmentIsNullOrPositionIsNull();

    List<User> findAllByOrderByCreatedAtDesc();

    boolean existsByDepartment_DepartmentId(Long departmentId);

    boolean existsByPosition_PositionId(Long positionId);
}
