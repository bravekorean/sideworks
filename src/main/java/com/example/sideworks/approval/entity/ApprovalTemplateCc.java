package com.example.sideworks.approval.entity;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.department.entity.Department;
import com.example.sideworks.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "approval_template_cctbl", uniqueConstraints = {
        @UniqueConstraint(name = "uk_template_cc_user",
                columnNames = {"approval_template_id", "user_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalTemplateCc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_template_cc_id")
    private Long approvalTemplateCcId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_template_id", nullable = false)
    private ApprovalTemplate approvalTemplate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User ccUser;

    // 사용자 인사정보가 바뀌어도 원본 비교 기준은 자동으로 바뀌지 않는다.
    @Column(name = "baseline_department_id")
    private Long baselineDepartmentId;

    public static ApprovalTemplateCc create(ApprovalTemplate template, User ccUser) {
        if (template == null || ccUser == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        ApprovalTemplateCc cc = new ApprovalTemplateCc();
        cc.approvalTemplate = template;
        cc.ccUser = ccUser;
        Department department = ccUser.getDepartment();
        cc.baselineDepartmentId = department == null ? null : department.getDepartmentId();
        return cc;
    }
}
