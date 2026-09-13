package com.example.sideworks.organization.controller;

import com.example.sideworks.common.dto.PageResponse;
import com.example.sideworks.organization.dto.OrganizationDepartmentResponse;
import com.example.sideworks.organization.dto.OrganizationMemberResponse;
import com.example.sideworks.organization.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/organization")
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping("/departments")
    public ResponseEntity<List<OrganizationDepartmentResponse>> getDepartmentTree() {
        return ResponseEntity.ok(organizationService.getDepartmentTree());
    }

    @GetMapping("/departments/{departmentId}/members")
    public ResponseEntity<PageResponse<OrganizationMemberResponse>> getDepartmentMembers(
            @PathVariable("departmentId") Long departmentId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<OrganizationMemberResponse> members = organizationService.getDepartmentMembers(departmentId, pageable);

        return ResponseEntity.ok(PageResponse.from(members));
    }
}
