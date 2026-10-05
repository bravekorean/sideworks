package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.entity.ApprovalCc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;

public interface ApprovalCcRepository extends JpaRepository<ApprovalCc, Long> {
    @EntityGraph(attributePaths = "ccUser")
    List<ApprovalCc> findByApproval_ApprovalId(Long approvalId);
}
