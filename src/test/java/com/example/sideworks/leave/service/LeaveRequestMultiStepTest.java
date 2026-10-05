package com.example.sideworks.leave.service;

import com.example.sideworks.approval.attachment.service.ApprovalAttachmentService;
import com.example.sideworks.approval.entity.*;
import com.example.sideworks.approval.factory.ApprovalSubmissionFactory;
import com.example.sideworks.approval.repository.*;
import com.example.sideworks.approval.service.ApprovalDocumentTypeService;
import com.example.sideworks.approval.service.ApprovalTemplateService;
import com.example.sideworks.approval.validator.ApprovalSubmissionValidator;
import com.example.sideworks.leave.dto.LeaveRequestDocumentInput;
import com.example.sideworks.leave.entity.AnnualLeaveBalance;
import com.example.sideworks.leave.entity.LeavePeriod;
import com.example.sideworks.leave.policy.LeaveRequestPolicy;
import com.example.sideworks.leave.repository.*;
import com.example.sideworks.notification.service.ApprovalNotificationWorkflow;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeaveRequestMultiStepTest {
    @Test
    void 휴가_상신은_결재_두_단계와_참조자를_원본_문서에_저장한다() {
        UserRepository users = mock(UserRepository.class);
        AnnualLeaveBalanceService balanceService = mock(AnnualLeaveBalanceService.class);
        AnnualLeaveBalanceRepository balances = mock(AnnualLeaveBalanceRepository.class);
        AnnualLeaveHistoryRepository histories = mock(AnnualLeaveHistoryRepository.class);
        LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
        LeaveRequestDayRepository days = mock(LeaveRequestDayRepository.class);
        LeaveRequestPolicy policy = mock(LeaveRequestPolicy.class);
        ApprovalRepository approvals = mock(ApprovalRepository.class);
        ApprovalLineRepository lines = mock(ApprovalLineRepository.class);
        ApprovalCcRepository ccs = mock(ApprovalCcRepository.class);
        ApprovalHistoryRepository approvalHistories = mock(ApprovalHistoryRepository.class);
        ApprovalSubmissionValidator validator = mock(ApprovalSubmissionValidator.class);
        ApprovalDocumentTypeService documentTypes = mock(ApprovalDocumentTypeService.class);
        ApprovalAttachmentService attachments = mock(ApprovalAttachmentService.class);
        ApprovalNotificationWorkflow notifications = mock(ApprovalNotificationWorkflow.class);
        ApprovalTemplateService templates = mock(ApprovalTemplateService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        LeaveRequestService service = new LeaveRequestService(users, balanceService, balances, histories,
                requests, days, policy, approvals, lines, ccs, approvalHistories,
                new ApprovalSubmissionFactory(org.mockito.Mockito.mock(com.example.sideworks.approval.service.ApprovalDelegationService.class)), validator, documentTypes, attachments, clock, notifications, templates);

        User writer = user(1L);
        when(writer.getLoginId()).thenReturn("writer");
        when(writer.getUserName()).thenReturn("작성자");
        User first = user(2L);
        User second = user(3L);
        User reference = user(4L);
        Map<Long, User> byId = Map.of(2L, first, 3L, second, 4L, reference);
        when(users.findByLoginId("writer")).thenReturn(java.util.Optional.of(writer));
        when(users.findAllByUserIdIn(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().map(byId::get).toList();
        });
        LocalDate date = LocalDate.of(2026, 9, 25);
        when(policy.plan(writer, date, date, LeavePeriod.FULL)).thenReturn(new LeaveRequestPolicy.Plan(
                List.of(new LeaveRequestPolicy.Day(date, LeavePeriod.FULL)), BigDecimal.ONE));
        AnnualLeaveBalance balance = AnnualLeaveBalance.grant(writer, 2026, new BigDecimal("15.0"));
        when(balances.findByUser_UserIdAndLeaveYear(1L, 2026)).thenReturn(java.util.Optional.of(balance));
        when(requests.sumPendingDays("writer", 2026, ApprovalStatus.IN_PROGRESS)).thenReturn(BigDecimal.ZERO);
        when(days.findConflictingCandidates(eq(1L), eq(date), eq(date), any())).thenReturn(List.of());
        ApprovalDocumentType type = mock(ApprovalDocumentType.class);
        when(type.getBehaviorType()).thenReturn(DocumentBehaviorType.LEAVE_REQUEST);
        when(documentTypes.requireSystemType("LEAVE_REQUEST", DocumentBehaviorType.LEAVE_REQUEST)).thenReturn(type);
        when(approvals.save(any())).thenAnswer(invocation -> {
            Approval approval = invocation.getArgument(0);
            ReflectionTestUtils.setField(approval, "approvalId", 10L);
            return approval;
        });
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Long approvalId = service.submit("writer", new LeaveRequestDocumentInput(date, date,
                LeavePeriod.FULL, "휴식", null, List.of(2L, 3L), List.of(4L), null, null), List.of());

        assertThat(approvalId).isEqualTo(10L);
        verify(lines).saveAll(argThat(saved -> {
            List<? extends ApprovalLine> approvalLines = StreamSupport.stream(saved.spliterator(), false).toList();
            return approvalLines.size() == 2
                    && approvalLines.get(0).getApprovalStatus() == ApprovalLineStatus.PENDING
                    && approvalLines.get(1).getApprovalStatus() == ApprovalLineStatus.WAITING;
        }));
        verify(ccs).saveAll(any());
        verify(notifications).onSubmitted(any(Approval.class), eq(first), eq(List.of(reference)));
        verify(templates, never()).validateSubmission(anyString(), anyLong(), anyLong());
    }

    private User user(Long id) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(id);
        return user;
    }
}
