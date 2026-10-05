package com.example.sideworks.approval.entity;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.department.entity.Department;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalTemplateEntitiesTest {

    @Test
    void 공용_템플릿은_부서없이_활성상태로_생성한다() {
        User creator = user(null);

        ApprovalTemplate template = ApprovalTemplate.create(
                " 공용 결재선 ", null, ApprovalTemplateScope.COMMON, null, creator);

        assertThat(template.getTemplateName()).isEqualTo("공용 결재선");
        assertThat(template.getDepartment()).isNull();
        assertThat(template.getStatus()).isEqualTo(ApprovalTemplateStatus.ACTIVE);
        assertThat(template.getValidationStatus()).isEqualTo(ApprovalTemplateValidationStatus.VALID);
        assertThat(template.getCreatedBy()).isSameAs(creator);
        assertThat(template.getUpdatedBy()).isSameAs(creator);
    }

    @Test
    void 부서_범위와_소유부서가_맞지_않으면_거절한다() {
        User creator = user(null);

        assertThatThrownBy(() -> ApprovalTemplate.create(
                "부서 결재선", null, ApprovalTemplateScope.DEPARTMENT, null, creator))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ApprovalTemplate.create(
                "공용 결재선", null, ApprovalTemplateScope.COMMON, department(10L), creator))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 이미_차단된_템플릿은_반복_차단하지_않는다() {
        ApprovalTemplate template = template();

        assertThat(template.block()).isTrue();
        assertThat(template.block()).isFalse();
        assertThat(template.getValidationStatus()).isEqualTo(ApprovalTemplateValidationStatus.BLOCKED);
    }

    @Test
    void 관리자_재검증_저장으로_차단을_해제하고_생성자는_보존한다() {
        ApprovalTemplate template = template();
        User creator = template.getCreatedBy();
        User editor = user(null);
        template.block();

        template.updateAfterValidation(" 수정 결재선 ", "설명", ApprovalTemplateScope.COMMON,
                null, ApprovalTemplateStatus.INACTIVE, editor);

        assertThat(template.getTemplateName()).isEqualTo("수정 결재선");
        assertThat(template.getValidationStatus()).isEqualTo(ApprovalTemplateValidationStatus.VALID);
        assertThat(template.getStatus()).isEqualTo(ApprovalTemplateStatus.INACTIVE);
        assertThat(template.getCreatedBy()).isSameAs(creator);
        assertThat(template.getUpdatedBy()).isSameAs(editor);
    }

    @Test
    void 결재단계는_생성당시_부서값을_보존한다() {
        ApprovalTemplate template = template();
        User approver = user(department(10L));

        ApprovalTemplateLine line = ApprovalTemplateLine.create(template, approver, 1);
        approver.assignDepartment(department(20L));

        assertThat(line.getApprovalTemplate()).isSameAs(template);
        assertThat(line.getApprover()).isSameAs(approver);
        assertThat(line.getApprovalStep()).isEqualTo(1);
        assertThat(line.getBaselineDepartmentId()).isEqualTo(10L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void 결재순서는_양수여야_한다(Integer step) {
        assertThatThrownBy(() -> ApprovalTemplateLine.create(template(), user(null), step))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 결재단계에는_템플릿과_결재자가_필요하다() {
        assertThatThrownBy(() -> ApprovalTemplateLine.create(null, user(null), 1))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ApprovalTemplateLine.create(template(), null, 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 참조자는_생성당시_부서값을_보존한다() {
        ApprovalTemplate template = template();
        User ccUser = user(department(10L));

        ApprovalTemplateCc cc = ApprovalTemplateCc.create(template, ccUser);
        ccUser.assignDepartment(department(20L));

        assertThat(cc.getApprovalTemplate()).isSameAs(template);
        assertThat(cc.getCcUser()).isSameAs(ccUser);
        assertThat(cc.getBaselineDepartmentId()).isEqualTo(10L);
    }

    @Test
    void 참조자에는_템플릿과_사용자가_필요하다() {
        assertThatThrownBy(() -> ApprovalTemplateCc.create(null, user(null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ApprovalTemplateCc.create(template(), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 미배정_부서는_NULL_기준값으로_보존한다() {
        User member = user(null);

        assertThat(ApprovalTemplateLine.create(template(), member, 1).getBaselineDepartmentId()).isNull();
        assertThat(ApprovalTemplateCc.create(template(), member).getBaselineDepartmentId()).isNull();
    }

    private static ApprovalTemplate template() {
        return ApprovalTemplate.create("공용 결재선", null, ApprovalTemplateScope.COMMON, null, user(null));
    }

    private static User user(Department department) {
        return User.create("member", null, "구성원", null, null, "EMP-TEST", null, null,
                department, null, UserRole.USER, UserStatus.ACTIVE);
    }

    private static Department department(Long id) {
        Department department = Department.create("부서", null);
        ReflectionTestUtils.setField(department, "departmentId", id);
        return department;
    }
}
