package com.example.sideworks.position.service;

import com.example.sideworks.position.entity.Position;
import com.example.sideworks.position.dto.PositionResponse;
import com.example.sideworks.position.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PositionService {

    private final PositionRepository positionRepository;

    public List<PositionResponse> findAllPositions() {
        return positionRepository.findAllByOrderByPositionOrderAsc().stream().map(PositionResponse::from).toList();
    }

    public PositionResponse findPositionById(Long positionId) {
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POSITION_NOT_FOUND));

        return PositionResponse.from(position);
    }
}