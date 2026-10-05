package com.example.sideworks.approval.entity;

import org.springframework.test.util.ReflectionTestUtils;

public final class ApprovalDocumentTypeFixtures {
    private ApprovalDocumentTypeFixtures() {}

    public static ApprovalDocumentType general() {
        return type(1L, "GENERAL_PROPOSAL", DocumentBehaviorType.GENERAL, true);
    }

    public static ApprovalDocumentType type(Long id, String code, DocumentBehaviorType behavior, boolean active) {
        ApprovalDocumentType type = new ApprovalDocumentType();
        ReflectionTestUtils.setField(type, "approvalDocumentTypeId", id);
        ReflectionTestUtils.setField(type, "typeCode", code);
        ReflectionTestUtils.setField(type, "typeName", code);
        ReflectionTestUtils.setField(type, "behaviorType", behavior);
        ReflectionTestUtils.setField(type, "active", active);
        ReflectionTestUtils.setField(type, "system", behavior != DocumentBehaviorType.GENERAL);
        return type;
    }
}
