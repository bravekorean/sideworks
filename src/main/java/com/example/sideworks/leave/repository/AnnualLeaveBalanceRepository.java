package com.example.sideworks.leave.repository;

import com.example.sideworks.leave.entity.AnnualLeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnnualLeaveBalanceRepository extends JpaRepository<AnnualLeaveBalance, Long> {
    Optional<AnnualLeaveBalance> findByUser_UserIdAndLeaveYear(Long userId, int leaveYear);
}
