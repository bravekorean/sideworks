package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.entity.ApprovalLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalLineRepository extends JpaRepository<ApprovalLine, Long> {
}
