package com.example.sideworks.approval.controller;

import com.example.sideworks.approval.dto.ApprovalCreateResponse;
import com.example.sideworks.approval.dto.ApprovalActivityResponse;
import com.example.sideworks.approval.dto.ApprovalDetailResponse;
import com.example.sideworks.approval.dto.ApprovalDecisionRequest;
import com.example.sideworks.approval.dto.ApprovalDraftRequest;
import com.example.sideworks.approval.dto.ApprovalListResponse;
import com.example.sideworks.approval.dto.ApprovalSubmitRequest;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.approval.service.ApprovalService;
import com.example.sideworks.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/approvals")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "전자결재", description = "결재 문서 작성, 상신, 조회 및 승인·반려 처리 API")
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping
    @Operation(summary = "결재 문서 임시저장", description = "현재 사용자를 작성자로 하여 새로운 결재 문서를 임시저장합니다.")
    public ResponseEntity<ApprovalCreateResponse> createDraft(Authentication authentication, @RequestBody ApprovalDraftRequest request) {
        Long approvalId = approvalService.createDraft(authentication.getName(), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApprovalCreateResponse(approvalId));
    }

    @PutMapping("/{approvalId}")
    @Operation(summary = "임시저장 문서 수정", description = "작성자가 임시저장 상태인 결재 문서의 제목과 내용을 수정합니다.")
    public ResponseEntity<Void> updateDraft(@PathVariable("approvalId") Long approvalId, Authentication authentication, @RequestBody ApprovalDraftRequest request) {
        approvalService.updateDraft(approvalId, authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{approvalId}")
    @Operation(summary = "임시저장 문서 삭제", description = "작성자가 임시저장 상태인 결재 문서를 삭제합니다.")
    public ResponseEntity<Void> deleteDraft(@PathVariable("approvalId") Long approvalId, Authentication authentication) {
        approvalService.deleteDraft(approvalId, authentication.getName());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{approvalId}/submit")
    @Operation(summary = "결재 문서 상신", description = "임시저장 문서에 결재선과 참조자를 지정하고 결재를 시작합니다.")
    public ResponseEntity<Void> submitApproval(@PathVariable("approvalId") Long approvalId, Authentication authentication, @RequestBody ApprovalSubmitRequest request) {
        approvalService.submitApproval(approvalId, authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{approvalId}/approve")
    @Operation(summary = "결재 승인", description = "현재 결재자가 문서를 승인하며, 다음 결재자를 활성화하거나 최종 승인 상태로 변경합니다.")
    public ResponseEntity<Void> approveApproval(@PathVariable("approvalId") Long approvalId, Authentication authentication, @RequestBody(required = false) ApprovalDecisionRequest request) {
        approvalService.approveApproval(approvalId, authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{approvalId}/reject")
    @Operation(summary = "결재 반려", description = "현재 결재자가 의견과 함께 문서를 반려하고 결재 처리를 종료합니다.")
    public ResponseEntity<Void> rejectApproval(@PathVariable("approvalId") Long approvalId, Authentication authentication, @RequestBody(required = false) ApprovalDecisionRequest request) {
        approvalService.rejectApproval(approvalId, authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{approvalId}/cancel")
    @Operation(summary = "결재 상신 취소", description = "작성자가 진행 중인 결재 문서의 상신을 취소합니다.")
    public ResponseEntity<Void> cancelApproval(@PathVariable("approvalId") Long approvalId, Authentication authentication) {
        approvalService.cancelApproval(approvalId, authentication.getName());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{approvalId}")
    @Operation(summary = "결재 문서 상세 조회", description = "작성자, 결재자, 참조자 또는 권한이 있는 관리자가 문서 내용과 결재선 및 처리 이력을 조회합니다.")
    public ResponseEntity<ApprovalDetailResponse> getApprovalDetail(@PathVariable("approvalId") Long approvalId, Authentication authentication) {
        ApprovalDetailResponse response = approvalService.getApprovalDetail(
                approvalId,
                authentication.getName()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/drafts")
    @Operation(summary = "임시저장함 조회", description = "현재 사용자가 작성한 임시저장 문서를 검색 조건과 페이지 단위로 조회합니다.")
    public ResponseEntity<PageResponse<ApprovalListResponse>> getDraftApprovals(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ApprovalListResponse> response = approvalService.getDraftApprovals(authentication.getName(), keyword, pageable);

        return ResponseEntity.ok(PageResponse.from(response));
    }

    @GetMapping("/sent")
    @Operation(summary = "내가 작성한 문서 조회", description = "현재 사용자가 상신한 문서를 제목과 상태 조건으로 조회합니다.")
    public ResponseEntity<PageResponse<ApprovalListResponse>> getSentApprovals(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ApprovalStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ApprovalListResponse> response = approvalService.getSentApprovals(authentication.getName(), keyword, status, pageable);

        return ResponseEntity.ok(PageResponse.from(response));
    }

    @GetMapping("/pending")
    @Operation(summary = "결재 대기함 조회", description = "현재 사용자가 지금 처리해야 하는 결재 문서를 조회합니다.")
    public ResponseEntity<PageResponse<ApprovalListResponse>> getPendingApprovals(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ApprovalListResponse> response = approvalService.getPendingApprovals(authentication.getName(), keyword, pageable);

        return ResponseEntity.ok(PageResponse.from(response));
    }

    @GetMapping("/processed")
    @Operation(summary = "결재 처리함 조회", description = "현재 사용자가 이미 승인하거나 반려한 결재 문서를 조회합니다.")
    public ResponseEntity<PageResponse<ApprovalListResponse>> getProcessedApprovals(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ApprovalStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ApprovalListResponse> response = approvalService.getProcessedApprovals(authentication.getName(), keyword, status, pageable);

        return ResponseEntity.ok(PageResponse.from(response));
    }

    @GetMapping("/cc")
    @Operation(summary = "참조 문서함 조회", description = "현재 사용자가 참조자로 지정된 결재 문서를 조회합니다.")
    public ResponseEntity<PageResponse<ApprovalListResponse>> getCcApprovals(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ApprovalStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ApprovalListResponse> response = approvalService.getCcApprovals(authentication.getName(), keyword, status, pageable);

        return ResponseEntity.ok(PageResponse.from(response));
    }

    @GetMapping("/activities")
    @Operation(summary = "최근 결재 활동 조회", description = "현재 사용자와 관련된 최근 결재 처리 이력을 조회합니다.")
    public ResponseEntity<PageResponse<ApprovalActivityResponse>> getRecentActivities(
            Authentication authentication,
            @PageableDefault(size = 5) Pageable pageable
    ) {
        Page<ApprovalActivityResponse> response = approvalService.getRecentActivities(
                authentication.getName(),
                pageable
        );

        return ResponseEntity.ok(PageResponse.from(response));
    }

    @GetMapping("/search")
    @Operation(summary = "결재 문서 통합 검색", description = "제목, 작성자, 결재자 또는 참조자 기준으로 접근 권한이 있는 문서를 검색합니다. SUPER_ADMIN은 전체 문서를 검색할 수 있습니다.")
    public ResponseEntity<PageResponse<ApprovalListResponse>> searchApprovals(
            Authentication authentication,
            @RequestParam String keyword,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ApprovalListResponse> response = approvalService.searchApprovals(
                authentication.getName(),
                keyword,
                pageable
        );

        return ResponseEntity.ok(PageResponse.from(response));
    }
}
