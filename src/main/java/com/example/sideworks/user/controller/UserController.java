package com.example.sideworks.user.controller;

import com.example.sideworks.user.dto.MyProfileResponse;
import com.example.sideworks.user.dto.MyProfileUpdateRequest;
import com.example.sideworks.user.dto.AccountWithdrawalRequest;
import com.example.sideworks.user.dto.PasswordChangeRequest;
import com.example.sideworks.user.service.UserService;
import lombok.RequiredArgsConstructor;
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

    @PatchMapping("/mypage")
    public ResponseEntity<Void> updateMyProfile(
            Authentication authentication,
            @RequestBody MyProfileUpdateRequest request
    ) {
        userService.updateMyProfile(authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/mypage")
    public ResponseEntity<Void> withdrawMyAccount(
            Authentication authentication,
            @RequestBody AccountWithdrawalRequest request
    ) {
        userService.withdrawMyAccount(authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/mypage/password")
    public ResponseEntity<Void> changeMyPassword(
            Authentication authentication,
            @RequestBody PasswordChangeRequest request
    ) {
        userService.changeMyPassword(authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }
}
