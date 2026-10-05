package com.example.sideworks.approval.controller;

import com.example.sideworks.approval.dto.ApprovalDelegationResponse;
import com.example.sideworks.approval.dto.ApprovalDelegationSaveRequest;
import com.example.sideworks.approval.service.ApprovalDelegationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/approval-delegations")
@SecurityRequirement(name = "bearerAuth")
public class ApprovalDelegationController {
    private final ApprovalDelegationService delegations;

    @GetMapping
    @Operation(summary = "결재 위임 목록")
    public List<ApprovalDelegationResponse> list(Authentication authentication,
                                                  @RequestParam(required = false) Long delegatorId) {
        return delegations.list(authentication.getName(), delegatorId);
    }

    @PostMapping
    @Operation(summary = "기간제 결재 위임 등록")
    public ResponseEntity<ApprovalDelegationResponse> create(Authentication authentication,
                                                              @RequestBody ApprovalDelegationSaveRequest request) {
        ApprovalDelegationResponse response = delegations.create(authentication.getName(), request);
        return ResponseEntity.created(URI.create("/api/approval-delegations/" + response.approvalDelegationId()))
                .body(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "결재 위임 취소")
    public ResponseEntity<Void> cancel(Authentication authentication, @PathVariable Long id) {
        delegations.cancel(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
