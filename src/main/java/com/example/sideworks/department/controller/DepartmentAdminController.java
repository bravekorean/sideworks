package com.example.sideworks.department.controller;

import com.example.sideworks.common.dto.PageResponse;
import com.example.sideworks.department.dto.DepartmentCreateRequest;
import com.example.sideworks.department.dto.DepartmentCreateResponse;
import com.example.sideworks.department.dto.DepartmentUpdateRequest;
import com.example.sideworks.department.dto.DepartmentManagerUpdateRequest;
import com.example.sideworks.department.dto.DepartmentResponse;
import com.example.sideworks.department.service.DepartmentAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/departments")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "관리자 부서 관리", description = "ADMIN 및 SUPER_ADMIN용 부서 생성, 수정, 삭제 API")
public class DepartmentAdminController {

    private final DepartmentAdminService departmentAdminService;

    @GetMapping
    @Operation(summary = "관리자용 부서 목록 조회", description = "관리 화면에서 사용할 부서 목록을 페이지 단위로 조회합니다.")
    public ResponseEntity<PageResponse<DepartmentResponse>> findAllDepartments(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<DepartmentResponse> departments = departmentAdminService.findAllDepartments(pageable);

        return ResponseEntity.ok(PageResponse.from(departments));
    }

    @PostMapping
    @Operation(summary = "부서 생성", description = "부서명과 선택적인 상위 부서를 지정하여 새로운 부서를 생성합니다.")
    public ResponseEntity<DepartmentCreateResponse> createDepartment(@RequestBody DepartmentCreateRequest request) {
        Long departmentId = departmentAdminService.createDepartment(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(new DepartmentCreateResponse(departmentId));
    }

    @PutMapping("/{departmentId}")
    @Operation(summary = "부서 수정", description = "부서명과 상위 부서 정보를 수정합니다.")
    public ResponseEntity<Void> updateDepartment(@PathVariable("departmentId") Long departmentId, @RequestBody DepartmentUpdateRequest request) {
        departmentAdminService.updateDepartment(departmentId, request);

        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{departmentId}/manager")
    @Operation(summary = "부서장 지정", description = "특정 부서의 부서장을 지정하거나 변경합니다.")
    public ResponseEntity<Void> updateDepartmentManager(@PathVariable("departmentId") Long departmentId, @RequestBody DepartmentManagerUpdateRequest request) {
        departmentAdminService.updateDepartmentManager(departmentId, request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{departmentId}")
    @Operation(summary = "부서 삭제", description = "부서를 물리적으로 제거하지 않고 삭제 정책에 따라 비활성 상태로 변경합니다.")
    public ResponseEntity<Void> deleteDepartment(@PathVariable("departmentId") Long departmentId) {
        departmentAdminService.deleteDepartment(departmentId);

        return ResponseEntity.noContent().build();
    }
}
