package com.example.sideworks.approval.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ApprovalSubmitRequest {

    private List<Long> approverIds;

    private List<Long> ccUserIds;

    // 수동 결재선이면 둘 다 null이다. 템플릿은 최종 결재자 목록의 출발점일 뿐이다.
    private Long templateId;

    private Long templateVersion;
}
