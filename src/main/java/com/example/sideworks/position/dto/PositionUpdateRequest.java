package com.example.sideworks.position.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PositionUpdateRequest {

    private String positionName;

    private Integer positionOrder;
}