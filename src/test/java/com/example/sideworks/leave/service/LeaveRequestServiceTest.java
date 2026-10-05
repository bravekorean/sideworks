package com.example.sideworks.leave.service;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.leave.dto.LeaveRequestDocumentInput;
import com.example.sideworks.leave.entity.AnnualLeaveBalance;
import com.example.sideworks.leave.entity.AnnualLeaveHistory;
import com.example.sideworks.leave.entity.LeavePeriod;
import com.example.sideworks.leave.entity.LeaveRequest;
import com.example.sideworks.leave.entity.LeaveRequestDay;
import com.example.sideworks.leave.policy.LeaveRequestPolicy;
import com.example.sideworks.leave.repository.AnnualLeaveBalanceRepository;
import com.example.sideworks.leave.repository.AnnualLeaveHistoryRepository;
import com.example.sideworks.leave.repository.LeaveRequestDayRepository;
import com.example.sideworks.leave.repository.LeaveRequestRepository;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTest {
    @Mock UserRepository users;
    @Mock AnnualLeaveBalanceService balanceService;
    @Mock AnnualLeaveBalanceRepository balances;
    @Mock AnnualLeaveHistoryRepository histories;
    @Mock LeaveRequestRepository requests;
    @Mock LeaveRequestDayRepository days;
    @Mock LeaveRequestPolicy policy;
    @Mock ApprovalRepository approvals;

    private LeaveRequestService service() {
        return new LeaveRequestService(users, balanceService, balances, histories, requests, days, policy,
                approvals, null, null, null, null, null, null, null,
                Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneId.of("Asia/Seoul")),
                org.mockito.Mockito.mock(com.example.sideworks.notification.service.ApprovalNotificationWorkflow.class),
                org.mockito.Mockito.mock(com.example.sideworks.approval.service.ApprovalTemplateService.class));
    }

    @Test
    void 승인_대기량을_제외한_잔액이_부족하면_상신하지_않는다() {
        User user = user(2L, "employee");
        AnnualLeaveBalance balance = AnnualLeaveBalance.grant(user, 2026, new BigDecimal("11.0"));
        LocalDate date = LocalDate.of(2026, 9, 25);
        LeaveRequestPolicy.Plan plan = new LeaveRequestPolicy.Plan(
                List.of(new LeaveRequestPolicy.Day(date, LeavePeriod.FULL)), new BigDecimal("1.0"));
        when(users.findByLoginId("employee")).thenReturn(Optional.of(user));
        when(policy.plan(eq(user), eq(date), eq(date), eq(LeavePeriod.FULL))).thenReturn(plan);
        when(balances.findByUser_UserIdAndLeaveYear(2L, 2026)).thenReturn(Optional.of(balance));
        when(requests.sumPendingDays("employee", 2026, ApprovalStatus.IN_PROGRESS))
                .thenReturn(new BigDecimal("10.5"));

        assertThatThrownBy(() -> service().submit("employee",
                new LeaveRequestDocumentInput(date, date, LeavePeriod.FULL, "휴식", 3L), List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.LEAVE_INSUFFICIENT_BALANCE));
        verifyNoInteractions(approvals);
    }

    @Test
    void 최종_승인에서만_잔액과_USE_이력을_변경한다() {
        User user = user(2L, "employee");
        User actor = user(3L, "approver");
        AnnualLeaveBalance balance = AnnualLeaveBalance.grant(user, 2026, new BigDecimal("15.0"));
        Approval approval = mock(Approval.class);
        when(approval.getApprovalId()).thenReturn(1L);
        LeaveRequest request = LeaveRequest.create(user, approval, balance, new BigDecimal("1.0"), "휴식");
        ReflectionTestUtils.setField(request, "leaveRequestId", 10L);
        LeaveRequestDay day = LeaveRequestDay.create(request, LocalDate.of(2026, 9, 25), LeavePeriod.FULL);
        when(requests.findByApproval_ApprovalId(1L)).thenReturn(Optional.of(request));
        when(users.lockLeaveOwnerById(2L)).thenReturn(Optional.of(user));
        when(requests.sumPendingDays("employee", 2026, ApprovalStatus.IN_PROGRESS))
                .thenReturn(new BigDecimal("1.0"));
        when(days.findByRequest_LeaveRequestIdOrderByLeaveDateAsc(10L)).thenReturn(List.of(day));
        when(days.findConflictingCandidates(eq(2L), any(), any(), any())).thenReturn(List.of(day));

        service().onDecision(approval, actor, true);

        assertThat(balance.getRemainingDays()).isEqualByComparingTo("14.0");
        var saved = org.mockito.ArgumentCaptor.forClass(AnnualLeaveHistory.class);
        verify(histories).save(saved.capture());
        assertThat(saved.getValue().getSourceKey()).isEqualTo("USE:10");
        assertThat(saved.getValue().getDaysDelta()).isEqualByComparingTo("-1.0");
    }

    @Test
    void 같은_날_겹치는_시간대가_있으면_상신하지_않는다() {
        User user = user(2L, "employee");
        AnnualLeaveBalance balance = AnnualLeaveBalance.grant(user, 2026, new BigDecimal("11.0"));
        LocalDate date = LocalDate.of(2026, 9, 25);
        LeaveRequestPolicy.Plan plan = new LeaveRequestPolicy.Plan(
                List.of(new LeaveRequestPolicy.Day(date, LeavePeriod.AM)), new BigDecimal("0.5"));
        when(users.findByLoginId("employee")).thenReturn(Optional.of(user));
        when(policy.plan(eq(user), eq(date), eq(date), eq(LeavePeriod.AM))).thenReturn(plan);
        when(balances.findByUser_UserIdAndLeaveYear(2L, 2026)).thenReturn(Optional.of(balance));
        LeaveRequest existing = mock(LeaveRequest.class);
        LeaveRequestDay candidate = LeaveRequestDay.create(existing, date, LeavePeriod.FULL);
        when(days.findConflictingCandidates(eq(2L), eq(date), eq(date), any()))
                .thenReturn(List.of(candidate));

        assertThatThrownBy(() -> service().submit("employee",
                new LeaveRequestDocumentInput(date, date, LeavePeriod.AM, "휴식", 3L), List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.LEAVE_DATE_CONFLICT));
        verifyNoInteractions(approvals);
    }

    @Test
    void 반려는_잔액을_바꾸지_않는다() {
        Approval approval = mock(Approval.class);
        when(approval.getApprovalId()).thenReturn(1L);
        when(requests.findByApproval_ApprovalId(1L)).thenReturn(Optional.of(mock(LeaveRequest.class)));

        service().onDecision(approval, mock(User.class), false);

        verifyNoInteractions(histories, balances);
        verify(users, never()).lockLeaveOwnerById(any());
    }

    private User user(Long id, String login) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(id);
        if ("employee".equals(login)) when(user.getLoginId()).thenReturn(login);
        return user;
    }
}
