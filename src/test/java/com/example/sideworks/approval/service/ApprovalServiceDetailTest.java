package com.example.sideworks.approval.service;

import com.example.sideworks.approval.dto.ApprovalDetailHeaderResponse;
import com.example.sideworks.approval.dto.ApprovalDetailResponse;
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
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceDetailTest {

    private static final Long APPROVAL_ID = 10L;
    private static final Long VIEWER_ID = 2L;
    private static final String LOGIN_ID = "viewer";

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
                submissionValidator,
                submissionFactory
        );
    }

    @Test
    void 결재_참여자는_상세_문서를_조회할_수_있다() {
        User viewer = viewer(UserRole.USER);
        when(viewer.getUserId()).thenReturn(VIEWER_ID);
        ApprovalDetailHeaderResponse header = header();

        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(viewer));
        when(approvalRepository.findAccessibleDetailHeader(APPROVAL_ID, VIEWER_ID))
                .thenReturn(Optional.of(header));
        when(approvalRepository.findDetailLinesByApprovalId(APPROVAL_ID)).thenReturn(List.of());
        when(approvalRepository.findDetailCcsByApprovalId(APPROVAL_ID)).thenReturn(List.of());
        when(approvalRepository.findDetailHistoriesByApprovalId(APPROVAL_ID)).thenReturn(List.of());

        ApprovalDetailResponse response = approvalService.getApprovalDetail(APPROVAL_ID, LOGIN_ID);

        assertThat(response.getApprovalId()).isEqualTo(APPROVAL_ID);
        assertThat(response.getContent()).isEqualTo("본문");
        assertThat(response.getApprovalLines()).isEmpty();
        assertThat(response.getCcUsers()).isEmpty();
        assertThat(response.getHistories()).isEmpty();
        verify(approvalRepository, never()).findDetailHeaderByApprovalId(APPROVAL_ID);
    }

    @Test
    void 슈퍼_관리자는_참여하지_않은_문서도_조회할_수_있다() {
        User viewer = viewer(UserRole.SUPER_ADMIN);

        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(viewer));
        when(approvalRepository.findDetailHeaderByApprovalId(APPROVAL_ID))
                .thenReturn(Optional.of(header()));
        when(approvalRepository.findDetailLinesByApprovalId(APPROVAL_ID)).thenReturn(List.of());
        when(approvalRepository.findDetailCcsByApprovalId(APPROVAL_ID)).thenReturn(List.of());
        when(approvalRepository.findDetailHistoriesByApprovalId(APPROVAL_ID)).thenReturn(List.of());

        ApprovalDetailResponse response = approvalService.getApprovalDetail(APPROVAL_ID, LOGIN_ID);

        assertThat(response.getApprovalId()).isEqualTo(APPROVAL_ID);
        verify(approvalRepository, never()).findAccessibleDetailHeader(APPROVAL_ID, VIEWER_ID);
    }

    @Test
    void 일반_비참여자는_문서_존재를_알_수_없다() {
        assertNonParticipantCannotRead(UserRole.USER);
    }

    @Test
    void 일반_관리자도_비참여_문서를_조회할_수_없다() {
        assertNonParticipantCannotRead(UserRole.ADMIN);
    }

    private void assertNonParticipantCannotRead(UserRole role) {
        User viewer = viewer(role);
        when(viewer.getUserId()).thenReturn(VIEWER_ID);

        when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(viewer));
        when(approvalRepository.findAccessibleDetailHeader(APPROVAL_ID, VIEWER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.getApprovalDetail(APPROVAL_ID, LOGIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPROVAL_NOT_FOUND);

        verify(approvalRepository, never()).findDetailLinesByApprovalId(APPROVAL_ID);
        verify(approvalRepository, never()).findDetailCcsByApprovalId(APPROVAL_ID);
        verify(approvalRepository, never()).findDetailHistoriesByApprovalId(APPROVAL_ID);
    }

    private User viewer(UserRole role) {
        User viewer = mock(User.class);
        when(viewer.getUserRole()).thenReturn(role);
        return viewer;
    }

    private ApprovalDetailHeaderResponse header() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 12, 0);

        return new ApprovalDetailHeaderResponse(
                APPROVAL_ID,
                1L,
                "작성자",
                "제목",
                "본문",
                ApprovalStatus.IN_PROGRESS,
                1,
                now,
                now,
                now,
                null
        );
    }
}
