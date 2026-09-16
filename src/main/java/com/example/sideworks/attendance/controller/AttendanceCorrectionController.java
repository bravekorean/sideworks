package com.example.sideworks.attendance.controller;

import com.example.sideworks.attendance.service.AttendanceCorrectionService;
import com.example.sideworks.attendance.service.AttendanceCorrectionService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/attendances/corrections")
public class AttendanceCorrectionController {
    private final AttendanceCorrectionService service;
    @GetMapping("/approvers")
    public List<Approver> approvers(Authentication auth) { return service.approvers(auth.getName()); }
    @GetMapping("/record")
    public Snapshot record(Authentication auth, @RequestParam LocalDate date, @RequestParam(required = false) Long userId) {
        return service.snapshot(auth.getName(), userId, date);
    }
    @GetMapping("/history")
    public List<History> history(Authentication auth, @RequestParam LocalDate date, @RequestParam(required = false) Long userId) {
        return service.history(auth.getName(), userId, date);
    }
    @PostMapping
    public Map<String, Long> submit(Authentication auth, @RequestBody Input input) {
        return Map.of("approvalId", service.submit(auth.getName(), input));
    }
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Long> submitDocument(Authentication auth, @RequestPart("request") DocumentInput request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return Map.of("approvalId", service.submitDocument(auth.getName(), request, files));
    }
    @PutMapping("/direct/{userId}")
    public Snapshot direct(Authentication auth, @PathVariable Long userId, @RequestBody Input input) {
        return service.direct(auth.getName(), userId, input);
    }
}
