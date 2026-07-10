package com.example.sideworks.position.controller;

import com.example.sideworks.position.dto.PositionResponse;
import com.example.sideworks.position.service.PositionService;
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
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    public ResponseEntity<List<PositionResponse>> findAllPositions() {
        List<PositionResponse> positions = positionService.findAllPositions();

        return ResponseEntity.ok(positions);
    }

    @GetMapping("/{positionId}")
    public ResponseEntity<PositionResponse> findPositionById(@PathVariable("positionId") Long positionId) {
        PositionResponse position = positionService.findPositionById(positionId);

        return ResponseEntity.ok(position);
    }
}