package com.example.sideworks.leave.repository;

import com.example.sideworks.leave.entity.LeaveCancellation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeaveCancellationRepository extends JpaRepository<LeaveCancellation, Long> {
    Optional<LeaveCancellation> findByApproval_ApprovalId(Long approvalId);
    boolean existsByPendingLeaveRequestId(Long leaveRequestId);
}
