package com.example.sideworks.approval.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApprovalDraftRequest {

    private String title;
    private String content;
}
