package com.example.sideworks.approval.controller;

import com.example.sideworks.approval.dto.ApprovalCreateResponse;
import com.example.sideworks.approval.dto.ApprovalDetailResponse;
import com.example.sideworks.approval.dto.ApprovalDecisionRequest;
import com.example.sideworks.approval.dto.ApprovalDraftRequest;
import com.example.sideworks.approval.dto.ApprovalListResponse;
import com.example.sideworks.approval.dto.ApprovalSubmitRequest;
import com.example.sideworks.approval.service.ApprovalService;
import com.example.sideworks.common.dto.PageResponse;
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
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping
    public ResponseEntity<ApprovalCreateResponse> createDraft(Authentication authentication, @RequestBody ApprovalDraftRequest request) {
        Long approvalId = approvalService.createDraft(authentication.getName(), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApprovalCreateResponse(approvalId));
    }

    @PutMapping("/{approvalId}")
    public ResponseEntity<Void> updateDraft(@PathVariable("approvalId") Long approvalId, Authentication authentication, @RequestBody ApprovalDraftRequest request) {
        approvalService.updateDraft(approvalId, authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{approvalId}")
    public ResponseEntity<Void> deleteDraft(@PathVariable("approvalId") Long approvalId, Authentication authentication) {
        approvalService.deleteDraft(approvalId, authentication.getName());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{approvalId}/submit")
    public ResponseEntity<Void> submitApproval(@PathVariable("approvalId") Long approvalId, Authentication authentication, @RequestBody ApprovalSubmitRequest request) {
        approvalService.submitApproval(approvalId, authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{approvalId}/approve")
    public ResponseEntity<Void> approveApproval(@PathVariable("approvalId") Long approvalId, Authentication authentication, @RequestBody(required = false) ApprovalDecisionRequest request) {
        approvalService.approveApproval(approvalId, authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{approvalId}/reject")
    public ResponseEntity<Void> rejectApproval(@PathVariable("approvalId") Long approvalId, Authentication authentication, @RequestBody(required = false) ApprovalDecisionRequest request) {
        approvalService.rejectApproval(approvalId, authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{approvalId}/cancel")
    public ResponseEntity<Void> cancelApproval(@PathVariable("approvalId") Long approvalId, Authentication authentication) {
        approvalService.cancelApproval(approvalId, authentication.getName());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{approvalId}")
    public ResponseEntity<ApprovalDetailResponse> getApprovalDetail(@PathVariable("approvalId") Long approvalId, Authentication authentication) {
        ApprovalDetailResponse response = approvalService.getApprovalDetail(
                approvalId,
                authentication.getName()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/drafts")
    public ResponseEntity<PageResponse<ApprovalListResponse>> getDraftApprovals(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        Page<ApprovalListResponse> response = approvalService.getDraftApprovals(authentication.getName(), pageable);

        return ResponseEntity.ok(PageResponse.from(response));
    }

    @GetMapping("/sent")
    public ResponseEntity<PageResponse<ApprovalListResponse>> getSentApprovals(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        Page<ApprovalListResponse> response = approvalService.getSentApprovals(authentication.getName(), pageable);

        return ResponseEntity.ok(PageResponse.from(response));
    }

    @GetMapping("/pending")
    public ResponseEntity<PageResponse<ApprovalListResponse>> getPendingApprovals(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        Page<ApprovalListResponse> response = approvalService.getPendingApprovals(authentication.getName(), pageable);

        return ResponseEntity.ok(PageResponse.from(response));
    }

    @GetMapping("/processed")
    public ResponseEntity<PageResponse<ApprovalListResponse>> getProcessedApprovals(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        Page<ApprovalListResponse> response = approvalService.getProcessedApprovals(authentication.getName(), pageable);

        return ResponseEntity.ok(PageResponse.from(response));
    }

    @GetMapping("/cc")
    public ResponseEntity<PageResponse<ApprovalListResponse>> getCcApprovals(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        Page<ApprovalListResponse> response = approvalService.getCcApprovals(authentication.getName(), pageable);

        return ResponseEntity.ok(PageResponse.from(response));
    }
}
