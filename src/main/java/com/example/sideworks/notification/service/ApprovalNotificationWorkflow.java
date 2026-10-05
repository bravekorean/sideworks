package com.example.sideworks.notification.service;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalLine;
import com.example.sideworks.approval.repository.ApprovalCcRepository;
import com.example.sideworks.approval.repository.ApprovalLineRepository;
import com.example.sideworks.notification.entity.NotificationType;
import com.example.sideworks.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApprovalNotificationWorkflow {
    private final NotificationService notifications;
    private final ApprovalLineRepository lines;
    private final ApprovalCcRepository ccs;

    @Transactional(propagation = Propagation.MANDATORY)
    public void onSubmitted(Approval approval, User firstApprover, List<User> ccUsers) {
        Map<Long, Recipient> recipients = new LinkedHashMap<>();
        add(recipients, firstApprover, NotificationType.APPROVAL_REQUEST);
        ccUsers.forEach(cc -> add(recipients, cc, NotificationType.APPROVAL_REFERENCE));
        record(approval, "SUBMITTED", "새 결재 문서", recipients);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onApproved(Approval approval, int processedStep) {
        if (approval.isInProgress()) {
            ApprovalLine next = lines.findByApproval_ApprovalIdAndApprovalStep(
                    approval.getApprovalId(), approval.getCurrentStep()).orElseThrow();
            record(approval, "STEP_APPROVED:" + processedStep, "결재 요청",
                    Map.of(next.getApprover().getUserId(),
                            new Recipient(next.getApprover(), NotificationType.APPROVAL_REQUEST)));
            return;
        }
        Map<Long, Recipient> recipients = new LinkedHashMap<>();
        add(recipients, approval.getWriter(), NotificationType.APPROVAL_COMPLETED);
        addCcs(approval, recipients, NotificationType.APPROVAL_COMPLETED);
        record(approval, "FINAL_APPROVED", "결재 완료", recipients);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onRejected(Approval approval) {
        Map<Long, Recipient> recipients = new LinkedHashMap<>();
        add(recipients, approval.getWriter(), NotificationType.APPROVAL_REJECTED);
        addCcs(approval, recipients, NotificationType.APPROVAL_REJECTED);
        record(approval, "REJECTED", "결재 반려", recipients);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onCanceled(Approval approval, User currentApprover) {
        Map<Long, Recipient> recipients = new LinkedHashMap<>();
        add(recipients, currentApprover, NotificationType.APPROVAL_CANCELED);
        addCcs(approval, recipients, NotificationType.APPROVAL_CANCELED);
        record(approval, "CANCELED", "상신 취소", recipients);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onTerminated(Approval approval) {
        Map<Long, Recipient> recipients = new LinkedHashMap<>();
        add(recipients, approval.getWriter(), NotificationType.APPROVAL_TERMINATED);
        lines.findAllByApproval_ApprovalIdOrderByApprovalStepAsc(approval.getApprovalId())
                .forEach(line -> add(recipients, line.getApprover(), NotificationType.APPROVAL_TERMINATED));
        addCcs(approval, recipients, NotificationType.APPROVAL_TERMINATED);
        record(approval, "TERMINATED", "결재 강제 종료", recipients);
    }

    private void addCcs(Approval approval, Map<Long, Recipient> recipients, NotificationType type) {
        ccs.findByApproval_ApprovalId(approval.getApprovalId())
                .forEach(cc -> add(recipients, cc.getCcUser(), type));
    }

    private void add(Map<Long, Recipient> recipients, User user, NotificationType type) {
        recipients.putIfAbsent(user.getUserId(), new Recipient(user, type));
    }

    private void record(Approval approval, String eventKey, String title, Map<Long, Recipient> recipients) {
        String message = approval.getTitle().length() > 500
                ? approval.getTitle().substring(0, 500) : approval.getTitle();
        recipients.values().forEach(recipient -> notifications.record(approval, recipient.user(),
                recipient.type(), eventKey,
                recipient.type() == NotificationType.APPROVAL_REFERENCE ? "참조 문서" : title, message));
    }

    private record Recipient(User user, NotificationType type) {}
}
