package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.entity.ApprovalTemplate;
import com.example.sideworks.approval.entity.ApprovalTemplateScope;
import com.example.sideworks.approval.entity.ApprovalTemplateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;

public interface ApprovalTemplateRepository extends JpaRepository<ApprovalTemplate, Long> {
    @EntityGraph(attributePaths = "department")
    @Query("select t from ApprovalTemplate t left join t.department d where t.status = :status and (t.scope = :common or d.departmentId = :departmentId)")
    Page<ApprovalTemplate> findVisible(@Param("status") ApprovalTemplateStatus status,
                                       @Param("common") ApprovalTemplateScope common,
                                       @Param("departmentId") Long departmentId, Pageable pageable);

    @EntityGraph(attributePaths = "department")
    @Query("select t from ApprovalTemplate t left join t.department d where :superAdmin = true or d.departmentId in :managerDepartmentIds")
    Page<ApprovalTemplate> findManageable(@Param("superAdmin") boolean superAdmin,
                                          @Param("managerDepartmentIds") Collection<Long> managerDepartmentIds, Pageable pageable);

    @EntityGraph(attributePaths = "department")
    @Query("select t from ApprovalTemplate t where t.approvalTemplateId = :id")
    Optional<ApprovalTemplate> findWithDepartment(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ApprovalTemplate t where t.approvalTemplateId = :id")
    Optional<ApprovalTemplate> findForBlock(@Param("id") Long id);
}
