package com.example.sideworks.approval.service;

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
