package com.example.sideworks.user.controller;

import com.example.sideworks.common.dto.PageResponse;
import com.example.sideworks.user.dto.*;
import com.example.sideworks.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "사용자", description = "마이페이지, 비밀번호 및 조직 구성원 조회 API")
public class UserController {

    private final UserService userService;

    @GetMapping("/mypage")
    @Operation(summary = "내 정보 조회", description = "현재 인증된 사용자의 계정, 소속 부서 및 직급 정보를 조회합니다.")
    public ResponseEntity<MyProfileResponse> getMyProfile(Authentication authentication) {
        MyProfileResponse response =
                userService.getMyProfile(authentication.getName());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/directory")
    @Operation(summary = "조직 구성원 조회", description = "결재선과 참조자 지정 등에 사용할 활성 조직 구성원을 페이지 단위로 조회합니다.")
    public ResponseEntity<PageResponse<UserDirectoryResponse>> findDirectoryUsers(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        Page<UserDirectoryResponse> users = userService.findDirectoryUsers(authentication.getName(), pageable);

        return ResponseEntity.ok(PageResponse.from(users));
    }


    @PatchMapping("/mypage")
    @Operation(summary = "내 정보 수정", description = "현재 사용자가 직접 변경할 수 있는 연락처 등의 프로필 정보를 수정합니다.")
    public ResponseEntity<Void> updateMyProfile(Authentication authentication, @RequestBody MyProfileUpdateRequest request) {
        userService.updateMyProfile(authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/mypage")
    @Operation(summary = "회원 탈퇴", description = "현재 비밀번호를 재확인한 후 계정 상태를 삭제 상태로 변경합니다.")
    public ResponseEntity<Void> withdrawMyAccount(Authentication authentication, @RequestBody AccountWithdrawalRequest request) {
        userService.withdrawMyAccount(authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/mypage/password")
    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 검증한 뒤 새로운 비밀번호를 암호화하여 저장합니다.")
    public ResponseEntity<Void> changeMyPassword(Authentication authentication, @RequestBody PasswordChangeRequest request) {
        userService.changeMyPassword(authentication.getName(), request);

        return ResponseEntity.noContent().build();
    }
}
