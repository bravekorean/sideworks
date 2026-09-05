package com.example.sideworks.department.controller;

import com.example.sideworks.department.dto.DepartmentResponse;
import com.example.sideworks.department.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "부서 조회", description = "인증된 사용자가 이용하는 부서 정보 조회 API")
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @Operation(summary = "전체 부서 조회", description = "활성 상태인 전체 부서와 계층 관계 정보를 조회합니다.")
    public ResponseEntity<List<DepartmentResponse>> findAllDepartments() {
        List<DepartmentResponse> departments = departmentService.findAllDepartments();

        return ResponseEntity.ok(departments);
    }

    @GetMapping("/{departmentId}")
    @Operation(summary = "부서 상세 조회", description = "부서 식별번호로 특정 부서의 상세 정보를 조회합니다.")
    public ResponseEntity<DepartmentResponse> findDepartmentById(@PathVariable("departmentId") Long departmentId) {
        DepartmentResponse department = departmentService.findDepartmentById(departmentId);

        return ResponseEntity.ok(department);
    }
}
