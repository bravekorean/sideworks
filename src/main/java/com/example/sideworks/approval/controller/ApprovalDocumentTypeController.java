package com.example.sideworks.approval.controller;

import com.example.sideworks.approval.dto.ApprovalDocumentTypeResponse;
import com.example.sideworks.approval.service.ApprovalDocumentTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/approval-document-types")
public class ApprovalDocumentTypeController {

    private final ApprovalDocumentTypeService service;

    @GetMapping
    public ResponseEntity<List<ApprovalDocumentTypeResponse>> getActiveDocumentTypes() {
        return ResponseEntity.ok(service.getActiveDocumentTypes());
    }
}