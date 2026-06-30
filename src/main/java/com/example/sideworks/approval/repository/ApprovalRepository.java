package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.entity.Approval;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, Long>, ApprovalRepositoryCustom {
}
