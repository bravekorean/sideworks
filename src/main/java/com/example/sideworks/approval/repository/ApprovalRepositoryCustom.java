package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.dto.ApprovalCcResponse;
import com.example.sideworks.approval.dto.ApprovalActivityResponse;
import com.example.sideworks.approval.dto.ApprovalDetailHeaderResponse;
import com.example.sideworks.approval.dto.ApprovalHistoryResponse;
import com.example.sideworks.approval.dto.ApprovalLineResponse;
import com.example.sideworks.approval.dto.ApprovalListResponse;
import com.example.sideworks.approval.entity.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ApprovalRepositoryCustom {

    Page<ApprovalListResponse> findDraftsByWriterId(Long writerId, String keyword, Pageable pageable);

    Page<ApprovalListResponse> findSentByWriterId(Long writerId, String keyword, ApprovalStatus status, Pageable pageable);

    Page<ApprovalListResponse> findPendingByApproverId(Long approverId, String keyword, Pageable pageable);

    Page<ApprovalListResponse> findProcessedByApproverId(Long approverId, String keyword, ApprovalStatus status, Pageable pageable);

    Page<ApprovalListResponse> findCcByUserId(Long userId, String keyword, ApprovalStatus status, Pageable pageable);

    Page<ApprovalActivityResponse> findRecentActivitiesByUserId(Long userId, Pageable pageable);

    Page<ApprovalListResponse> searchApprovals(
            Long userId,
            boolean searchAll,
            String keyword,
            Pageable pageable
    );

    Optional<ApprovalDetailHeaderResponse> findDetailHeaderByApprovalId(Long approvalId);

    Optional<ApprovalDetailHeaderResponse> findAccessibleDetailHeader(Long approvalId, Long userId);

    List<ApprovalLineResponse> findDetailLinesByApprovalId(Long approvalId);

    List<ApprovalCcResponse> findDetailCcsByApprovalId(Long approvalId);

    List<ApprovalHistoryResponse> findDetailHistoriesByApprovalId(Long approvalId);
}
