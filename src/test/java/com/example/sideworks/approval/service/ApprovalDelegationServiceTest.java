package com.example.sideworks.approval.service;

import com.example.sideworks.approval.dto.ApprovalDelegationSaveRequest;
import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalCc;
import com.example.sideworks.approval.entity.ApprovalDelegation;
import com.example.sideworks.approval.entity.ApprovalDocumentType;
import com.example.sideworks.approval.entity.ApprovalDocumentTypeFixtures;
import com.example.sideworks.approval.entity.ApprovalLine;
import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.approval.entity.DocumentBehaviorType;
import com.example.sideworks.approval.repository.ApprovalCcRepository;
import com.example.sideworks.approval.repository.ApprovalDelegationRepository;
import com.example.sideworks.approval.repository.ApprovalLineRepository;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 처리 API의 실제 처리자 규칙(resolveProcessor) 단위 테스트.
 * 조회 쿼리와 같은 결과를 내는지는 ApprovalDelegationIntegrationTest가 실제 MySQL에서 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalDelegationServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

    @Mock ApprovalDelegationRepository delegations;
    @Mock UserRepository users;
    @Mock ApprovalLineRepository lines;
    @Mock ApprovalCcRepository ccs;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    private User writer;
    private User original;
    private User delegatee;

    @BeforeEach
    void setUp() {
        writer = user(1L, UserRole.USER, UserStatus.ACTIVE);
        original = user(2L, UserRole.USER, UserStatus.ACTIVE);
        delegatee = user(3L, UserRole.USER, UserStatus.ACTIVE);
    }

    @Test
    void 유효한_위임이_있으면_대결자가_처리한다() {
        Approval approval = approval(ApprovalDocumentTypeFixtures.general());
        ApprovalLine target = line(approval, original, 1, 11L);
        givenDelegation(original, delegatee);
        givenParticipants(approval, List.of(target), List.of());

        assertThat(service().resolveProcessor(approval, target, TODAY)).isSameAs(delegatee);
    }

    @Test
    void 유효한_위임이_없으면_원_결재자가_처리한다() {
        Approval approval = approval(ApprovalDocumentTypeFixtures.general());
        ApprovalLine target = line(approval, original, 1, 11L);
        when(delegations.findActiveForUpdate(2L, TODAY)).thenReturn(List.of());

        assertThat(service().resolveProcessor(approval, target, TODAY)).isSameAs(original);
    }

    @Test
    void 대결자가_비활성이면_원_결재자가_처리한다() {
        Approval approval = approval(ApprovalDocumentTypeFixtures.general());
        ApprovalLine target = line(approval, original, 1, 11L);
        User inactive = user(3L, UserRole.USER, UserStatus.INACTIVE);
        givenDelegation(original, inactive);

        assertThat(service().resolveProcessor(approval, target, TODAY)).isSameAs(original);
    }

    @Test
    void 대결자가_작성자이면_원_결재자가_처리한다() {
        Approval approval = approval(ApprovalDocumentTypeFixtures.general());
        ApprovalLine target = line(approval, original, 1, 11L);
        givenDelegation(original, writer);

        assertThat(service().resolveProcessor(approval, target, TODAY)).isSameAs(original);
    }

    @Test
    void 대결자가_참조자이면_원_결재자가_처리한다() {
        Approval approval = approval(ApprovalDocumentTypeFixtures.general());
        ApprovalLine target = line(approval, original, 1, 11L);
        givenDelegation(original, delegatee);
        givenParticipants(approval, List.of(target), List.of(ApprovalCc.create(approval, delegatee)));

        assertThat(service().resolveProcessor(approval, target, TODAY)).isSameAs(original);
    }

    @Test
    void 대결자가_다른_단계의_결재자이면_원_결재자가_처리한다() {
        Approval approval = approval(ApprovalDocumentTypeFixtures.general());
        ApprovalLine target = line(approval, original, 1, 11L);
        ApprovalLine later = line(approval, delegatee, 2, 12L);
        givenDelegation(original, delegatee);
        givenParticipants(approval, List.of(target, later), List.of());

        assertThat(service().resolveProcessor(approval, target, TODAY)).isSameAs(original);
    }

    @Test
    void 대결자가_앞_단계를_이미_대결로_처리했으면_원_결재자가_처리한다() {
        Approval approval = approval(ApprovalDocumentTypeFixtures.general());
        User firstApprover = user(4L, UserRole.USER, UserStatus.ACTIVE);
        ApprovalLine first = line(approval, firstApprover, 1, 11L);
        ReflectionTestUtils.setField(first, "approvalStatus", ApprovalLineStatus.PENDING);
        first.approve(delegatee, null, LocalDateTime.of(2026, 9, 30, 9, 0));
        ApprovalLine target = line(approval, original, 2, 12L);
        givenDelegation(original, delegatee);
        givenParticipants(approval, List.of(first, target), List.of());

        assertThat(service().resolveProcessor(approval, target, TODAY)).isSameAs(original);
    }

    @Test
    void 근태_정정_문서는_인사_권한이_없는_대결자에게_넘기지_않는다() {
        ApprovalDocumentType correction = ApprovalDocumentTypeFixtures.type(
                5L, "ATTENDANCE_CORRECTION", DocumentBehaviorType.ATTENDANCE_CORRECTION, true);
        Approval approval = approval(correction);
        ApprovalLine target = line(approval, original, 1, 11L);
        givenDelegation(original, delegatee);

        assertThat(service().resolveProcessor(approval, target, TODAY)).isSameAs(original);
    }

    @Test
    void 근태_정정_문서도_인사_담당_대결자에게는_넘긴다() {
        ApprovalDocumentType correction = ApprovalDocumentTypeFixtures.type(
                5L, "ATTENDANCE_CORRECTION", DocumentBehaviorType.ATTENDANCE_CORRECTION, true);
        Approval approval = approval(correction);
        ApprovalLine target = line(approval, original, 1, 11L);
        User hr = user(3L, UserRole.HR_MANAGER, UserStatus.ACTIVE);
        givenDelegation(original, hr);
        givenParticipants(approval, List.of(target), List.of());

        assertThat(service().resolveProcessor(approval, target, TODAY)).isSameAs(hr);
    }

    @Test
    void 같은_위임자의_기간이_겹치면_등록을_거부한다() {
        User delegator = user(2L, UserRole.USER, UserStatus.ACTIVE);
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

    private void givenDelegation(User from, User to) {
        when(delegations.findActiveForUpdate(from.getUserId(), TODAY))
                .thenReturn(List.of(ApprovalDelegation.create(from, to,
                        LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 1), from)));
    }

    private void givenParticipants(Approval approval, List<ApprovalLine> approvalLines, List<ApprovalCc> approvalCcs) {
        lenient().when(lines.findAllByApproval_ApprovalIdOrderByApprovalStepAsc(approval.getApprovalId()))
                .thenReturn(approvalLines);
        lenient().when(ccs.findByApproval_ApprovalId(approval.getApprovalId())).thenReturn(approvalCcs);
    }

    private Approval approval(ApprovalDocumentType type) {
        Approval approval = Approval.createDraft(writer, "제목", "내용", type);
        ReflectionTestUtils.setField(approval, "approvalId", 10L);
        return approval;
    }

    private ApprovalLine line(Approval approval, User approver, int step, Long lineId) {
        ApprovalLine line = ApprovalLine.create(approval, approver, step,
                step == 1 ? ApprovalLineStatus.PENDING : ApprovalLineStatus.WAITING);
        ReflectionTestUtils.setField(line, "approvalLineId", lineId);
        return line;
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
