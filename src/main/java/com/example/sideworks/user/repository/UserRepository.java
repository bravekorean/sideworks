package com.example.sideworks.user.repository;

import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    List<User> findAllByDepartmentIsNull();

    List<User> findAllByPositionIsNull();

    @EntityGraph(attributePaths = {"department", "position"})
    Page<User> findAllByDepartmentIsNullOrPositionIsNullOrderByCreatedAtDescUserIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"department", "position"})
    Page<User> findAllByOrderByCreatedAtDescUserIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"department", "position"})
    Page<User> findAllByStatusAndLoginIdNotOrderByUserNameAscUserIdAsc(UserStatus status, String loginId, Pageable pageable);

    @EntityGraph(attributePaths = {"department", "position"})
    Optional<User> findProfileByLoginId(String loginId);

    boolean existsByDepartment_DepartmentId(Long departmentId);

    boolean existsByPosition_PositionId(Long positionId);
}
