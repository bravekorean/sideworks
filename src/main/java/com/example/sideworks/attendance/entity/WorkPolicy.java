package com.example.sideworks.attendance.entity;

import com.example.sideworks.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "work_policytbl")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkPolicy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "work_policy_id")
    private Long workPolicyId;

    @Column(name = "policy_name", nullable = false, length = 100)
    private String policyName;

    @Column(name = "work_start_time", nullable = false)
    private LocalTime workStartTime;

    @Column(name = "work_end_time", nullable = false)
    private LocalTime workEndTime;

    @Column(name = "break_start_time", nullable = false)
    private LocalTime breakStartTime;

    @Column(name = "break_end_time", nullable = false)
    private LocalTime breakEndTime;

    @Column(name = "late_grace_minutes", nullable = false)
    private int lateGraceMinutes;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkPolicyStatus status;

    public boolean isApplicableOn(LocalDate date) {
        boolean started = !date.isBefore(effectiveFrom);
        boolean notEnded = effectiveTo == null || !date.isAfter(effectiveTo);

        return status == WorkPolicyStatus.ACTIVE && started && notEnded;
    }

    public LocalDateTime scheduledCheckInAt(LocalDate date) {
        return date.atTime(workStartTime);
    }

    public LocalDateTime scheduledCheckOutAt(LocalDate date) {
        return date.atTime(workEndTime);
    }
}