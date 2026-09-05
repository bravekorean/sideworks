package com.example.sideworks.position.controller;

import com.example.sideworks.common.dto.PageResponse;
import com.example.sideworks.position.dto.PositionCreateRequest;
import com.example.sideworks.position.dto.PositionUpdateRequest;
import com.example.sideworks.position.dto.PositionCreateResponse;
import com.example.sideworks.position.dto.PositionResponse;
import com.example.sideworks.position.service.PositionAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/positions")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "관리자 직급 관리", description = "ADMIN 및 SUPER_ADMIN용 직급 생성, 수정, 삭제 API")
public class PositionAdminController {

    private final PositionAdminService positionAdminService;

    @GetMapping
    @Operation(summary = "관리자용 직급 목록 조회", description = "관리 화면에서 사용할 직급 목록을 페이지 단위로 조회합니다.")
    public ResponseEntity<PageResponse<PositionResponse>> findAllPositions(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<PositionResponse> positions = positionAdminService.findAllPositions(pageable);

        return ResponseEntity.ok(PageResponse.from(positions));
    }

    @PostMapping
    @Operation(summary = "직급 생성", description = "직급명과 정렬 순서를 지정하여 새로운 직급을 생성합니다.")
    public ResponseEntity<PositionCreateResponse> createPosition(@RequestBody PositionCreateRequest request) {
        Long positionId = positionAdminService.createPosition(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(new PositionCreateResponse(positionId));
    }

    @PutMapping("/{positionId}")
    @Operation(summary = "직급 수정", description = "기존 직급의 이름과 정렬 순서를 수정합니다.")
    public ResponseEntity<Void> updatePosition(@PathVariable("positionId") Long positionId, @RequestBody PositionUpdateRequest request) {
        positionAdminService.updatePosition(positionId, request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{positionId}")
    @Operation(summary = "직급 삭제", description = "다른 사용자 배정 상태 등 삭제 가능 여부를 검증한 뒤 직급을 삭제합니다.")
    public ResponseEntity<Void> deletePosition(@PathVariable("positionId") Long positionId) {
        positionAdminService.deletePosition(positionId);

        return ResponseEntity.noContent().build();
    }
}
