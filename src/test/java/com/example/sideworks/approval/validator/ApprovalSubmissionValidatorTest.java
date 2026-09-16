package com.example.sideworks.approval.validator;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.department.entity.Department;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalSubmissionValidatorTest {

    private final ApprovalSubmissionValidator validator = new ApprovalSubmissionValidator();

    @Test
    void 부서장_관계가_있는_USER는_결재자로_지정할_수_있다() {
        User writer = user(1L, UserRole.USER, null);
        Department department = Department.create("개발팀", null);
        ReflectionTestUtils.setField(department, "departmentId", 10L);
        department.assignManager(2L);
        User teamLeader = user(2L, UserRole.USER, department);
        Approval approval = Approval.createDraft(writer, "제목", "내용");

        assertThatCode(() -> validator.validateParticipants(
                approval,
                List.of(teamLeader),
                List.of()
        )).doesNotThrowAnyException();
    }

    @Test
    void 부서장_관계와_관리_역할이_없는_USER는_결재자로_지정할_수_없다() {
        User writer = user(1L, UserRole.USER, null);
        Department department = Department.create("개발팀", null);
        ReflectionTestUtils.setField(department, "departmentId", 10L);
        User member = user(2L, UserRole.USER, department);
        Approval approval = Approval.createDraft(writer, "제목", "내용");

        assertThatThrownBy(() -> validator.validateParticipants(
                approval,
                List.of(member),
                List.of()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_APPROVER);
    }

    private User user(Long userId, UserRole role, Department department) {
        User user = User.create(
                "user-" + userId,
                "password",
                "사용자" + userId,
                null,
                null,
                "EMP-" + userId,
                null,
                null,
                department,
                null,
                role,
                UserStatus.ACTIVE
        );
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }
}
