package com.example.sideworks.approval.controller;

import com.example.sideworks.approval.dto.ApprovalCreateResponse;
import com.example.sideworks.approval.dto.ApprovalDraftRequest;
import com.example.sideworks.approval.dto.ApprovalSubmitRequest;
import com.example.sideworks.approval.service.ApprovalService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<Void> submitApproval(
            @PathVariable("approvalId") Long approvalId,
            Authentication authentication,
            @RequestBody ApprovalSubmitRequest request
    ) {
        approvalService.submitApproval(approvalId, authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }
}
