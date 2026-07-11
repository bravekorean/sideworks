package com.example.sideworks.approval.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ApprovalSubmitRequest {

    private List<Long> approverIds;

    private List<Long> ccUserIds;
}