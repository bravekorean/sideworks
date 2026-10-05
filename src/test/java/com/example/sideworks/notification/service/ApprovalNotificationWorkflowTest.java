package com.example.sideworks.notification.service;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalCc;
import com.example.sideworks.approval.entity.ApprovalLine;
import com.example.sideworks.approval.repository.ApprovalCcRepository;
import com.example.sideworks.approval.repository.ApprovalLineRepository;
import com.example.sideworks.notification.entity.NotificationType;
import com.example.sideworks.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalNotificationWorkflowTest {
    @Mock NotificationService notifications;
    @Mock ApprovalLineRepository lines;
    @Mock ApprovalCcRepository ccs;
    ApprovalNotificationWorkflow workflow;

    @BeforeEach
    void setUp() {
        workflow = new ApprovalNotificationWorkflow(notifications, lines, ccs);
    }

    @Test
    void 상신시_첫결재자와_참조자에게_각각_한번만_알린다() {
        Approval approval = approval();
        User approver = user(2L);
        User cc = user(3L);

        workflow.onSubmitted(approval, approver, List.of(approver, cc));

        verify(notifications).record(eq(approval), eq(approver), eq(NotificationType.APPROVAL_REQUEST),
                eq("SUBMITTED"), anyString(), anyString());
        verify(notifications).record(eq(approval), eq(cc), eq(NotificationType.APPROVAL_REFERENCE),
                eq("SUBMITTED"), anyString(), anyString());
        verify(notifications, times(2)).record(eq(approval), any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void 중간승인시_다음_결재자에게만_알린다() {
        Approval approval = approval();
        User nextApprover = user(4L);
        ApprovalLine next = mock(ApprovalLine.class);
        when(approval.isInProgress()).thenReturn(true);
        when(approval.getCurrentStep()).thenReturn(2);
        when(lines.findByApproval_ApprovalIdAndApprovalStep(10L, 2)).thenReturn(Optional.of(next));
        when(next.getApprover()).thenReturn(nextApprover);

        workflow.onApproved(approval, 1);

        verify(notifications).record(eq(approval), eq(nextApprover), eq(NotificationType.APPROVAL_REQUEST),
                eq("STEP_APPROVED:1"), anyString(), anyString());
        verifyNoInteractions(ccs);
    }

    @Test
    void 최종승인시_작성자와_참조자에게_알린다() {
        Approval approval = approval();
        User writer = user(1L);
        User cc = user(3L);
        ApprovalCc ccRow = mock(ApprovalCc.class);
        when(approval.getWriter()).thenReturn(writer);
        when(ccs.findByApproval_ApprovalId(10L)).thenReturn(List.of(ccRow));
        when(ccRow.getCcUser()).thenReturn(cc);

        workflow.onApproved(approval, 1);

        verify(notifications).record(eq(approval), eq(writer), eq(NotificationType.APPROVAL_COMPLETED),
                eq("FINAL_APPROVED"), anyString(), anyString());
        verify(notifications).record(eq(approval), eq(cc), eq(NotificationType.APPROVAL_COMPLETED),
                eq("FINAL_APPROVED"), anyString(), anyString());
    }

    @Test
    void 상신취소시_결재자와_같은_참조자는_중복알림을_받지_않는다() {
        Approval approval = approval();
        User approverAndCc = user(2L);
        ApprovalCc ccRow = mock(ApprovalCc.class);
        when(ccs.findByApproval_ApprovalId(10L)).thenReturn(List.of(ccRow));
        when(ccRow.getCcUser()).thenReturn(approverAndCc);

        workflow.onCanceled(approval, approverAndCc);

        verify(notifications, times(1)).record(eq(approval), eq(approverAndCc),
                eq(NotificationType.APPROVAL_CANCELED), eq("CANCELED"), anyString(), anyString());
    }

    private Approval approval() {
        Approval approval = mock(Approval.class);
        lenient().when(approval.getApprovalId()).thenReturn(10L);
        when(approval.getTitle()).thenReturn("휴가 신청");
        return approval;
    }

    private User user(Long id) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(id);
        return user;
    }
}
