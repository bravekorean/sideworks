package com.example.sideworks.approval.entity;

import com.example.sideworks.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;

@Entity
@Table(name = "approval_document_typetbl")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalDocumentType extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_document_type_id")
    private Long approvalDocumentTypeId;

    @Column(name = "type_code", nullable = false, length = 30, updatable = false)
    private String typeCode;

    @Column(name = "type_name", nullable = false, length = 100)
    private String typeName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "content_template", columnDefinition = "LONGTEXT")
    private String contentTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "behavior_type", nullable = false, length = 30)
    private DocumentBehaviorType behaviorType;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;


    public static ApprovalDocumentType createGeneral(String typeCode, String typeName, String description, String contentTemplate, int sortOrder) {
        ApprovalDocumentType documentType = new ApprovalDocumentType();

        documentType.typeCode = typeCode;
        documentType.typeName = typeName;
        documentType.description = description;
        documentType.contentTemplate = contentTemplate;
        documentType.sortOrder = sortOrder;

        documentType.behaviorType = DocumentBehaviorType.GENERAL;
        documentType.system = false;
        documentType.active = true;

        return documentType;
    }

    public void updateGeneral(String typeName, String description, String contentTemplate, int sortOrder, boolean active) {
        validateGeneralType();

        this.typeName = typeName;
        this.description = description;
        this.contentTemplate = contentTemplate;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    private void validateGeneralType() {
        if (system || behaviorType != DocumentBehaviorType.GENERAL) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
