package com.example.sideworks.leave.repository;

import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.leave.entity.LeaveRequestDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface LeaveRequestDayRepository extends JpaRepository<LeaveRequestDay, Long> {
    List<LeaveRequestDay> findByRequest_LeaveRequestIdOrderByLeaveDateAsc(Long leaveRequestId);

    @Query("""
            select day from LeaveRequestDay day
            join fetch day.request request
            where request.user.userId = :userId
              and day.leaveDate between :startDate and :endDate
              and request.approval.approvalStatus in :statuses
              and request.canceledAt is null
            """)
    List<LeaveRequestDay> findConflictingCandidates(@Param("userId") Long userId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("statuses") Collection<ApprovalStatus> statuses);

    @Query("""
            select day from LeaveRequestDay day
            join fetch day.request request
            where request.user.userId = :userId
              and day.leaveDate between :startDate and :endDate
              and request.approval.approvalStatus = :status
              and request.canceledAt is null
            order by day.leaveDate asc
            """)
    List<LeaveRequestDay> findApprovedDays(@Param("userId") Long userId,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("status") ApprovalStatus status);
}
