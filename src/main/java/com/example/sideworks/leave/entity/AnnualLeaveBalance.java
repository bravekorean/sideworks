package com.example.sideworks.leave.entity;

import com.example.sideworks.common.entity.BaseTimeEntity;
import com.example.sideworks.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "annual_leave_balancetbl", uniqueConstraints =
        @UniqueConstraint(name = "uk_annual_leave_balance_user_year", columnNames = {"user_id", "leave_year"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnualLeaveBalance extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "annual_leave_balance_id")
    private Long annualLeaveBalanceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "leave_year", nullable = false)
    private int leaveYear;

    @Column(name = "granted_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal grantedDays;

    @Column(name = "remaining_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal remainingDays;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static AnnualLeaveBalance grant(User user, int year, BigDecimal days) {
        Objects.requireNonNull(user);
        Objects.requireNonNull(days);
        if (year < 2000 || year > 9999 || days.compareTo(BigDecimal.ZERO) <= 0
                || days.compareTo(new BigDecimal("25.0")) > 0) {
            throw new IllegalArgumentException("유효하지 않은 연차 부여량 또는 연도입니다.");
        }
        AnnualLeaveBalance balance = new AnnualLeaveBalance();
        balance.user = user;
        balance.leaveYear = year;
        balance.grantedDays = days.setScale(1);
        balance.remainingDays = balance.grantedDays;
        return balance;
    }

    public void use(BigDecimal days) {
        if (days == null || days.compareTo(BigDecimal.ZERO) <= 0 || remainingDays.compareTo(days) < 0) {
            throw new IllegalArgumentException("사용 가능한 연차 잔액이 부족합니다.");
        }
        remainingDays = remainingDays.subtract(days);
    }

    public void restore(BigDecimal days) {
        if (days == null || days.compareTo(BigDecimal.ZERO) <= 0
                || remainingDays.add(days).compareTo(grantedDays) > 0) {
            throw new IllegalArgumentException("복원 가능한 연차 범위를 초과했습니다.");
        }
        remainingDays = remainingDays.add(days);
    }
}
