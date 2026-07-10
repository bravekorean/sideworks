package com.example.sideworks.position.controller;

import com.example.sideworks.position.dto.PositionCreateRequest;
import com.example.sideworks.position.dto.PositionUpdateRequest;
import com.example.sideworks.position.dto.PositionCreateResponse;
import com.example.sideworks.position.service.PositionAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/positions")
public class PositionAdminController {

    private final PositionAdminService positionAdminService;

    @PostMapping
    public ResponseEntity<PositionCreateResponse> createPosition(@RequestBody PositionCreateRequest request) {
        Long positionId = positionAdminService.createPosition(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(new PositionCreateResponse(positionId));
    }

    @PutMapping("/{positionId}")
    public ResponseEntity<Void> updatePosition(@PathVariable("positionId") Long positionId, @RequestBody PositionUpdateRequest request) {
        positionAdminService.updatePosition(positionId, request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{positionId}")
    public ResponseEntity<Void> deletePosition(@PathVariable("positionId") Long positionId) {
        positionAdminService.deletePosition(positionId);

        return ResponseEntity.noContent().build();
    }
}