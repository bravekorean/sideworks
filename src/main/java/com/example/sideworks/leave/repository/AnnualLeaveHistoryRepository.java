package com.example.sideworks.leave.repository;

import com.example.sideworks.leave.entity.AnnualLeaveHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnualLeaveHistoryRepository extends JpaRepository<AnnualLeaveHistory, Long> {
    List<AnnualLeaveHistory> findByBalance_AnnualLeaveBalanceIdOrderByCreatedAtDescAnnualLeaveHistoryIdDesc(Long balanceId);
}
