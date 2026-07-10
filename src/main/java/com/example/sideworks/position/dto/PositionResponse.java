package com.example.sideworks.position.dto;

import com.example.sideworks.position.entity.Position;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PositionResponse {

    private Long positionId;

    private String positionName;

    private Integer positionOrder;

    public static PositionResponse from(Position position) {
        return new PositionResponse(position.getPositionId(), position.getPositionName(), position.getPositionOrder());
    }
}