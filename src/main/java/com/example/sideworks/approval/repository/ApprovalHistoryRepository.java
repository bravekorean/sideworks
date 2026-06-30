package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.entity.ApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {

}


