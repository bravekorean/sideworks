package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.entity.ApprovalDelegation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface ApprovalDelegationRepository extends JpaRepository<ApprovalDelegation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ApprovalDelegation d where d.delegator.userId = :delegatorId "
            + "and d.canceledAt is null and d.startDate <= :endDate and d.endDate >= :startDate")
    List<ApprovalDelegation> findOverlappingForUpdate(@Param("delegatorId") Long delegatorId,
                                                       @Param("startDate") LocalDate startDate,
                                                       @Param("endDate") LocalDate endDate);

    @Modifying
    @Query("update ApprovalDelegation d set d.canceledAt = :at where d.approvalDelegationId = :id and d.canceledAt is null")
    int cancelIfActive(@Param("id") Long id, @Param("at") LocalDateTime at);

    @EntityGraph(attributePaths = "delegatee")
    @Lock(LockModeType.PESSIMISTIC_READ)
    List<ApprovalDelegation> findByDelegator_UserIdAndCanceledAtIsNullAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long delegatorId, LocalDate dateForStart, LocalDate dateForEnd);

    @EntityGraph(attributePaths = {"delegator", "delegatee", "creator"})
    List<ApprovalDelegation> findByDelegator_UserIdOrderByStartDateDescApprovalDelegationIdDesc(Long delegatorId);
}
