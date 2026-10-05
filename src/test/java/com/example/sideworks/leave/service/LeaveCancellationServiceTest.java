package com.example.sideworks.leave.service;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.leave.entity.AnnualLeaveBalance;
import com.example.sideworks.leave.entity.AnnualLeaveHistory;
import com.example.sideworks.leave.entity.LeaveCancellation;
import com.example.sideworks.leave.entity.LeavePeriod;
import com.example.sideworks.leave.entity.LeaveRequest;
import com.example.sideworks.leave.entity.LeaveRequestDay;
import com.example.sideworks.leave.repository.AnnualLeaveHistoryRepository;
import com.example.sideworks.leave.repository.LeaveCancellationRepository;
import com.example.sideworks.leave.repository.LeaveRequestDayRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveCancellationServiceTest {
    @Mock UserRepository users;
    @Mock LeaveRequestDayRepository days;
    @Mock LeaveCancellationRepository cancellations;
    @Mock AnnualLeaveHistoryRepository histories;

    private LeaveCancellationService service() {
        return new LeaveCancellationService(users, null, days, cancellations, histories,
                null, null, null, null, null, null, null, null,
                Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneId.of("Asia/Seoul")),
                org.mockito.Mockito.mock(com.example.sideworks.notification.service.ApprovalNotificationWorkflow.class),
                org.mockito.Mockito.mock(com.example.sideworks.approval.service.ApprovalTemplateService.class));
    }

    @Test
    void 취소_최종_승인에서_원래_연도_잔액과_RESTORE_이력을_남긴다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(2L);
        User actor = mock(User.class);
        when(actor.getUserId()).thenReturn(3L);
        AnnualLeaveBalance balance = AnnualLeaveBalance.grant(owner, 2026, new BigDecimal("15.0"));
        balance.use(new BigDecimal("1.0"));
        Approval originalApproval = mock(Approval.class);
        when(originalApproval.getApprovalStatus()).thenReturn(ApprovalStatus.APPROVED);
        LeaveRequest request = LeaveRequest.create(owner, originalApproval, balance, new BigDecimal("1.0"), "휴식");
        ReflectionTestUtils.setField(request, "leaveRequestId", 10L);
        Approval cancellationApproval = mock(Approval.class);
        when(cancellationApproval.getApprovalId()).thenReturn(11L);
        LeaveCancellation cancellation = LeaveCancellation.create(request, cancellationApproval, "일정 변경");
        ReflectionTestUtils.setField(cancellation, "leaveCancellationId", 20L);
        when(cancellations.findByApproval_ApprovalId(11L)).thenReturn(Optional.of(cancellation));
        when(users.lockLeaveOwnerById(2L)).thenReturn(Optional.of(owner));
        when(days.findByRequest_LeaveRequestIdOrderByLeaveDateAsc(10L)).thenReturn(List.of(
                LeaveRequestDay.create(request, LocalDate.of(2026, 9, 25), LeavePeriod.FULL)));

        service().onDecision(cancellationApproval, actor, true);

        assertThat(balance.getRemainingDays()).isEqualByComparingTo("15.0");
        assertThat(request.isCanceled()).isTrue();
        assertThat(cancellation.getPendingLeaveRequestId()).isNull();
        var saved = org.mockito.ArgumentCaptor.forClass(AnnualLeaveHistory.class);
        verify(histories).save(saved.capture());
        assertThat(saved.getValue().getSourceKey()).isEqualTo("RESTORE:20");
        assertThat(saved.getValue().getDaysDelta()).isEqualByComparingTo("1.0");
    }

    @Test
    void 휴가_시작일이_지나면_취소_승인을_막는다() {
        User owner = mock(User.class);
        when(owner.getUserId()).thenReturn(2L);
        Approval originalApproval = mock(Approval.class);
        when(originalApproval.getApprovalStatus()).thenReturn(ApprovalStatus.APPROVED);
        AnnualLeaveBalance balance = AnnualLeaveBalance.grant(owner, 2026, new BigDecimal("15.0"));
        LeaveRequest request = LeaveRequest.create(owner, originalApproval, balance, new BigDecimal("1.0"), "휴식");
        ReflectionTestUtils.setField(request, "leaveRequestId", 10L);
        Approval cancellationApproval = mock(Approval.class);
        when(cancellationApproval.getApprovalId()).thenReturn(11L);
        LeaveCancellation cancellation = LeaveCancellation.create(request, cancellationApproval, "일정 변경");
        when(cancellations.findByApproval_ApprovalId(11L)).thenReturn(Optional.of(cancellation));
        when(users.lockLeaveOwnerById(2L)).thenReturn(Optional.of(owner));
        when(days.findByRequest_LeaveRequestIdOrderByLeaveDateAsc(10L)).thenReturn(List.of(
                LeaveRequestDay.create(request, LocalDate.of(2026, 9, 23), LeavePeriod.FULL)));

        assertThatThrownBy(() -> service().onDecision(cancellationApproval, mock(User.class), true))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.LEAVE_CANCELLATION_NOT_ALLOWED));
        assertThat(balance.getRemainingDays()).isEqualByComparingTo("15.0");
        verifyNoInteractions(histories);
    }

    @Test
    void 상신_취소는_원본을_유지하고_대기_표시만_해제한다() {
        LeaveCancellation cancellation = mock(LeaveCancellation.class);
        when(cancellations.findByApproval_ApprovalId(11L)).thenReturn(Optional.of(cancellation));

        service().onCancel(11L);

        verify(cancellation).finish();
        verifyNoInteractions(histories, days);
    }
}
