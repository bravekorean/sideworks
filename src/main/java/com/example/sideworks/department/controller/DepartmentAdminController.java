package com.example.sideworks.department.controller;

import com.example.sideworks.department.dto.DepartmentCreateRequest;
import com.example.sideworks.department.dto.DepartmentCreateResponse;
import com.example.sideworks.department.dto.DepartmentUpdateRequest;
import com.example.sideworks.department.dto.DepartmentManagerUpdateRequest;
import com.example.sideworks.department.service.DepartmentAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/departments")
public class DepartmentAdminController {

    private final DepartmentAdminService departmentAdminService;

    @PostMapping
    public ResponseEntity<DepartmentCreateResponse> createDepartment(@RequestBody DepartmentCreateRequest request) {
        Long departmentId = departmentAdminService.createDepartment(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(new DepartmentCreateResponse(departmentId));
    }

    @PutMapping("/{departmentId}")
    public ResponseEntity<Void> updateDepartment(@PathVariable("departmentId") Long departmentId, @RequestBody DepartmentUpdateRequest request) {
        departmentAdminService.updateDepartment(departmentId, request);

        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{departmentId}/manager")
    public ResponseEntity<Void> updateDepartmentManager(@PathVariable("departmentId") Long departmentId, @RequestBody DepartmentManagerUpdateRequest request) {
        departmentAdminService.updateDepartmentManager(departmentId, request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{departmentId}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable("departmentId") Long departmentId) {
        departmentAdminService.deleteDepartment(departmentId);

        return ResponseEntity.noContent().build();
    }
}