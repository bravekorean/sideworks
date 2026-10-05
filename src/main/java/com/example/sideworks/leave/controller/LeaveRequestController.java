package com.example.sideworks.leave.controller;

import com.example.sideworks.leave.dto.LeaveRequestDocumentInput;
import com.example.sideworks.leave.dto.LeaveCancellationDocumentInput;
import com.example.sideworks.leave.dto.ApprovedLeaveResponse;
import com.example.sideworks.leave.service.LeaveCancellationService;
import com.example.sideworks.leave.service.LeaveRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/annual-leave/requests")
public class LeaveRequestController {
    private final LeaveRequestService service;
    private final LeaveCancellationService cancellations;

    @GetMapping("/approved")
    public List<ApprovedLeaveResponse> approved(Authentication authentication) {
        return cancellations.getCancelable(authentication.getName());
    }

    @PostMapping(value = "/cancellations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Long> cancel(Authentication authentication,
                                    @RequestPart("request") LeaveCancellationDocumentInput input,
                                    @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return Map.of("approvalId", cancellations.submit(authentication.getName(), input, files));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Long> submit(Authentication authentication,
                                    @RequestPart("request") LeaveRequestDocumentInput input,
                                    @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return Map.of("approvalId", service.submit(authentication.getName(), input, files));
    }
}
