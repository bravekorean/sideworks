package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.entity.Approval;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ApprovalRepository extends JpaRepository<Approval, Long>, ApprovalRepositoryCustom {

    Optional<Approval> findByApprovalIdAndWriter_LoginId(Long approvalId, String loginId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Approval a where a.approvalId = :approvalId")
    Optional<Approval> findByIdForUpdate(@Param("approvalId") Long approvalId);
}
