package com.example.sideworks.user.controller;

import com.example.sideworks.common.dto.PageResponse;
import com.example.sideworks.user.dto.UserAssignmentRequest;
import com.example.sideworks.user.dto.UserCreateRequest;
import com.example.sideworks.user.dto.UserCreateResponse;
import com.example.sideworks.user.dto.UserDetailResponse;
import com.example.sideworks.user.dto.UserRoleUpdateRequest;
import com.example.sideworks.user.dto.UserStatusUpdateRequest;
import com.example.sideworks.user.dto.UserSummaryResponse;
import com.example.sideworks.user.dto.UserUpdateRequest;
import com.example.sideworks.user.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "관리자 사용자 관리", description = "ADMIN 및 SUPER_ADMIN용 사용자 생성, 조회 및 인사정보 관리 API")
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    @Operation(summary = "사용자 목록 조회", description = "전체 사용자 목록을 관리 화면에서 페이지 단위로 조회합니다.")
    public ResponseEntity<PageResponse<UserSummaryResponse>> findAllUsers(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<UserSummaryResponse> users = userAdminService.findAllUsers(pageable);

        return ResponseEntity.ok(PageResponse.from(users));
    }

    @PostMapping
    @Operation(summary = "사용자 생성", description = "사용자 계정을 생성하고 직렬과 입사일을 기준으로 사번을 자동 발급합니다.")
    public ResponseEntity<UserCreateResponse> createUser(@RequestBody UserCreateRequest request) {
        UserCreateResponse response = userAdminService.createUser(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "사용자 상세 조회", description = "사용자 식별번호로 계정과 인사 배정 정보를 조회합니다.")
    public ResponseEntity<UserDetailResponse> findUser(@PathVariable Long userId) {
        UserDetailResponse user = userAdminService.findUser(userId);

        return ResponseEntity.ok(user);
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "사용자 정보 수정", description = "관리자가 사용자의 수정 가능한 기본 정보를 변경합니다.")
    public ResponseEntity<Void> updateUser(@PathVariable Long userId, @RequestBody UserUpdateRequest request) {
        userAdminService.updateUser(userId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unassigned")
    @Operation(summary = "미배정 사용자 조회", description = "부서 또는 직급이 아직 배정되지 않은 사용자를 페이지 단위로 조회합니다.")
    public ResponseEntity<PageResponse<UserSummaryResponse>> findUnassignedUsers(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<UserSummaryResponse> users = userAdminService.findUnassignedUsers(pageable);

        return ResponseEntity.ok(PageResponse.from(users));
    }

    @PatchMapping("/{userId}/assignment")
    @Operation(summary = "부서·직급 배정", description = "사용자에게 부서와 직급을 배정하거나 기존 배정 정보를 변경합니다.")
    public ResponseEntity<Void> assignUser(@PathVariable Long userId,  @RequestBody UserAssignmentRequest request) {
        userAdminService.assignUser(userId, request);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/status")
    @Operation(summary = "사용자 상태 변경", description = "사용자의 활성, 비활성 또는 삭제 상태를 변경합니다.")
    public ResponseEntity<Void> changeUserStatus(@PathVariable Long userId, @RequestBody UserStatusUpdateRequest request) {
        userAdminService.changeUserStatus(userId, request);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/role")
    @Operation(summary = "사용자 역할 변경", description = "사용자에게 USER, ADMIN 등의 시스템 역할을 부여합니다.")
    public ResponseEntity<Void> changeUserRole(@PathVariable Long userId, @RequestBody UserRoleUpdateRequest request) {
        userAdminService.changeUserRole(userId, request);

        return ResponseEntity.noContent().build();
    }
}
