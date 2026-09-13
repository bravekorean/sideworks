package com.example.sideworks.approval.attachment.controller;

import com.example.sideworks.approval.attachment.dto.ApprovalAttachmentResponse;
import com.example.sideworks.approval.attachment.service.ApprovalAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/approvals/{approvalId}/attachments")
public class ApprovalAttachmentController {

    private final ApprovalAttachmentService attachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ApprovalAttachmentResponse>> upload(
            @PathVariable("approvalId") Long approvalId,
            Authentication authentication,
            @RequestParam("files") List<MultipartFile> files
    ) {
        List<ApprovalAttachmentResponse> attachments = attachmentService.upload(
                approvalId,
                authentication.getName(),
                files
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(attachments);
    }

    @GetMapping
    public ResponseEntity<List<ApprovalAttachmentResponse>> findAll(
            @PathVariable("approvalId") Long approvalId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(attachmentService.findAll(approvalId, authentication.getName()));
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable("approvalId") Long approvalId,
            @PathVariable("attachmentId") Long attachmentId,
            Authentication authentication
    ) {
        ApprovalAttachmentService.DownloadFile file = attachmentService.download(
                approvalId,
                attachmentId,
                authentication.getName()
        );
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.originalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.resource());
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(
            @PathVariable("approvalId") Long approvalId,
            @PathVariable("attachmentId") Long attachmentId,
            Authentication authentication
    ) {
        attachmentService.delete(approvalId, attachmentId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
