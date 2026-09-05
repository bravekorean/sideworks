package com.example.sideworks.position.controller;

import com.example.sideworks.position.dto.PositionResponse;
import com.example.sideworks.position.service.PositionService;
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
@RequestMapping("/api/positions")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "직급 조회", description = "인증된 사용자가 이용하는 직급 정보 조회 API")
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    @Operation(summary = "전체 직급 조회", description = "등록된 직급을 정렬 순서와 함께 조회합니다.")
    public ResponseEntity<List<PositionResponse>> findAllPositions() {
        List<PositionResponse> positions = positionService.findAllPositions();

        return ResponseEntity.ok(positions);
    }

    @GetMapping("/{positionId}")
    @Operation(summary = "직급 상세 조회", description = "직급 식별번호로 특정 직급의 상세 정보를 조회합니다.")
    public ResponseEntity<PositionResponse> findPositionById(@PathVariable("positionId") Long positionId) {
        PositionResponse position = positionService.findPositionById(positionId);

        return ResponseEntity.ok(position);
    }
}
