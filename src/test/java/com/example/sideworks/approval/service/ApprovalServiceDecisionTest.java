package com.example.sideworks.approval.service;

import com.example.sideworks.approval.attachment.repository.ApprovalAttachmentRepository;
import com.example.sideworks.approval.attachment.storage.FileStorage;
import com.example.sideworks.approval.dto.ApprovalDecisionRequest;
import com.example.sideworks.approval.dto.ApprovalTerminateRequest;
import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalActionType;
import com.example.sideworks.approval.entity.ApprovalHistory;
import com.example.sideworks.approval.entity.ApprovalLine;
import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.approval.factory.ApprovalSubmissionFactory;
import com.example.sideworks.approval.repository.ApprovalCcRepository;
import com.example.sideworks.approval.repository.ApprovalHistoryRepository;
import com.example.sideworks.approval.repository.ApprovalLineRepository;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.approval.validator.ApprovalSubmissionValidator;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceDecisionTest {

    private static final Long APPROVAL_ID = 10L;
    private static final Long ACTOR_ID = 2L;
    private static final String LOGIN_ID = "approver";

    @Mock
    private ApprovalRepository approvalRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApprovalLineRepository approvalLineRepository;
    @Mock
    private ApprovalCcRepository approvalCcRepository;
    @Mock
    private ApprovalHistoryRepository approvalHistoryRepository;
    @Mock
    private ApprovalAttachmentRepository attachmentRepository;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private ApprovalSubmissionValidator submissionValidator;
    @Mock
    private ApprovalSubmissionFactory submissionFactory;
    @Mock
    private com.example.sideworks.leave.service.LeaveRequestService leaveRequests;
    @Mock
    private com.example.sideworks.leave.service.LeaveCancellationService leaveCancellations;

    @Mock
    private ApprovalDelegationService delegationService;

    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(
                approvalRepository,
                userRepository,
                approvalLineRepository,
                approvalCcRepository,
                approvalHistoryRepository,
                attachmentRepository,
                fileStorage,
                submissionValidator,
                submissionFactory,
                org.mockito.Mockito.mock(com.example.sideworks.attendance.service.AttendanceCorrectionService.class),
                leaveRequests,
                leaveCancellations,
                org.mockito.Mockito.mock(ApprovalDocumentTypeService.class),
                org.mockito.Mockito.mock(com.example.sideworks.notification.service.ApprovalNotificationWorkflow.class),
                org.mockito.Mockito.mock(ApprovalTemplateService.class),
                delegationService,
                java.time.Clock.fixed(java.time.Instant.parse("2026-11-28T01:00:00Z"), java.time.ZoneId.of("Asia/Seoul")),
                org.springframework.transaction.support.TransactionOperations.withoutTransaction()
        );
        // 기본은 위임이 없는 상태: 원 결재자가 처리자다.
        org.mockito.Mockito.lenient().when(delegationService.resolveProcessor(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> ((ApprovalLine) invocation.getArgument(1)).getApprover());
    }

    @Test
    void 중간_결재를_승인하면_다음_결재선이_활성화된다() {
        User actor = user(ACTOR_ID);
        Approval approval = inProgressApproval();
        ApprovalLine currentLine = line(approval, actor, 1, ApprovalLineStatus.PENDING);
        ApprovalLine nextLine = line(approval, mock(User.class), 3, ApprovalLineStatus.WAITING);
        prepareDecision(actor, approval, currentLine);
        when(approvalLineRepository
                .findFirstByApproval_ApprovalIdAndApprovalStepGreaterThanOrderByApprovalStepAsc(APPROVAL_ID, 1))
                .thenReturn(Optional.of(nextLine));

        approvalService.approveApproval(APPROVAL_ID, LOGIN_ID, request(" 확인 완료 "));

        assertThat(currentLine.getApprovalStatus()).isEqualTo(ApprovalLineStatus.APPROVED);
        assertThat(currentLine.getApprovalComment()).isEqualTo("확인 완료");
        assertThat(currentLine.getProcessedAt()).isNotNull();
        assertThat(nextLine.getApprovalStatus()).isEqualTo(ApprovalLineStatus.PENDING);
        assertThat(approval.getCurrentStep()).isEqualTo(3);
        assertThat(approval.getApprovalStatus()).isEqualTo(ApprovalStatus.IN_PROGRESS);
        verifyNoInteractions(leaveRequests, leaveCancellations);
        assertHistory(ApprovalActionType.APPROVED, "확인 완료");
    }

    @Test
    void 마지막_결재를_승인하면_문서가_최종_승인된다() {
        User actor = user(ACTOR_ID);
        Approval approval = inProgressApproval();
        ApprovalLine currentLine = line(approval, actor, 1, ApprovalLineStatus.PENDING);
        prepareDecision(actor, approval, currentLine);
        when(approvalLineRepository
                .findFirstByApproval_ApprovalIdAndApprovalStepGreaterThanOrderByApprovalStepAsc(APPROVAL_ID, 1))
                .thenReturn(Optional.empty());

        approvalService.approveApproval(APPROVAL_ID, LOGIN_ID, null);

        assertThat(currentLine.getApprovalStatus()).isEqualTo(ApprovalLineStatus.APPROVED);
        assertThat(approval.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.getCompletedAt()).isEqualTo(currentLine.getProcessedAt());
        verify(leaveRequests).onDecision(approval, actor, true);
        verify(leaveCancellations).onDecision(approval, actor, true);
        assertHistory(ApprovalActionType.APPROVED, null);
    }

    @Test
    void 현재_결재자가_반려하면_문서와_결재선이_반려된다() {
        User actor = user(ACTOR_ID);
        Approval approval = inProgressApproval();
        ApprovalLine currentLine = line(approval, actor, 1, ApprovalLineStatus.PENDING);
        prepareDecision(actor, approval, currentLine);

        approvalService.rejectApproval(APPROVAL_ID, LOGIN_ID, request(" 자료 보완 필요 "));

        assertThat(currentLine.getApprovalStatus()).isEqualTo(ApprovalLineStatus.REJECTED);
        assertThat(currentLine.getApprovalComment()).isEqualTo("자료 보완 필요");
        assertThat(approval.getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(approval.getCompletedAt()).isEqualTo(currentLine.getProcessedAt());
        assertHistory(ApprovalActionType.REJECTED, "자료 보완 필요");
    }

    @Test
    void 현재_결재자가_아니면_승인할_수_없다() {
        User actor = user(ACTOR_ID);
        Approval approval = inProgressApproval();
        ApprovalLine currentLine = line(approval, user(99L), 1, ApprovalLineStatus.PENDING);
        prepareDecision(actor, approval, currentLine);

        assertThatThrownBy(() -> approvalService.approveApproval(APPROVAL_ID, LOGIN_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPROVAL_DECISION_FORBIDDEN);

        verify(approvalHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 대기_상태의_결재선은_처리할_수_없다() {
        User actor = user(ACTOR_ID);
        Approval approval = inProgressApproval();
        ApprovalLine currentLine = line(approval, actor, 1, ApprovalLineStatus.WAITING);
        prepareDecision(actor, approval, currentLine);

        assertThatThrownBy(() -> approvalService.approveApproval(APPROVAL_ID, LOGIN_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPROVAL_LINE_NOT_PROCESSABLE);
    }

    @Test
    void 진행_중이_아닌_문서는_처리할_수_없다() {
        Approval approval = inProgressApproval();
        ReflectionTestUtils.setField(approval, "approvalStatus", ApprovalStatus.APPROVED);
        when(approvalRepository.findByIdForUpdate(APPROVAL_ID)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> approvalService.approveApproval(APPROVAL_ID, LOGIN_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPROVAL_NOT_IN_PROGRESS);

        verify(approvalLineRepository, never())
                .findByApproval_ApprovalIdAndApprovalStep(APPROVAL_ID, 1);
    }

    @Test
    void 반려_사유가_없으면_반려할_수_없다() {
        User actor = user(ACTOR_ID);
        Approval approval = inProgressApproval();
        ApprovalLine currentLine = line(approval, actor, 1, ApprovalLineStatus.PENDING);
        prepareDecision(actor, approval, currentLine);

        assertThatThrownBy(() -> approvalService.rejectApproval(APPROVAL_ID, LOGIN_ID, request("  ")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REJECTION_COMMENT_REQUIRED);

        assertThat(currentLine.getApprovalStatus()).isEqualTo(ApprovalLineStatus.PENDING);
        assertThat(approval.getApprovalStatus()).isEqualTo(ApprovalStatus.IN_PROGRESS);
    }

    @Test
    void 처리된_결재자가_없으면_작성자가_상신을_취소할_수_있다() {
        User writer = user(ACTOR_ID);
        Approval approval = inProgressApproval(writer);
        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(writer));
        when(approvalRepository.findByIdForUpdate(APPROVAL_ID)).thenReturn(Optional.of(approval));
        when(approvalLineRepository.existsByApproval_ApprovalIdAndProcessedAtIsNotNull(APPROVAL_ID))
                .thenReturn(false);
        ApprovalLine currentLine = line(approval, mock(User.class), 1, ApprovalLineStatus.PENDING);
        when(approvalLineRepository.findByApproval_ApprovalIdAndApprovalStep(APPROVAL_ID, 1))
                .thenReturn(Optional.of(currentLine));

        approvalService.cancelApproval(APPROVAL_ID, LOGIN_ID);

        assertThat(approval.getApprovalStatus()).isEqualTo(ApprovalStatus.CANCELED);
        assertThat(approval.getCompletedAt()).isNotNull();
        assertHistory(ApprovalActionType.CANCELED, null);
    }

    @Test
    void 작성자가_아니면_상신을_취소할_수_없다() {
        User actor = user(ACTOR_ID);
        Approval approval = inProgressApproval(user(99L));
        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(actor));
        when(approvalRepository.findByIdForUpdate(APPROVAL_ID)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> approvalService.cancelApproval(APPROVAL_ID, LOGIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPROVAL_CANCEL_FORBIDDEN);

        verify(approvalLineRepository, never())
                .existsByApproval_ApprovalIdAndProcessedAtIsNotNull(APPROVAL_ID);
    }

    @Test
    void 이미_처리된_결재자가_있으면_상신을_취소할_수_없다() {
        User writer = user(ACTOR_ID);
        Approval approval = inProgressApproval(writer);
        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(writer));
        when(approvalRepository.findByIdForUpdate(APPROVAL_ID)).thenReturn(Optional.of(approval));
        when(approvalLineRepository.existsByApproval_ApprovalIdAndProcessedAtIsNotNull(APPROVAL_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> approvalService.cancelApproval(APPROVAL_ID, LOGIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPROVAL_CANCEL_NOT_ALLOWED);

        assertThat(approval.getApprovalStatus()).isEqualTo(ApprovalStatus.IN_PROGRESS);
        verify(approvalHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 비활성_결재자가_대기중이면_관리자가_사유와_함께_강제_종료한다() {
        User admin = mock(User.class);
        when(admin.getUserRole()).thenReturn(UserRole.SUPER_ADMIN);
        User approver = mock(User.class);
        when(approver.getStatus()).thenReturn(UserStatus.INACTIVE);
        Approval approval = inProgressApproval();
        ApprovalLine line = line(approval, approver, 1, ApprovalLineStatus.PENDING);
        prepareDecision(admin, approval, line);

        approvalService.terminateApproval(APPROVAL_ID, LOGIN_ID, new ApprovalTerminateRequest(" 인사이동으로 처리 불가 "));

        assertThat(approval.getApprovalStatus()).isEqualTo(ApprovalStatus.TERMINATED);
        assertThat(approval.getCompletedAt()).isNotNull();
        assertThat(line.getApprovalStatus()).isEqualTo(ApprovalLineStatus.PENDING);
        assertHistory(ApprovalActionType.TERMINATED, "인사이동으로 처리 불가");
        verify(leaveCancellations).onCancel(APPROVAL_ID);
        verifyNoInteractions(leaveRequests);
    }

    @Test
    void 활성_결재자가_대기중이면_관리자도_강제_종료할_수_없다() {
        User admin = mock(User.class);
        when(admin.getUserRole()).thenReturn(UserRole.SUPER_ADMIN);
        User approver = mock(User.class);
        when(approver.getStatus()).thenReturn(UserStatus.ACTIVE);
        Approval approval = inProgressApproval();
        prepareDecision(admin, approval, line(approval, approver, 1, ApprovalLineStatus.PENDING));

        assertThatThrownBy(() -> approvalService.terminateApproval(APPROVAL_ID, LOGIN_ID,
                new ApprovalTerminateRequest("사유")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APPROVAL_TERMINATE_NOT_ALLOWED);
        assertThat(approval.getApprovalStatus()).isEqualTo(ApprovalStatus.IN_PROGRESS);
        verify(approvalHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 일반_사용자는_강제_종료할_수_없다() {
        User user = mock(User.class);
        when(user.getUserRole()).thenReturn(UserRole.USER);
        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> approvalService.terminateApproval(APPROVAL_ID, LOGIN_ID,
                new ApprovalTerminateRequest("사유")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APPROVAL_TERMINATE_FORBIDDEN);
        verify(approvalRepository, never()).findByIdForUpdate(APPROVAL_ID);
    }

    @Test
    void 강제_종료_사유가_없으면_문서를_변경하지_않는다() {
        User admin = mock(User.class);
        when(admin.getUserRole()).thenReturn(UserRole.SUPER_ADMIN);
        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> approvalService.terminateApproval(APPROVAL_ID, LOGIN_ID,
                new ApprovalTerminateRequest(" ")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APPROVAL_TERMINATE_REASON_REQUIRED);
        verify(approvalRepository, never()).findByIdForUpdate(APPROVAL_ID);
    }

    // REPEATABLE READ 스냅샷은 첫 일반 SELECT에서 고정되므로 문서 잠금이 첫 SQL이어야 한다.
    // 실제 MySQL 경합 재현은 LockContentionIntegrationTest가 담당한다.
    @Test
    void 승인_반려_회수는_사용자_조회보다_문서_잠금을_먼저_얻는다() {
        List<Runnable> requests = List.of(
                () -> approvalService.approveApproval(APPROVAL_ID, LOGIN_ID, null),
                () -> approvalService.rejectApproval(APPROVAL_ID, LOGIN_ID, request("사유")),
                () -> approvalService.cancelApproval(APPROVAL_ID, LOGIN_ID));

        for (Runnable request : requests) {
            // 문서가 없으면 잠금 직후 끝나므로, 사용자 조회가 한 번도 없어야 잠금이 먼저다.
            assertThatThrownBy(request::run)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.APPROVAL_NOT_FOUND);
        }
        verify(approvalRepository, times(3)).findByIdForUpdate(APPROVAL_ID);
        verifyNoInteractions(userRepository);
    }

    // 처리 API의 오류 우선순위: 문서 잠금이 먼저이므로 사용자와 문서가 함께 실패하면 문서 오류가 나간다.
    // 인증된 요청의 사용자가 없는 경우는 토큰 발급 뒤 계정이 사라진 경우뿐이라 이 순서를 계약으로 둔다.
    @Test
    void 사용자와_문서가_모두_처리_불가하면_문서_오류가_먼저다() {
        Approval approved = inProgressApproval();
        ReflectionTestUtils.setField(approved, "approvalStatus", ApprovalStatus.APPROVED);
        when(approvalRepository.findByIdForUpdate(APPROVAL_ID)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> approvalService.approveApproval(APPROVAL_ID, "missing-user", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APPROVAL_NOT_IN_PROGRESS);
        verifyNoInteractions(userRepository);
    }

    @Test
    void 강제_종료는_권한을_확인한_뒤_문서를_잠근다() {
        User admin = mock(User.class);
        when(admin.getUserRole()).thenReturn(UserRole.SUPER_ADMIN);
        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> approvalService.terminateApproval(APPROVAL_ID, LOGIN_ID,
                new ApprovalTerminateRequest("사유")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APPROVAL_NOT_FOUND);
        // 사전 검사의 사용자 조회는 업무 트랜잭션 밖이고, 업무 트랜잭션은 잠금으로 시작한다.
        InOrder order = inOrder(userRepository, approvalRepository);
        order.verify(userRepository).findByLoginId(LOGIN_ID);
        order.verify(approvalRepository).findByIdForUpdate(APPROVAL_ID);
        verify(userRepository, times(1)).findByLoginId(LOGIN_ID);
    }

    private void prepareDecision(User actor, Approval approval, ApprovalLine currentLine) {
        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(actor));
        when(approvalRepository.findByIdForUpdate(APPROVAL_ID)).thenReturn(Optional.of(approval));
        when(approvalLineRepository.findByApproval_ApprovalIdAndApprovalStep(APPROVAL_ID, 1))
                .thenReturn(Optional.of(currentLine));
    }

    private Approval inProgressApproval() {
        return inProgressApproval(mock(User.class));
    }

    private Approval inProgressApproval(User writer) {
        Approval approval = Approval.createDraft(writer, "제목", "본문", com.example.sideworks.approval.entity.ApprovalDocumentTypeFixtures.general());
        ReflectionTestUtils.setField(approval, "approvalId", APPROVAL_ID);
        approval.submit(LocalDateTime.of(2026, 7, 12, 12, 0));
        return approval;
    }

    private ApprovalLine line(
            Approval approval,
            User approver,
            int step,
            ApprovalLineStatus status
    ) {
        return ApprovalLine.create(approval, approver, step, status);
    }

    private User user(Long userId) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(userId);
        return user;
    }

    private ApprovalDecisionRequest request(String comment) {
        ApprovalDecisionRequest request = new ApprovalDecisionRequest();
        ReflectionTestUtils.setField(request, "comment", comment);
        return request;
    }

    private void assertHistory(ApprovalActionType actionType, String comment) {
        ArgumentCaptor<ApprovalHistory> captor = ArgumentCaptor.forClass(ApprovalHistory.class);
        verify(approvalHistoryRepository).save(captor.capture());
        ApprovalHistory history = captor.getValue();
        assertThat(history.getActionType()).isEqualTo(actionType);
        assertThat(history.getActionStep()).isEqualTo(1);
        assertThat(history.getComment()).isEqualTo(comment);
    }
}
