package com.example.sideworks.approval.service;

import com.example.sideworks.approval.dto.ApprovalTemplateSaveRequest;
import com.example.sideworks.approval.entity.*;
import com.example.sideworks.approval.repository.ApprovalTemplateCcRepository;
import com.example.sideworks.approval.repository.ApprovalTemplateLineRepository;
import com.example.sideworks.approval.repository.ApprovalTemplateRepository;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.department.repository.DepartmentRepository;
import com.example.sideworks.department.entity.Department;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalTemplateServiceTest {
    @Mock ApprovalTemplateRepository templates;
    @Mock ApprovalTemplateLineRepository lines;
    @Mock ApprovalTemplateCcRepository ccs;
    @Mock UserRepository users;
    @Mock DepartmentRepository departments;
    @Mock ApprovalTemplateBlockService blocker;
    @Mock EntityManager entityManager;
    ApprovalTemplateService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalTemplateService(templates, lines, ccs, users, departments, blocker, entityManager);
    }

    @Test
    void 일반_사용자는_공용_템플릿을_생성할_수_없다() {
        User actor = mock(User.class);
        when(actor.getUserRole()).thenReturn(UserRole.USER);
        when(users.findByLoginId("member")).thenReturn(Optional.of(actor));
        var request = new ApprovalTemplateSaveRequest("공용", null, ApprovalTemplateScope.COMMON,
                null, ApprovalTemplateStatus.ACTIVE, null, List.of(2L), List.of());

        assertThatThrownBy(() -> service.create("member", request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TEMPLATE_FORBIDDEN));
        verifyNoInteractions(templates);
    }

    @Test
    void 같은_결재자를_여러_단계에_넣을_수_없다() {
        User actor = mock(User.class);
        when(actor.getUserRole()).thenReturn(UserRole.SUPER_ADMIN);
        when(users.findByLoginId("admin")).thenReturn(Optional.of(actor));
        var request = new ApprovalTemplateSaveRequest("중복", null, ApprovalTemplateScope.COMMON,
                null, ApprovalTemplateStatus.ACTIVE, null, List.of(2L, 2L), List.of());

        assertThatThrownBy(() -> service.create("admin", request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TEMPLATE_INVALID_MEMBERS));
        verifyNoInteractions(templates);
    }

    @Test
    void 작성자는_템플릿_버전이_바뀌면_상신할_수_없다() {
        User author = mock(User.class);
        User approver = mock(User.class);
        ApprovalTemplate template = mock(ApprovalTemplate.class);
        ApprovalTemplateLine line = mock(ApprovalTemplateLine.class);
        when(users.findByLoginId("author")).thenReturn(Optional.of(author));
        when(templates.findWithDepartment(7L)).thenReturn(Optional.of(template));
        when(template.getScope()).thenReturn(ApprovalTemplateScope.COMMON);
        when(template.getStatus()).thenReturn(ApprovalTemplateStatus.ACTIVE);
        when(template.getValidationStatus()).thenReturn(ApprovalTemplateValidationStatus.VALID);
        when(template.getVersion()).thenReturn(2L);
        when(lines.findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalStepAsc(7L)).thenReturn(List.of(line));
        when(ccs.findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalTemplateCcIdAsc(7L)).thenReturn(List.of());
        when(line.getApprover()).thenReturn(approver);
        when(line.getBaselineDepartmentId()).thenReturn(null);
        when(approver.getStatus()).thenReturn(UserStatus.ACTIVE);

        assertThatThrownBy(() -> service.validateSubmission("author", 7L, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TEMPLATE_STALE));
    }

    @Test
    void 구성원_부서가_변경되면_템플릿을_차단하고_적용을_거절한다() {
        User author = mock(User.class);
        User approver = mock(User.class);
        Department changedDepartment = mock(Department.class);
        ApprovalTemplate template = mock(ApprovalTemplate.class);
        ApprovalTemplateLine line = mock(ApprovalTemplateLine.class);
        when(users.findByLoginId("author")).thenReturn(Optional.of(author));
        when(templates.findWithDepartment(7L)).thenReturn(Optional.of(template));
        when(template.getScope()).thenReturn(ApprovalTemplateScope.COMMON);
        when(template.getStatus()).thenReturn(ApprovalTemplateStatus.ACTIVE);
        when(lines.findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalStepAsc(7L)).thenReturn(List.of(line));
        when(ccs.findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalTemplateCcIdAsc(7L)).thenReturn(List.of());
        when(line.getApprover()).thenReturn(approver);
        when(line.getBaselineDepartmentId()).thenReturn(4L);
        when(approver.getDepartment()).thenReturn(changedDepartment);
        when(changedDepartment.getDepartmentId()).thenReturn(5L);

        assertThatThrownBy(() -> service.resolve("author", 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TEMPLATE_BLOCKED));
        verify(blocker).block(eq(7L), any());
    }

    @Test
    void 부서_관리자는_본인을_포함한_소유_부서_활성_구성원을_조회한다() {
        User manager = mock(User.class);
        Department department = mock(Department.class);
        when(manager.getUserId()).thenReturn(2L);
        when(users.findByLoginId("leader")).thenReturn(Optional.of(manager));
        when(departments.findByDepartmentIdAndStatus(4L, com.example.sideworks.department.entity.DepartmentStatus.ACTIVE))
                .thenReturn(Optional.of(department));
        when(department.getDepartmentId()).thenReturn(4L);
        when(department.getManagerUserId()).thenReturn(2L);
        when(department.getStatus()).thenReturn(com.example.sideworks.department.entity.DepartmentStatus.ACTIVE);
        var pageable = PageRequest.of(0, 100);
        when(users.findAllByStatusAndDepartment_DepartmentIdOrderByUserNameAscUserIdAsc(UserStatus.ACTIVE, 4L, pageable))
                .thenReturn(new PageImpl<>(List.of(manager)));

        var result = service.manageableMembers("leader", ApprovalTemplateScope.DEPARTMENT, 4L, false, pageable);

        assertThat(result.getContent()).extracting(com.example.sideworks.user.dto.UserDirectoryResponse::getUserId)
                .containsExactly(2L);
        verify(users, never()).findAllByStatusAndLoginIdNotOrderByUserNameAscUserIdAsc(any(), any(), any());
    }

    @Test
    void 다른_부서_구성원_조회는_관리_권한을_검사한다() {
        User manager = mock(User.class);
        Department department = mock(Department.class);
        when(manager.getUserId()).thenReturn(2L);
        when(users.findByLoginId("leader")).thenReturn(Optional.of(manager));
        when(departments.findByDepartmentIdAndStatus(5L, com.example.sideworks.department.entity.DepartmentStatus.ACTIVE))
                .thenReturn(Optional.of(department));
        when(department.getStatus()).thenReturn(com.example.sideworks.department.entity.DepartmentStatus.ACTIVE);
        when(department.getManagerUserId()).thenReturn(3L);

        assertThatThrownBy(() -> service.manageableMembers("leader", ApprovalTemplateScope.DEPARTMENT, 5L, false, PageRequest.of(0, 100)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TEMPLATE_FORBIDDEN));
        verify(users, never()).findAllByStatusAndDepartment_DepartmentIdOrderByUserNameAscUserIdAsc(any(), any(), any());
    }

    @Test
    void 부서_템플릿의_참조_후보는_전사_활성_구성원이다() {
        User manager = mock(User.class);
        User otherMember = mock(User.class);
        Department department = mock(Department.class);
        when(manager.getUserId()).thenReturn(2L);
        when(otherMember.getUserId()).thenReturn(9L);
        when(users.findByLoginId("leader")).thenReturn(Optional.of(manager));
        when(departments.findByDepartmentIdAndStatus(4L, com.example.sideworks.department.entity.DepartmentStatus.ACTIVE))
                .thenReturn(Optional.of(department));
        when(department.getStatus()).thenReturn(com.example.sideworks.department.entity.DepartmentStatus.ACTIVE);
        when(department.getManagerUserId()).thenReturn(2L);
        var pageable = PageRequest.of(0, 100);
        when(users.findAllByStatusOrderByUserNameAscUserIdAsc(UserStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(manager, otherMember)));

        var result = service.manageableMembers("leader", ApprovalTemplateScope.DEPARTMENT, 4L, true, pageable);

        assertThat(result.getContent()).extracting(com.example.sideworks.user.dto.UserDirectoryResponse::getUserId)
                .containsExactly(2L, 9L);
        verify(users, never()).findAllByStatusAndDepartment_DepartmentIdOrderByUserNameAscUserIdAsc(any(), any(), any());
    }

    @Test
    void 부서_템플릿에_다른_부서_참조자를_저장할_수_있다() {
        User actor = mock(User.class);
        User approver = mock(User.class);
        User reference = mock(User.class);
        Department department = mock(Department.class);
        when(users.findByLoginId("admin")).thenReturn(Optional.of(actor));
        when(actor.getUserRole()).thenReturn(UserRole.SUPER_ADMIN);
        when(departments.findByDepartmentIdAndStatus(4L, com.example.sideworks.department.entity.DepartmentStatus.ACTIVE))
                .thenReturn(Optional.of(department));
        when(department.getDepartmentId()).thenReturn(4L);
        when(approver.getUserId()).thenReturn(2L);
        when(approver.getDepartment()).thenReturn(department);
        when(approver.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(reference.getUserId()).thenReturn(9L);
        when(reference.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(users.findAllByUserIdIn(List.of(2L, 9L))).thenReturn(List.of(approver, reference));
        var request = new ApprovalTemplateSaveRequest("부서 결재", null, ApprovalTemplateScope.DEPARTMENT,
                4L, ApprovalTemplateStatus.ACTIVE, null, List.of(2L), List.of(9L));

        assertThatCode(() -> service.create("admin", request)).doesNotThrowAnyException();
        verify(ccs).saveAll(anyList());
    }

    @Test
    void 본인이_포함된_템플릿은_조회하고_수동_조정_필요를_반환한다() {
        User actor = mock(User.class);
        ApprovalTemplate template = mock(ApprovalTemplate.class);
        ApprovalTemplateLine line = mock(ApprovalTemplateLine.class);
        when(users.findByLoginId("leader")).thenReturn(Optional.of(actor));
        when(actor.getUserId()).thenReturn(2L);
        when(actor.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(templates.findWithDepartment(7L)).thenReturn(Optional.of(template));
        when(template.getScope()).thenReturn(ApprovalTemplateScope.COMMON);
        when(template.getStatus()).thenReturn(ApprovalTemplateStatus.ACTIVE);
        when(template.getValidationStatus()).thenReturn(ApprovalTemplateValidationStatus.VALID);
        when(lines.findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalStepAsc(7L)).thenReturn(List.of(line));
        when(ccs.findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalTemplateCcIdAsc(7L)).thenReturn(List.of());
        when(line.getApprover()).thenReturn(actor);
        when(line.getBaselineDepartmentId()).thenReturn(null);

        var result = service.resolve("leader", 7L);

        assertThat(result.approverIds()).containsExactly(2L);
        assertThat(result.authorIsApprover()).isTrue();
        verifyNoInteractions(blocker);
    }
}
