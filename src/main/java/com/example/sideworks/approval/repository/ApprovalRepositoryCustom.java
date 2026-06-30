package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.entity.Approval;

import java.util.List;

public interface ApprovalRepositoryCustom {

    List<Approval> findDraftsByWriterId(Long writerId);

    List<Approval> findSentByWriterId(Long writerId);

    List<Approval> findPendingByApproverId(Long approverId);

    List<Approval> findProcessedByApproverId(Long approverId);

    List<Approval> findCcByUserId(Long userId);
}
