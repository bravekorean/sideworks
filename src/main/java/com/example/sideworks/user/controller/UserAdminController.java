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
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    public ResponseEntity<PageResponse<UserSummaryResponse>> findAllUsers(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<UserSummaryResponse> users = userAdminService.findAllUsers(pageable);

        return ResponseEntity.ok(PageResponse.from(users));
    }

    @PostMapping
    public ResponseEntity<UserCreateResponse> createUser(@RequestBody UserCreateRequest request) {
        Long userId = userAdminService.createUser(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(new UserCreateResponse(userId));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDetailResponse> findUser(@PathVariable Long userId) {
        UserDetailResponse user = userAdminService.findUser(userId);

        return ResponseEntity.ok(user);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<Void> updateUser(@PathVariable Long userId, @RequestBody UserUpdateRequest request) {
        userAdminService.updateUser(userId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unassigned")
    public ResponseEntity<PageResponse<UserSummaryResponse>> findUnassignedUsers(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<UserSummaryResponse> users = userAdminService.findUnassignedUsers(pageable);

        return ResponseEntity.ok(PageResponse.from(users));
    }

    @PatchMapping("/{userId}/assignment")
    public ResponseEntity<Void> assignUser(@PathVariable Long userId,  @RequestBody UserAssignmentRequest request) {
        userAdminService.assignUser(userId, request);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<Void> changeUserStatus(@PathVariable Long userId, @RequestBody UserStatusUpdateRequest request) {
        userAdminService.changeUserStatus(userId, request);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/role")
    public ResponseEntity<Void> changeUserRole(@PathVariable Long userId, @RequestBody UserRoleUpdateRequest request) {
        userAdminService.changeUserRole(userId, request);

        return ResponseEntity.noContent().build();
    }
}
