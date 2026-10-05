package com.example.sideworks.leave.entity;

import com.example.sideworks.common.entity.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "leave_request_daytbl")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveRequestDay extends BaseCreatedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leave_request_day_id")
    private Long leaveRequestDayId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_request_id", nullable = false)
    private LeaveRequest request;

    @Column(name = "leave_date", nullable = false)
    private LocalDate leaveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_period", nullable = false, length = 10)
    private LeavePeriod leavePeriod;

    @Column(name = "days", nullable = false, precision = 2, scale = 1)
    private BigDecimal days;

    public static LeaveRequestDay create(LeaveRequest request, LocalDate date, LeavePeriod period) {
        LeaveRequestDay day = new LeaveRequestDay();
        day.request = Objects.requireNonNull(request);
        day.leaveDate = Objects.requireNonNull(date);
        day.leavePeriod = Objects.requireNonNull(period);
        day.days = period.days();
        return day;
    }
}
