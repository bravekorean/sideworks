package com.example.sideworks.attendance.repository;

import com.example.sideworks.attendance.entity.WorkPolicy;
import com.example.sideworks.attendance.entity.WorkPolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface WorkPolicyRepository extends JpaRepository<WorkPolicy, Long> {

    @Query("""
            select policy
            from WorkPolicy policy
            where policy.status = :status
              and policy.effectiveFrom <= :date
              and (policy.effectiveTo is null or policy.effectiveTo >= :date)
            order by policy.effectiveFrom desc, policy.workPolicyId desc
            """)
    List<WorkPolicy> findApplicablePolicies(
            @Param("status") WorkPolicyStatus status,
            @Param("date") LocalDate date
    );

    @Query("""
            select policy
            from WorkPolicy policy
            where policy.status = :status
              and policy.effectiveFrom <= :endDate
              and (policy.effectiveTo is null or policy.effectiveTo >= :startDate)
            order by policy.effectiveFrom asc, policy.workPolicyId asc
            """)
    List<WorkPolicy> findPoliciesOverlapping(
            @Param("status") WorkPolicyStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
