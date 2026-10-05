package com.example.sideworks.approval.entity;

import com.example.sideworks.common.entity.BaseTimeEntity;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.department.entity.Department;
import com.example.sideworks.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "approval_templatetbl")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_template_id")
    private Long approvalTemplateId;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private ApprovalTemplateScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalTemplateStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 20)
    private ApprovalTemplateValidationStatus validationStatus;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by", nullable = false)
    private User updatedBy;

    public static ApprovalTemplate create(String templateName, String description, ApprovalTemplateScope scope, Department department, User creator) {
        validateDetails(templateName, description, scope, department);

        if (creator == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        ApprovalTemplate template = new ApprovalTemplate();

        template.templateName = templateName.trim();
        template.description = description;
        template.scope = scope;
        template.department = department;
        template.status = ApprovalTemplateStatus.ACTIVE;
        template.validationStatus = ApprovalTemplateValidationStatus.VALID;
        template.createdBy = creator;
        template.updatedBy = creator;

        return template;
    }

    // 서비스에서 구성원 전체를 검증한 뒤 호출한다.
    public void updateAfterValidation(String templateName, String description, ApprovalTemplateScope scope, Department department, ApprovalTemplateStatus status, User editor) {
        validateDetails(templateName, description, scope, department);

        if (status == null || editor == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        this.templateName = templateName.trim();
        this.description = description;
        this.scope = scope;
        this.department = department;
        this.status = status;
        this.validationStatus = ApprovalTemplateValidationStatus.VALID;
        this.updatedBy = editor;
    }

    // 새 차단 전환인지 반환하여 반복 알림을 방지한다.
    public boolean block() {
        if (validationStatus == ApprovalTemplateValidationStatus.BLOCKED) {
            return false;
        }

        validationStatus = ApprovalTemplateValidationStatus.BLOCKED;
        return true;
    }

    private static void validateDetails(String templateName, String description, ApprovalTemplateScope scope, Department department) {
        if (templateName == null || templateName.isBlank() || templateName.trim().length() > 100 || (description != null && description.length() > 500) || scope == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (scope == ApprovalTemplateScope.DEPARTMENT
                && department == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (scope == ApprovalTemplateScope.COMMON
                && department != null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}