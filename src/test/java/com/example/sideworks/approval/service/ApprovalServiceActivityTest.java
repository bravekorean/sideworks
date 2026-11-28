package com.example.sideworks.approval.service;

import com.example.sideworks.approval.attachment.repository.ApprovalAttachmentRepository;
import com.example.sideworks.approval.attachment.storage.FileStorage;
import com.example.sideworks.approval.dto.ApprovalActivityResponse;
import com.example.sideworks.approval.dto.ApprovalListResponse;
import com.example.sideworks.approval.factory.ApprovalSubmissionFactory;
import com.example.sideworks.approval.repository.ApprovalCcRepository;
import com.example.sideworks.approval.repository.ApprovalHistoryRepository;
import com.example.sideworks.approval.repository.ApprovalLineRepository;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.approval.validator.ApprovalSubmissionValidator;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceActivityTest {

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
                org.mockito.Mockito.mock(com.example.sideworks.leave.service.LeaveRequestService.class),
                org.mockito.Mockito.mock(com.example.sideworks.leave.service.LeaveCancellationService.class),
                org.mockito.Mockito.mock(ApprovalDocumentTypeService.class),
                org.mockito.Mockito.mock(com.example.sideworks.notification.service.ApprovalNotificationWorkflow.class),
                org.mockito.Mockito.mock(ApprovalTemplateService.class),
                org.mockito.Mockito.mock(ApprovalDelegationService.class),
                java.time.Clock.fixed(java.time.Instant.parse("2026-11-28T01:00:00Z"), java.time.ZoneId.of("Asia/Seoul")),
                org.springframework.transaction.support.TransactionOperations.withoutTransaction()
        );
    }

    @Test
    void 로그인_사용자가_참여한_결재의_최근_활동을_조회한다() {
        String loginId = "viewer";
        Long userId = 2L;
        PageRequest pageable = PageRequest.of(0, 5);
        User user = mock(User.class);
        Page<ApprovalActivityResponse> expected = new PageImpl<>(List.of(), pageable, 0);

        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
        when(user.getUserId()).thenReturn(userId);
        when(approvalRepository.findRecentActivitiesByUserId(userId, pageable))
                .thenReturn(expected);

        Page<ApprovalActivityResponse> result = approvalService.getRecentActivities(loginId, pageable);

        assertThat(result).isSameAs(expected);
        verify(approvalRepository).findRecentActivitiesByUserId(userId, pageable);
    }

    @Test
    void 대시보드는_로그인_사용자의_건수와_제한된_목록만_조회한다() {
        User user = mock(User.class);
        var pendingItem = new ApprovalListResponse(10L, "구매 요청", 3L, "작성자",
                com.example.sideworks.approval.entity.ApprovalStatus.IN_PROGRESS,
                1, null, null, null);
        var activity = new ApprovalActivityResponse(30L, 10L, "구매 요청", 2L, "결재자", 1,
                com.example.sideworks.approval.entity.ApprovalActionType.APPROVED, null);
        var pendingPage = new PageImpl<>(List.of(pendingItem), PageRequest.of(0, 4), 7);
        when(userRepository.findByLoginId("viewer")).thenReturn(Optional.of(user));
        when(user.getUserId()).thenReturn(2L);
        when(approvalRepository.findPendingByProcessorId(2L, java.time.LocalDate.of(2026, 11, 28), null, PageRequest.of(0, 4))).thenReturn(pendingPage);
        when(approvalRepository.countInProgressByWriterId(2L)).thenReturn(3L);
        when(approvalRepository.countDraftsByWriterId(2L)).thenReturn(4L);
        when(approvalRepository.countCcByUserId(2L)).thenReturn(5L);
        when(approvalRepository.findLatestActivitiesByUserId(2L, 5)).thenReturn(List.of(activity));

        var result = approvalService.getDashboard("viewer");

        assertThat(result.summary().pending()).isEqualTo(7);
        assertThat(result.summary().inProgress()).isEqualTo(3);
        assertThat(result.summary().drafts()).isEqualTo(4);
        assertThat(result.summary().cc()).isEqualTo(5);
        assertThat(result.pendingApprovals()).containsExactly(pendingItem);
        assertThat(result.recentActivities()).containsExactly(activity);
        verify(userRepository).findByLoginId("viewer");
        verify(approvalRepository).findPendingByProcessorId(2L, java.time.LocalDate.of(2026, 11, 28), null, PageRequest.of(0, 4));
        verify(approvalRepository).countInProgressByWriterId(2L);
        verify(approvalRepository).countDraftsByWriterId(2L);
        verify(approvalRepository).countCcByUserId(2L);
        verify(approvalRepository).findLatestActivitiesByUserId(2L, 5);
        verifyNoMoreInteractions(userRepository, approvalRepository);
    }

    @Test
    void 대시보드에_문서가_없으면_건수는_0이고_목록은_비어있다() {
        User user = mock(User.class);
        when(userRepository.findByLoginId("viewer")).thenReturn(Optional.of(user));
        when(user.getUserId()).thenReturn(2L);
        when(approvalRepository.findPendingByProcessorId(2L, java.time.LocalDate.of(2026, 11, 28), null, PageRequest.of(0, 4)))
                .thenReturn(Page.empty(PageRequest.of(0, 4)));
        when(approvalRepository.findLatestActivitiesByUserId(2L, 5)).thenReturn(List.of());

        var result = approvalService.getDashboard("viewer");

        assertThat(result.summary()).isEqualTo(
                new com.example.sideworks.approval.dto.ApprovalDashboardResponse.Summary(0, 0, 0, 0));
        assertThat(result.pendingApprovals()).isEmpty();
        assertThat(result.recentActivities()).isEmpty();
    }

    @Test
    void 대시보드_사용자가_존재하지_않으면_문서를_조회하지_않는다() {
        when(userRepository.findByLoginId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.getDashboard("missing"))
                .isInstanceOfSatisfying(com.example.sideworks.common.exception.BusinessException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo(
                                com.example.sideworks.common.exception.ErrorCode.USER_NOT_FOUND));
        verifyNoInteractions(approvalRepository);
    }

    @Test
    void 시스템_관리자는_참여_여부와_관계없이_관리_목록을_조회한다() {
        User admin = mock(User.class);
        var pageable = PageRequest.of(0, 20);
        Page<ApprovalListResponse> expected = Page.empty(pageable);
        when(userRepository.findByLoginId("admin")).thenReturn(Optional.of(admin));
        when(admin.getUserRole()).thenReturn(UserRole.SUPER_ADMIN);
        when(approvalRepository.findForManagement("문서", null, true, java.time.LocalDate.of(2026, 11, 28), pageable)).thenReturn(expected);

        assertThat(approvalService.getManagedApprovals("admin", "문서", null, true, pageable)).isSameAs(expected);
        verify(approvalRepository).findForManagement("문서", null, true, java.time.LocalDate.of(2026, 11, 28), pageable);
    }

    @Test
    void 일반_사용자와_인사관리자는_관리_목록을_조회할_수_없다() {
        User user = mock(User.class);
        when(userRepository.findByLoginId("user")).thenReturn(Optional.of(user));
        for (UserRole role : List.of(UserRole.USER, UserRole.HR_MANAGER)) {
            when(user.getUserRole()).thenReturn(role);
            assertThatThrownBy(() -> approvalService.getManagedApprovals("user", null, null, false, PageRequest.of(0, 20)))
                    .isInstanceOfSatisfying(com.example.sideworks.common.exception.BusinessException.class,
                            failure -> assertThat(failure.getErrorCode()).isEqualTo(
                                    com.example.sideworks.common.exception.ErrorCode.APPROVAL_MANAGEMENT_FORBIDDEN));
        }
        org.mockito.Mockito.verifyNoInteractions(approvalRepository);
    }

    @Test
    void 일반_사용자는_자신의_조회_권한_범위에서_통합_검색한다() {
        String loginId = "viewer";
        Long userId = 2L;
        PageRequest pageable = PageRequest.of(0, 20);
        User user = mock(User.class);
        Page<ApprovalListResponse> expected = new PageImpl<>(List.of(), pageable, 0);

        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
        when(user.getUserId()).thenReturn(userId);
        when(user.getUserRole()).thenReturn(UserRole.USER);
        when(approvalRepository.searchApprovals(userId, false, "홍길동", pageable))
                .thenReturn(expected);

        Page<ApprovalListResponse> result = approvalService.searchApprovals(
                loginId,
                "  홍길동  ",
                pageable
        );

        assertThat(result).isSameAs(expected);
        verify(approvalRepository).searchApprovals(userId, false, "홍길동", pageable);
    }

    @Test
    void 검색어가_공백이면_통합_검색을_거부한다() {
        assertThatThrownBy(() -> approvalService.searchApprovals(
                "viewer",
                "   ",
                PageRequest.of(0, 20)
        ))
                .isInstanceOf(com.example.sideworks.common.exception.BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(com.example.sideworks.common.exception.ErrorCode.INVALID_REQUEST);
    }
}
