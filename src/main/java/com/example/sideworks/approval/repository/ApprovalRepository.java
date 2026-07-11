package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.entity.Approval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApprovalRepository extends JpaRepository<Approval, Long>, ApprovalRepositoryCustom {

    Optional<Approval> findByApprovalIdAndWriter_LoginId(Long approvalId, String loginId);
}
