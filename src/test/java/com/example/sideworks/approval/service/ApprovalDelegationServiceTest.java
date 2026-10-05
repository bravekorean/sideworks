package com.example.sideworks.approval.service;

import com.example.sideworks.approval.dto.ApprovalDelegationSaveRequest;
import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalDelegation;
import com.example.sideworks.approval.entity.ApprovalDocumentTypeFixtures;
import com.example.sideworks.approval.entity.ApprovalLine;
import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.approval.repository.ApprovalCcRepository;
import com.example.sideworks.approval.repository.ApprovalDelegationRepository;
import com.example.sideworks.approval.repository.ApprovalLineRepository;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalDelegationServiceTest {
    @Mock ApprovalDelegationRepository delegations;
    @Mock UserRepository users;
    @Mock ApprovalLineRepository lines;
    @Mock ApprovalCcRepository ccs;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void 새로_활성화되는_첫_단계에만_대리인을_배정하고_원래_결재자를_보존한다() {
        User writer = user(1L, UserRole.USER, UserStatus.ACTIVE);
        User original = user(2L, UserRole.USER, UserStatus.ACTIVE);
        User delegatee = user(3L, UserRole.USER, UserStatus.ACTIVE);
        User later = user(4L, UserRole.USER, UserStatus.ACTIVE);
        Approval approval = Approval.createDraft(writer, "제목", "내용", ApprovalDocumentTypeFixtures.general());
        ApprovalLine first = ApprovalLine.create(approval, original, 1, ApprovalLineStatus.PENDING);
        ApprovalLine second = ApprovalLine.create(approval, later, 2, ApprovalLineStatus.WAITING);
        when(delegations.findByDelegator_UserIdAndCanceledAtIsNullAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                2L, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(ApprovalDelegation.create(original, delegatee,
                        LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 1), original)));

        service().assignFirstStep(approval, List.of(first, second), List.of());

        assertThat(first.getApprover()).isSameAs(delegatee);
        assertThat(first.getOriginalApprover()).isSameAs(original);
        assertThat(second.getApprover()).isSameAs(later);
    }

    @Test
    void 대리인이_작성자이면_원래_결재자를_유지한다() {
        User writer = user(1L, UserRole.USER, UserStatus.ACTIVE);
        User original = user(2L, UserRole.USER, UserStatus.ACTIVE);
        Approval approval = Approval.createDraft(writer, "제목", "내용", ApprovalDocumentTypeFixtures.general());
        ApprovalLine first = ApprovalLine.create(approval, original, 1, ApprovalLineStatus.PENDING);
        when(delegations.findByDelegator_UserIdAndCanceledAtIsNullAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                2L, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(ApprovalDelegation.create(original, writer,
                        LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 1), original)));

        service().assignFirstStep(approval, List.of(first), List.of());

        assertThat(first.getApprover()).isSameAs(original);
        assertThat(first.getOriginalApprover()).isNull();
    }

    @Test
    void 같은_위임자의_기간이_겹치면_등록을_거부한다() {
        User delegator = user(2L, UserRole.USER, UserStatus.ACTIVE);
        User delegatee = user(3L, UserRole.USER, UserStatus.ACTIVE);
        when(users.findByLoginId("delegator")).thenReturn(Optional.of(delegator));
        when(users.lockDelegator(2L)).thenReturn(Optional.of(delegator));
        when(users.findById(3L)).thenReturn(Optional.of(delegatee));
        when(delegations.findOverlappingForUpdate(2L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3)))
                .thenReturn(List.of(ApprovalDelegation.create(delegator, delegatee,
                        LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 4), delegator)));

        assertThatThrownBy(() -> service().create("delegator",
                new ApprovalDelegationSaveRequest(2L, 3L,
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DELEGATION_OVERLAP);
        verify(delegations, never()).save(any());
    }

    private ApprovalDelegationService service() {
        return new ApprovalDelegationService(delegations, users, lines, ccs, clock);
    }

    private User user(Long id, UserRole role, UserStatus status) {
        User user = mock(User.class);
        lenient().when(user.getUserId()).thenReturn(id);
        lenient().when(user.getUserRole()).thenReturn(role);
        lenient().when(user.getStatus()).thenReturn(status);
        return user;
    }
}
