package com.example.sideworks.user.controller;

import com.example.sideworks.common.dto.PageResponse;
import com.example.sideworks.user.dto.*;
import com.example.sideworks.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/mypage")
    public ResponseEntity<MyProfileResponse> getMyProfile(Authentication authentication) {
        MyProfileResponse response =
                userService.getMyProfile(authentication.getName());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/directory")
    public ResponseEntity<PageResponse<UserDirectoryResponse>> findDirectoryUsers(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        Page<UserDirectoryResponse> users = userService.findDirectoryUsers(authentication.getName(), pageable);

        return ResponseEntity.ok(PageResponse.from(users));
    }


    @PatchMapping("/mypage")
    public ResponseEntity<Void> updateMyProfile(Authentication authentication, @RequestBody MyProfileUpdateRequest request) {
        userService.updateMyProfile(authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/mypage")
    public ResponseEntity<Void> withdrawMyAccount(Authentication authentication, @RequestBody AccountWithdrawalRequest request) {
        userService.withdrawMyAccount(authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/mypage/password")
    public ResponseEntity<Void> changeMyPassword(Authentication authentication, @RequestBody PasswordChangeRequest request) {
        userService.changeMyPassword(authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }
}
