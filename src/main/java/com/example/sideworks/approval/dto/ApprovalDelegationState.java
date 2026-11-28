package com.example.sideworks.approval.dto;

/**
 * 결재 단계가 위임과 어떤 관계인지 화면에 알려준다. 위임과 무관하면 null이다.
 */
public enum ApprovalDelegationState {
    /** 대결자가 원 결재자 대신 처리한다(처리했다). */
    DELEGATED,
    /** 위임 기간 중이지만 대결자가 이 문서의 다른 참여자라서 원 결재자가 처리한다. */
    DELEGATION_BLOCKED
}
