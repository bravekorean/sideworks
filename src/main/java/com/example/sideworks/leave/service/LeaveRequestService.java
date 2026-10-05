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
import com.example.sideworks.leave.dto.LeaveRequestDocumentInput;
import com.example.sideworks.leave.entity.AnnualLeaveBalance;
import com.example.sideworks.leave.entity.AnnualLeaveHistory;
import com.example.sideworks.leave.entity.LeaveRequest;
import com.example.sideworks.leave.entity.LeaveRequestDay;
import com.example.sideworks.leave.policy.LeaveRequestPolicy;
import com.example.sideworks.leave.repository.AnnualLeaveBalanceRepository;
import com.example.sideworks.leave.repository.AnnualLeaveHistoryRepository;
import com.example.sideworks.leave.repository.LeaveRequestDayRepository;
import com.example.sideworks.leave.repository.LeaveRequestRepository;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import com.example.sideworks.notification.service.ApprovalNotificationWorkflow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveRequestService {
    private final UserRepository users;
    private final AnnualLeaveBalanceService balanceService;
    private final AnnualLeaveBalanceRepository balances;
    private final AnnualLeaveHistoryRepository histories;
    private final LeaveRequestRepository requests;
    private final LeaveRequestDayRepository days;
    private final LeaveRequestPolicy policy;
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

    @Transactional
    public Long submit(String loginId, LeaveRequestDocumentInput input, List<MultipartFile> files) {
        validateInput(input);
        balanceService.getMyBalance(loginId, input.startDate().getYear()); // 사용자 행 잠금과 최초 부여
        User user = users.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        LeaveRequestPolicy.Plan plan = policy.plan(user, input.startDate(), input.endDate(), input.period());
        AnnualLeaveBalance balance = balances.findByUser_UserIdAndLeaveYear(user.getUserId(), input.startDate().getYear())
                .orElseThrow(() -> new BusinessException(ErrorCode.LEAVE_YEAR_NOT_ELIGIBLE));
        requireAvailable(user, balance, plan.totalDays(), null);
        requireNoOverlap(user.getUserId(), plan.days(), null);

        List<Long> approverIds = input.effectiveApproverIds();
        List<Long> ccIds = input.effectiveCcUserIds();
        submissionValidator.validateParticipantIds(approverIds, ccIds);
        validateTemplateSelection(loginId, input.approverId(), input.approverIds(),
                input.templateId(), input.templateVersion());
        List<User> approvers = ApprovalParticipantResolver.findInOrder(users, approverIds, ErrorCode.INVALID_APPROVER);
        List<User> ccUsers = ApprovalParticipantResolver.findInOrder(users, ccIds, ErrorCode.INVALID_CC_USER);
        String title = "[휴가 신청] " + user.getUserName() + " / " + input.startDate() + "~" + input.endDate();
        String content = "휴가 신청\n기간: " + input.startDate() + " ~ " + input.endDate()
                + "\n단위: " + input.period() + "\n신청 일수: " + plan.totalDays()
                + "\n사유: " + input.reason().trim();
        Approval approval = approvals.save(Approval.createDraft(user, title, content,
                documentTypes.requireSystemType("LEAVE_REQUEST", DocumentBehaviorType.LEAVE_REQUEST)));
        submissionValidator.validateDocument(approval);
        submissionValidator.validateParticipants(approval, approvers, ccUsers);
        if (files != null && !files.isEmpty()) attachments.upload(approval.getApprovalId(), loginId, files);
        approval.submit(LocalDateTime.now(clock));
        var approvalLines = submissionFactory.createLines(approval, approvers, ccUsers);
        lines.saveAll(approvalLines);
        if (!ccUsers.isEmpty()) approvalCcs.saveAll(submissionFactory.createCcs(approval, ccUsers));
        approvalHistories.save(submissionFactory.createHistory(approval));

        LeaveRequest request = requests.save(LeaveRequest.create(user, approval, balance,
                plan.totalDays(), input.reason().trim()));
        days.saveAll(plan.days().stream()
                .map(day -> LeaveRequestDay.create(request, day.date(), day.period())).toList());
        notificationWorkflow.onSubmitted(approval, approvalLines.getFirst().getApprover(), ccUsers);
        return approval.getApprovalId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onDecision(Approval approval, User actor, boolean approved) {
        requests.findByApproval_ApprovalId(approval.getApprovalId()).ifPresent(request -> {
            if (!approved) return;
            users.lockLeaveOwnerById(request.getUser().getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            AnnualLeaveBalance balance = request.getBalance();
            requireAvailable(request.getUser(), balance, request.getTotalDays(), request.getLeaveRequestId());
            List<LeaveRequestDay> requestedDays = days.findByRequest_LeaveRequestIdOrderByLeaveDateAsc(
                    request.getLeaveRequestId());
            if (requestedDays.isEmpty() || !requestedDays.getFirst().getLeaveDate().isAfter(LocalDate.now(clock))) {
                throw new BusinessException(ErrorCode.LEAVE_INVALID_DATE);
            }
            requireNoOverlap(request.getUser().getUserId(), requestedDays.stream()
                    .map(day -> new LeaveRequestPolicy.Day(day.getLeaveDate(), day.getLeavePeriod())).toList(),
                    request.getLeaveRequestId());
            balance.use(request.getTotalDays());
            histories.save(AnnualLeaveHistory.use(balance, request, actor));
        });
    }

    private void requireAvailable(User user, AnnualLeaveBalance balance, BigDecimal requested, Long selfRequestId) {
        BigDecimal pending = requests.sumPendingDays(user.getLoginId(), balance.getLeaveYear(), ApprovalStatus.IN_PROGRESS);
        if (pending == null) pending = BigDecimal.ZERO;
        if (selfRequestId != null) pending = pending.subtract(requested);
        if (balance.getRemainingDays().subtract(pending).compareTo(requested) < 0) {
            throw new BusinessException(ErrorCode.LEAVE_INSUFFICIENT_BALANCE);
        }
    }

    private void requireNoOverlap(Long userId, List<LeaveRequestPolicy.Day> requested, Long selfRequestId) {
        Map<java.time.LocalDate, LeaveRequestPolicy.Day> byDate = requested.stream()
                .collect(Collectors.toMap(LeaveRequestPolicy.Day::date, Function.identity()));
        var existing = days.findConflictingCandidates(userId, requested.getFirst().date(),
                requested.getLast().date(), List.of(ApprovalStatus.IN_PROGRESS, ApprovalStatus.APPROVED));
        for (LeaveRequestDay day : existing) {
            if (selfRequestId != null && selfRequestId.equals(day.getRequest().getLeaveRequestId())) continue;
            LeaveRequestPolicy.Day current = byDate.get(day.getLeaveDate());
            if (current != null && current.period().overlaps(day.getLeavePeriod())) {
                throw new BusinessException(ErrorCode.LEAVE_DATE_CONFLICT);
            }
        }
    }

    private void validateInput(LeaveRequestDocumentInput input) {
        if (input == null || input.startDate() == null || input.endDate() == null
                || input.period() == null || input.reason() == null
                || input.reason().isBlank() || input.reason().trim().length() > 1000) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private void validateTemplateSelection(String loginId, Long legacyApproverId, List<Long> approverIds,
                                           Long templateId, Long templateVersion) {
        if (legacyApproverId != null && approverIds != null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if ((templateId == null) != (templateVersion == null)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (templateId != null) templates.validateSubmission(loginId, templateId, templateVersion);
    }
}
