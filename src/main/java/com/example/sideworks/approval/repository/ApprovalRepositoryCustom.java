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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ApprovalRepositoryCustom {

    Page<ApprovalListResponse> findForManagement(String keyword, ApprovalStatus status, boolean blockedOnly,
                                                 LocalDate today, Pageable pageable);

    Page<ApprovalListResponse> findDraftsByWriterId(Long writerId, String keyword, Pageable pageable);

    Page<ApprovalListResponse> findSentByWriterId(Long writerId, String keyword, ApprovalStatus status, Pageable pageable);

    // today: 애플리케이션 Clock 기준 날짜. 대결 기간을 반영해 지금 내가 처리할 대기 단계를 찾는다.
    Page<ApprovalListResponse> findPendingByProcessorId(Long processorId, LocalDate today, String keyword, Pageable pageable);

    // 실제로 승인·반려한 사람 기준이다. 대결로 처리한 문서는 대결자의 처리함에 나온다.
    Page<ApprovalListResponse> findProcessedByProcessorId(Long processorId, String keyword, ApprovalStatus status, Pageable pageable);

    Page<ApprovalListResponse> findCcByUserId(Long userId, String keyword, ApprovalStatus status, Pageable pageable);

    Page<ApprovalActivityResponse> findRecentActivitiesByUserId(Long userId, Pageable pageable);

    long countInProgressByWriterId(Long writerId);

    long countDraftsByWriterId(Long writerId);

    long countCcByUserId(Long userId);

    List<ApprovalActivityResponse> findLatestActivitiesByUserId(Long userId, int limit);

    Page<ApprovalListResponse> searchApprovals(
            Long userId,
            boolean searchAll,
            String keyword,
            Pageable pageable
    );

    Optional<ApprovalDetailHeaderResponse> findDetailHeaderByApprovalId(Long approvalId);

    Optional<ApprovalDetailHeaderResponse> findAccessibleDetailHeader(Long approvalId, Long userId, LocalDate today);

    List<ApprovalLineResponse> findDetailLinesByApprovalId(Long approvalId, LocalDate today);

    List<ApprovalCcResponse> findDetailCcsByApprovalId(Long approvalId);

    List<ApprovalHistoryResponse> findDetailHistoriesByApprovalId(Long approvalId);
}
