package com.example.sideworks.leave.service;

import com.example.sideworks.approval.attachment.service.ApprovalAttachmentService;
import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.approval.entity.DocumentBehaviorType;
import com.example.sideworks.approval.factory.ApprovalSubmissionFactory;
import com.example.sideworks.approval.repository.ApprovalHistoryRepository;
import com.example.sideworks.approval.repository.ApprovalCcRepository;
import com.example.sideworks.approval.repository.ApprovalLineRepository;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.approval.service.ApprovalDocumentTypeService;
import com.example.sideworks.approval.service.ApprovalParticipantResolver;
import com.example.sideworks.approval.service.ApprovalTemplateService;
import com.example.sideworks.approval.validator.ApprovalSubmissionValidator;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.leave.dto.ApprovedLeaveResponse;
import com.example.sideworks.leave.dto.LeaveCancellationDocumentInput;
import com.example.sideworks.leave.entity.AnnualLeaveBalance;
import com.example.sideworks.leave.entity.AnnualLeaveHistory;
import com.example.sideworks.leave.entity.LeaveCancellation;
import com.example.sideworks.leave.entity.LeaveRequest;
import com.example.sideworks.leave.entity.LeaveRequestDay;
import com.example.sideworks.leave.repository.AnnualLeaveHistoryRepository;
import com.example.sideworks.leave.repository.LeaveCancellationRepository;
import com.example.sideworks.leave.repository.LeaveRequestDayRepository;
import com.example.sideworks.leave.repository.LeaveRequestRepository;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import com.example.sideworks.notification.service.ApprovalNotificationWorkflow;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveCancellationService {
    private final UserRepository users;
    private final LeaveRequestRepository requests;
    private final LeaveRequestDayRepository days;
    private final LeaveCancellationRepository cancellations;
    private final AnnualLeaveHistoryRepository histories;
    private final ApprovalRepository approvals;
    private final ApprovalLineRepository lines;
    private final ApprovalCcRepository approvalCcs;
    private final ApprovalHistoryRepository approvalHistories;
    private final ApprovalSubmissionFactory submissionFactory;
    private final ApprovalSubmissionValidator submissionValidator;
    private final ApprovalDocumentTypeService documentTypes;
    private final ApprovalAttachmentService attachments;
    private final Clock clock;
    private final ApprovalNotificationWorkflow notificationWorkflow;
    private final ApprovalTemplateService templates;

    public List<ApprovedLeaveResponse> getCancelable(String loginId) {
        User user = users.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        return requests.findActiveApproved(user.getUserId(), ApprovalStatus.APPROVED).stream()
                .filter(request -> !cancellations.existsByPendingLeaveRequestId(request.getLeaveRequestId()))
                .map(request -> {
                    List<LeaveRequestDay> entries = days.findByRequest_LeaveRequestIdOrderByLeaveDateAsc(
                            request.getLeaveRequestId());
                    if (entries.isEmpty() || !entries.getFirst().getLeaveDate().isAfter(LocalDate.now(clock))) return null;
                    return new ApprovedLeaveResponse(request.getLeaveRequestId(), entries.getFirst().getLeaveDate(),
                            entries.getLast().getLeaveDate(), entries.getFirst().getLeavePeriod(), request.getTotalDays());
                }).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional
    public Long submit(String loginId, LeaveCancellationDocumentInput input, List<MultipartFile> files) {
        if (input == null || input.leaveRequestId() == null
                || input.reason() == null || input.reason().isBlank() || input.reason().trim().length() > 1000) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        User user = users.lockLeaveOwner(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        LeaveRequest original = requests.findById(input.leaveRequestId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LEAVE_REQUEST_NOT_FOUND));
        if (!original.getUser().getUserId().equals(user.getUserId())) {
            throw new BusinessException(ErrorCode.LEAVE_REQUEST_NOT_FOUND);
        }
        List<LeaveRequestDay> entries = requireCancelable(original);
        if (cancellations.existsByPendingLeaveRequestId(original.getLeaveRequestId())) {
            throw new BusinessException(ErrorCode.LEAVE_CANCELLATION_PENDING);
        }
        List<Long> approverIds = input.effectiveApproverIds();
        List<Long> ccIds = input.effectiveCcUserIds();
        submissionValidator.validateParticipantIds(approverIds, ccIds);
        if (input.approverId() != null && input.approverIds() != null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if ((input.templateId() == null) != (input.templateVersion() == null)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (input.templateId() != null) templates.validateSubmission(loginId, input.templateId(), input.templateVersion());
        List<User> approvers = ApprovalParticipantResolver.findInOrder(users, approverIds, ErrorCode.INVALID_APPROVER);
        List<User> ccUsers = ApprovalParticipantResolver.findInOrder(users, ccIds, ErrorCode.INVALID_CC_USER);
        String title = "[휴가 취소] " + user.getUserName() + " / " + entries.getFirst().getLeaveDate()
                + "~" + entries.getLast().getLeaveDate();
        String content = "휴가 전체 취소 신청\n원 휴가 문서: #" + original.getApproval().getApprovalId()
                + "\n기간: " + entries.getFirst().getLeaveDate() + " ~ " + entries.getLast().getLeaveDate()
                + "\n취소 사유: " + input.reason().trim();
        Approval approval = approvals.save(Approval.createDraft(user, title, content,
                documentTypes.requireSystemType("LEAVE_CANCELLATION", DocumentBehaviorType.LEAVE_CANCELLATION)));
        submissionValidator.validateDocument(approval);
        submissionValidator.validateParticipants(approval, approvers, ccUsers);
        if (files != null && !files.isEmpty()) attachments.upload(approval.getApprovalId(), loginId, files);
        approval.submit(LocalDateTime.now(clock));
        var approvalLines = submissionFactory.createLines(approval, approvers, ccUsers);
        lines.saveAll(approvalLines);
        if (!ccUsers.isEmpty()) approvalCcs.saveAll(submissionFactory.createCcs(approval, ccUsers));
        approvalHistories.save(submissionFactory.createHistory(approval));
        try {
            cancellations.saveAndFlush(LeaveCancellation.create(original, approval, input.reason().trim()));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.LEAVE_CANCELLATION_PENDING);
        }
        notificationWorkflow.onSubmitted(approval, approvalLines.getFirst().getApprover(), ccUsers);
        return approval.getApprovalId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onDecision(Approval approval, User actor, boolean approved) {
        cancellations.findByApproval_ApprovalId(approval.getApprovalId()).ifPresent(cancellation -> {
            if (approved) {
                LeaveRequest original = cancellation.getRequest();
                users.lockLeaveOwnerById(original.getUser().getUserId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                requireCancelable(original);
                AnnualLeaveBalance balance = original.getBalance();
                balance.restore(original.getTotalDays());
                original.cancel(LocalDateTime.now(clock));
                histories.save(AnnualLeaveHistory.restore(balance, cancellation, actor));
            }
            cancellation.finish();
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onCancel(Long approvalId) {
        cancellations.findByApproval_ApprovalId(approvalId).ifPresent(LeaveCancellation::finish);
    }

    private List<LeaveRequestDay> requireCancelable(LeaveRequest original) {
        if (original.getApproval().getApprovalStatus() != ApprovalStatus.APPROVED || original.isCanceled()) {
            throw new BusinessException(ErrorCode.LEAVE_CANCELLATION_NOT_ALLOWED);
        }
        List<LeaveRequestDay> entries = days.findByRequest_LeaveRequestIdOrderByLeaveDateAsc(original.getLeaveRequestId());
        if (entries.isEmpty() || !entries.getFirst().getLeaveDate().isAfter(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.LEAVE_CANCELLATION_NOT_ALLOWED);
        }
        return entries;
    }
}
