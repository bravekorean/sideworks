package com.example.sideworks.user.repository;

import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Collection;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :id")
    Optional<User> lockAttendanceOwner(@Param("id") Long id);

    List<User> findAllByStatusAndUserRoleInOrderByUserNameAsc(UserStatus status,
            Collection<com.example.sideworks.user.entity.UserRole> roles);

    @EntityGraph(attributePaths = {"department", "position"})
    @Query("""
            select u from User u left join u.department d
            where u.status = :status
              and (u.hireDate is null or u.hireDate <= :date)
              and (:allDepartments = true or d.departmentId in :departmentIds)
              and (:departmentId is null or d.departmentId = :departmentId)
              and (:name = '' or locate(lower(:name), lower(u.userName)) > 0)
            order by u.userName asc, u.userId asc
            """)
    Page<User> findAttendanceMembers(
            @Param("status") UserStatus status,
            @Param("date") LocalDate date,
            @Param("allDepartments") boolean allDepartments,
            @Param("departmentIds") Collection<Long> departmentIds,
            @Param("departmentId") Long departmentId,
            @Param("name") String name,
            Pageable pageable
    );

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
