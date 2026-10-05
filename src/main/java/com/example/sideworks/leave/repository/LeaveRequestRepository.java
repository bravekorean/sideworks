package com.example.sideworks.leave.repository;

import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    Optional<LeaveRequest> findByApproval_ApprovalId(Long approvalId);

    @Query("""
            select request from LeaveRequest request
            where request.user.userId = :userId
              and request.approval.approvalStatus = :status
              and request.canceledAt is null
            order by request.leaveRequestId desc
            """)
    List<LeaveRequest> findActiveApproved(@Param("userId") Long userId,
                                          @Param("status") ApprovalStatus status);

    @Query("""
            select sum(request.totalDays)
            from LeaveRequest request
            where request.user.loginId = :loginId
              and request.balance.leaveYear = :year
              and request.approval.approvalStatus = :status
            """)
    BigDecimal sumPendingDays(@Param("loginId") String loginId, @Param("year") int year,
                              @Param("status") ApprovalStatus status);
}
