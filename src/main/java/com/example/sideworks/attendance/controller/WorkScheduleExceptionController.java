package com.example.sideworks.attendance.controller;

import com.example.sideworks.attendance.service.WorkScheduleExceptionService;
import com.example.sideworks.attendance.service.WorkScheduleExceptionService.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/work-schedule-exceptions")
public class WorkScheduleExceptionController {
    private final WorkScheduleExceptionService service;
    @GetMapping public List<Entry> list(Authentication auth, @RequestParam int year) { return service.list(auth.getName(), year); }
    @PostMapping public Entry create(Authentication auth, @RequestBody Input input) { return service.create(auth.getName(), input); }
    @PutMapping("/{id}") public Entry update(Authentication auth, @PathVariable Long id, @RequestBody Input input) {
        return service.update(auth.getName(), id, input);
    }
}
