package com.example.sideworks.approval.controller;

import com.example.sideworks.approval.dto.ApprovalDocumentTypeAdminResponse;
import com.example.sideworks.approval.dto.ApprovalDocumentTypeCreateRequest;
import com.example.sideworks.approval.dto.ApprovalDocumentTypeUpdateRequest;
import com.example.sideworks.approval.service.ApprovalDocumentTypeAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/approval-document-types")
public class ApprovalDocumentTypeAdminController {
    private final ApprovalDocumentTypeAdminService service;

    @GetMapping
    public List<ApprovalDocumentTypeAdminResponse> findAll() {
        return service.findAll();
    }

    @PostMapping
    public ResponseEntity<ApprovalDocumentTypeAdminResponse> create(@RequestBody ApprovalDocumentTypeCreateRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/admin/approval-document-types/" + response.approvalDocumentTypeId())).body(response);
    }

    @PutMapping("/{id}")
    public ApprovalDocumentTypeAdminResponse update(@PathVariable("id") Long id, @RequestBody ApprovalDocumentTypeUpdateRequest request) {
        return service.update(id, request);
    }
}
