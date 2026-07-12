package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.entity.ApprovalLine;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApprovalLineRepository extends JpaRepository<ApprovalLine, Long> {

    boolean existsByApproval_ApprovalIdAndProcessedAtIsNotNull(Long approvalId);

    @EntityGraph(attributePaths = "approver")
    Optional<ApprovalLine> findByApproval_ApprovalIdAndApprovalStep(
            Long approvalId,
            Integer approvalStep
    );

    Optional<ApprovalLine> findFirstByApproval_ApprovalIdAndApprovalStepGreaterThanOrderByApprovalStepAsc(
            Long approvalId,
            Integer approvalStep
    );
}
