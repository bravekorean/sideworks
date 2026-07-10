package com.example.sideworks.department.controller;

import com.example.sideworks.department.dto.DepartmentResponse;
import com.example.sideworks.department.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public ResponseEntity<List<DepartmentResponse>> findAllDepartments() {
        List<DepartmentResponse> departments = departmentService.findAllDepartments();

        return ResponseEntity.ok(departments);
    }

    @GetMapping("/{departmentId}")
    public ResponseEntity<DepartmentResponse> findDepartmentById(@PathVariable("departmentId") Long departmentId) {
        DepartmentResponse department = departmentService.findDepartmentById(departmentId);

        return ResponseEntity.ok(department);
    }
}
