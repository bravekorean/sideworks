package com.example.sideworks.approval.controller;

import com.example.sideworks.approval.dto.ApprovalTemplateResolveResponse;
import com.example.sideworks.approval.dto.ApprovalTemplateDepartmentResponse;
import com.example.sideworks.approval.dto.ApprovalTemplateResponse;
import com.example.sideworks.approval.dto.ApprovalTemplateSaveRequest;
import com.example.sideworks.approval.service.ApprovalTemplateService;
import com.example.sideworks.approval.entity.ApprovalTemplateScope;
import com.example.sideworks.user.dto.UserDirectoryResponse;
import com.example.sideworks.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/approval-templates")
public class ApprovalTemplateController {
    private final ApprovalTemplateService service;

    @GetMapping("/manage/members")
    public PageResponse<UserDirectoryResponse> manageableMembers(Authentication authentication,
            @RequestParam ApprovalTemplateScope scope,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "false") boolean reference,
            @PageableDefault(size = 100) Pageable pageable) {
        return PageResponse.from(service.manageableMembers(authentication.getName(), scope, departmentId, reference, pageable));
    }

    @GetMapping("/manage/departments")
    public List<ApprovalTemplateDepartmentResponse> manageableDepartments(Authentication authentication) {
        return service.manageableDepartments(authentication.getName());
    }

    @GetMapping
    public PageResponse<ApprovalTemplateResponse> available(Authentication authentication,
                                                             @PageableDefault(size = 20, sort = "approvalTemplateId", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(service.available(authentication.getName(), pageable));
    }

    @GetMapping("/manage")
    public PageResponse<ApprovalTemplateResponse> manageable(Authentication authentication,
                                                              @PageableDefault(size = 20, sort = "approvalTemplateId", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(service.manageable(authentication.getName(), pageable));
    }

    @GetMapping("/manage/{id}")
    public ApprovalTemplateResponse manageDetail(Authentication authentication, @PathVariable Long id) {
        return service.manageDetail(authentication.getName(), id);
    }

    @PostMapping("/{id}/resolve")
    public ApprovalTemplateResolveResponse resolve(Authentication authentication, @PathVariable Long id) {
        return service.resolve(authentication.getName(), id);
    }

    @PostMapping
    public ResponseEntity<ApprovalTemplateResponse> create(Authentication authentication,
                                                            @RequestBody ApprovalTemplateSaveRequest request) {
        ApprovalTemplateResponse response = service.create(authentication.getName(), request);
        return ResponseEntity.created(URI.create("/api/approval-templates/manage/" + response.approvalTemplateId()))
                .body(response);
    }

    @PutMapping("/{id}")
    public ApprovalTemplateResponse update(Authentication authentication, @PathVariable Long id,
                                           @RequestBody ApprovalTemplateSaveRequest request) {
        return service.update(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id,
                                       @RequestParam Long version) {
        service.delete(authentication.getName(), id, version);
        return ResponseEntity.noContent().build();
    }
}
