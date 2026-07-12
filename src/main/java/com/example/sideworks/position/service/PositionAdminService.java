package com.example.sideworks.position.service;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.position.dto.PositionCreateRequest;
import com.example.sideworks.position.dto.PositionResponse;
import com.example.sideworks.position.dto.PositionUpdateRequest;
import com.example.sideworks.position.entity.Position;
import com.example.sideworks.position.repository.PositionRepository;
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PositionAdminService {

    private final PositionRepository positionRepository;
    private final UserRepository userRepository;

    public Page<PositionResponse> findAllPositions(Pageable pageable) {
        return positionRepository
                .findAllByOrderByPositionOrderAscPositionIdAsc(pageable)
                .map(PositionResponse::from);
    }

    @Transactional
    public Long createPosition(PositionCreateRequest request) {
        String positionName = validatePositionName(request.getPositionName());

        Integer positionOrder = validatePositionOrder(request.getPositionOrder());

        if (positionRepository.existsByPositionName(positionName)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (positionRepository.existsByPositionOrder(positionOrder)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Position position = Position.create(positionName, positionOrder);

        return positionRepository.save(position).getPositionId();
    }

    @Transactional
    public void updatePosition(Long positionId, PositionUpdateRequest request) {
        Position position = findPosition(positionId);

        String positionName = validatePositionName(request.getPositionName());

        Integer positionOrder = validatePositionOrder(request.getPositionOrder());

        if (!position.getPositionName().equals(positionName) && positionRepository.existsByPositionName(positionName)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (!position.getPositionOrder().equals(positionOrder) && positionRepository.existsByPositionOrder(positionOrder)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        position.update(positionName, positionOrder);
    }

    @Transactional
    public void deletePosition(Long positionId) {
        Position position = findPosition(positionId);

        if (userRepository.existsByPosition_PositionId(positionId)) {
            throw new BusinessException(ErrorCode.POSITION_IN_USE);
        }

        positionRepository.delete(position);
    }

    private String validatePositionName(String positionName) {
        if (positionName == null || positionName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        String trimmedName = positionName.trim();

        if (trimmedName.length() > 50) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        return trimmedName;
    }

    private Integer validatePositionOrder(Integer positionOrder) {
        if (positionOrder == null || positionOrder < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        return positionOrder;
    }

    private Position findPosition(Long positionId) {
        return positionRepository.findById(positionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POSITION_NOT_FOUND));
    }


}
