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
@Table(name = "approval_template_linetbl", uniqueConstraints = {
        @UniqueConstraint(name = "uk_template_line_step",
                columnNames = {"approval_template_id", "approval_step"}),
        @UniqueConstraint(name = "uk_template_line_approver",
                columnNames = {"approval_template_id", "approver_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalTemplateLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_template_line_id")
    private Long approvalTemplateLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approval_template_id", nullable = false)
    private ApprovalTemplate approvalTemplate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_id", nullable = false)
    private User approver;

    @Column(name = "approval_step", nullable = false)
    private Integer approvalStep;

    // 현재 부서 연관관계가 아니라 관리자 저장 당시의 비교 기준이다.
    @Column(name = "baseline_department_id")
    private Long baselineDepartmentId;

    public static ApprovalTemplateLine create(ApprovalTemplate template, User approver, Integer step) {
        if (template == null || approver == null || step == null || step <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        ApprovalTemplateLine line = new ApprovalTemplateLine();
        line.approvalTemplate = template;
        line.approver = approver;
        line.approvalStep = step;
        Department department = approver.getDepartment();
        line.baselineDepartmentId = department == null ? null : department.getDepartmentId();
        return line;
    }
}
