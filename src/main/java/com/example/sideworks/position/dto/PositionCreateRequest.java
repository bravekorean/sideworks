package com.example.sideworks.position.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PositionCreateRequest {

    private String positionName;

    private Integer positionOrder;
}