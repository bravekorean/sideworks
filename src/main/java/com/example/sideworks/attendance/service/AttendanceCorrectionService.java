package com.example.sideworks.attendance.service;

import com.example.sideworks.approval.entity.*;
import com.example.sideworks.approval.attachment.service.ApprovalAttachmentService;
import com.example.sideworks.approval.factory.ApprovalSubmissionFactory;
import com.example.sideworks.approval.service.ApprovalDocumentTypeService;
import com.example.sideworks.approval.repository.*;
import com.example.sideworks.attendance.entity.*;
import com.example.sideworks.attendance.repository.*;
import com.example.sideworks.common.exception.*;
import com.example.sideworks.user.entity.*;
import com.example.sideworks.user.repository.UserRepository;
import com.example.sideworks.notification.service.ApprovalNotificationWorkflow;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class AttendanceCorrectionService {
    private final UserRepository users;
    private final AttendanceRepository attendances;
    private final AttendanceCorrectionRepository corrections;
    private final AttendanceChangeHistoryRepository histories;
    private final WorkPolicyRepository policies;
    private final ApprovalRepository approvals;
    private final ApprovalLineRepository lines;
    private final ApprovalHistoryRepository approvalHistories;
    private final ApprovalSubmissionFactory submissionFactory;
    private final Clock clock;
    private final ApprovalAttachmentService attachments;
    private final ApprovalDocumentTypeService documentTypes;
    private final ApprovalNotificationWorkflow notificationWorkflow;

    public record DocumentInput(String title, Input correction) {}

    public record Input(LocalDate date, LocalDateTime checkInAt, LocalDateTime checkOutAt, String reason,
                        Long approverId, Long expectedAttendanceId, Long expectedVersion) {}
    public record Approver(Long userId, String userName) {}
    public record Snapshot(Long attendanceId, Long version, LocalDateTime checkInAt, LocalDateTime checkOutAt) {
        static Snapshot from(Attendance a) {
            return a == null ? new Snapshot(null, null, null, null)
                    : new Snapshot(a.getAttendanceId(), a.getVersion(), a.getCheckInAt(), a.getCheckOutAt());
        }
    }
    public record History(Long id, Long actorId, Long approvalId, String source, LocalDateTime createdAt,
            LocalDateTime beforeCheckInAt, LocalDateTime beforeCheckOutAt, Boolean beforeLate, Boolean beforeEarlyLeave,
            LocalDateTime afterCheckInAt, LocalDateTime afterCheckOutAt, boolean afterLate, boolean afterEarlyLeave, String reason) {
        static History from(AttendanceChangeHistory h) {
            return new History(h.getAttendanceChangeHistoryId(), h.getActorId(), h.getApprovalId(), h.getSource(), h.getCreatedAt(),
                    h.getBeforeCheckInAt(), h.getBeforeCheckOutAt(), h.getBeforeLate(), h.getBeforeEarlyLeave(),
                    h.getAfterCheckInAt(), h.getAfterCheckOutAt(), h.isAfterLate(), h.isAfterEarlyLeave(), h.getReason());
        }
    }

    public List<Approver> approvers(String login) {
        User actor = activeUser(login);
        return users.findAllByStatusAndUserRoleInOrderByUserNameAsc(UserStatus.ACTIVE,
                        List.of(UserRole.HR_MANAGER, UserRole.SUPER_ADMIN)).stream()
                .filter(u -> !u.getUserId().equals(actor.getUserId()))
                .map(u -> new Approver(u.getUserId(), u.getUserName())).toList();
    }

    public Snapshot snapshot(String login, Long targetId, LocalDate date) {
        User target = readableTarget(login, targetId);
        validateDate(target, date);
        return Snapshot.from(attendances.findByUser_UserIdAndAttendanceDate(target.getUserId(), date).orElse(null));
    }

    public List<History> history(String login, Long targetId, LocalDate date) {
        User target = readableTarget(login, targetId);
        validateDate(target, date);
        return histories.findAllByUserIdAndAttendanceDateOrderByAttendanceChangeHistoryIdDesc(target.getUserId(), date)
                .stream().map(History::from).toList();
    }

    @Transactional
    public Long submit(String login, Input input) {
        return submitDocument(login, new DocumentInput(
                input == null ? null : "[근태 정정] " + input.date(), input), List.of());
    }

    @Transactional
    public Long submitDocument(String login, DocumentInput document, List<MultipartFile> files) {
        if (document == null || document.title() == null || document.title().isBlank()
                || document.title().trim().length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Input input = document.correction();
        User actor = activeUser(login);
        users.lockAttendanceOwner(actor.getUserId());
        validate(actor, input);
        if (input.approverId() == null) throw new BusinessException(ErrorCode.INVALID_APPROVER);
        User approver = users.findById(input.approverId()).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_APPROVER));
        if (!isHr(approver) || approver.getUserId().equals(actor.getUserId()))
            throw new BusinessException(ErrorCode.INVALID_APPROVER);
        if (corrections.existsByUser_UserIdAndPendingDate(actor.getUserId(), input.date()))
            throw new BusinessException(ErrorCode.ATTENDANCE_CORRECTION_PENDING);
        Attendance original = lockedRecord(actor.getUserId(), input.date());
        validateSnapshot(input, original);
        String content = "근태 정정 신청\n대상 날짜: " + input.date()
                + "\n기존 출근: " + display(original == null ? null : original.getCheckInAt())
                + "\n기존 퇴근: " + display(original == null ? null : original.getCheckOutAt())
                + "\n요청 출근: " + input.checkInAt() + "\n요청 퇴근: " + display(input.checkOutAt())
                + "\n사유: " + input.reason().trim();
        Approval approval = approvals.save(Approval.createDraft(actor, document.title().trim(), content,
                documentTypes.requireAttendanceCorrection()));
        // 첨부는 DRAFT에서 저장한다. 업로드·상신 중 실패하면 동일 트랜잭션의 문서와 파일을 롤백한다.
        if (files != null && !files.isEmpty()) {
            attachments.upload(approval.getApprovalId(), login, files);
        }
        approval.submit(LocalDateTime.now(clock));
        var approvalLines = submissionFactory.createLines(approval, List.of(approver), List.of());
        lines.saveAll(approvalLines);
        approvalHistories.save(submissionFactory.createHistory(approval));
        try {
            corrections.saveAndFlush(AttendanceCorrection.create(actor, approval, input.date(), original,
                    input.checkInAt(), input.checkOutAt(), input.reason().trim()));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ATTENDANCE_CORRECTION_PENDING);
        }
        notificationWorkflow.onSubmitted(approval, approvalLines.getFirst().getApprover(), List.of());
        return approval.getApprovalId();
    }

    // 기존 결재의 트랜잭션 안에서만 실행한다. 이후 결재 저장이 실패해도 근태·이력이 함께 롤백된다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void onDecision(Approval approval, User actor, boolean approved) {
        corrections.findByApproval_ApprovalId(approval.getApprovalId()).ifPresent(c -> {
            if (!isHr(actor) || actor.getUserId().equals(c.getUser().getUserId()))
                throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_FORBIDDEN);
            if (approved) {
                users.lockAttendanceOwner(c.getUser().getUserId());
                Attendance original = lockedRecord(c.getUser().getUserId(), c.getAttendanceDate());
                if (!c.matches(original)) throw new BusinessException(ErrorCode.ATTENDANCE_CORRECTION_STALE);
                apply(c.getUser(), actor, approval.getApprovalId(), c.getAttendanceDate(), original,
                        c.getRequestedCheckInAt(), c.getRequestedCheckOutAt(), c.getReason());
            }
            c.finish();
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void onCancel(Long approvalId) {
        corrections.findByApproval_ApprovalId(approvalId).ifPresent(AttendanceCorrection::finish);
    }

    @Transactional
    public Snapshot direct(String login, Long userId, Input input) {
        User actor = activeUser(login);
        if (actor.getUserRole() != UserRole.SUPER_ADMIN)
            throw new BusinessException(ErrorCode.ATTENDANCE_CHANGE_FORBIDDEN);
        User target = users.lockAttendanceOwner(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validate(target, input);
        Attendance original = lockedRecord(userId, input.date());
        validateSnapshot(input, original);
        Attendance changed = apply(target, actor, null, input.date(), original,
                input.checkInAt(), input.checkOutAt(), input.reason().trim());
        attendances.flush();
        return Snapshot.from(changed);
    }

    private Attendance apply(User target, User actor, Long approvalId, LocalDate date, Attendance original,
                             LocalDateTime checkIn, LocalDateTime checkOut, String reason) {
        AttendanceChangeHistory h = AttendanceChangeHistory.before(target.getUserId(), actor.getUserId(), approvalId,
                date, original, reason);
        Attendance changed = original;
        if (changed == null) {
            List<WorkPolicy> applicable = policies.findApplicablePolicies(WorkPolicyStatus.ACTIVE, date);
            if (applicable.isEmpty()) throw new BusinessException(ErrorCode.WORK_POLICY_NOT_CONFIGURED);
            if (applicable.size() != 1) throw new BusinessException(ErrorCode.WORK_POLICY_CONFIGURATION_CONFLICT);
            changed = Attendance.createCheckIn(target, applicable.getFirst(), checkIn);
        }
        changed.correct(checkIn, checkOut);
        attendances.saveAndFlush(changed);
        h.recordAfter(changed);
        histories.save(h);
        AttendanceAudit.afterCommit(approvalId == null ? "ATTENDANCE_DIRECT_CORRECTION" : "ATTENDANCE_CORRECTION_APPROVED",
                actor.getUserId(), target.getUserId());
        return changed;
    }

    private Attendance lockedRecord(Long userId, LocalDate date) {
        return attendances.findByUserIdAndAttendanceDateForUpdate(userId, date).orElse(null);
    }

    private User readableTarget(String login, Long targetId) {
        User actor = activeUser(login);
        if (targetId == null || targetId.equals(actor.getUserId())) return actor;
        // 상세 사유·이력은 부서장 집계 조회 권한으로 공개하지 않는다.
        if (!isHr(actor)) throw new BusinessException(ErrorCode.ATTENDANCE_VIEW_FORBIDDEN);
        return users.findById(targetId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private User activeUser(String login) {
        User user = users.findByLoginId(login).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        return user;
    }
    private boolean isHr(User user) {
        return user.getStatus() == UserStatus.ACTIVE
                && (user.getUserRole() == UserRole.HR_MANAGER || user.getUserRole() == UserRole.SUPER_ADMIN);
    }
    private void validate(User user, Input input) {
        if (input == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        validateDate(user, input.date());
        LocalDateTime now = LocalDateTime.now(clock);
        if (input.reason() == null || input.reason().isBlank() || input.reason().trim().length() > 1000
                || input.checkInAt() == null || !input.checkInAt().toLocalDate().equals(input.date())
                || input.checkInAt().isAfter(now)
                || (input.checkOutAt() != null && (!input.checkOutAt().toLocalDate().equals(input.date())
                || input.checkOutAt().isBefore(input.checkInAt()) || input.checkOutAt().isAfter(now))))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
    private void validateDate(User user, LocalDate date) {
        if (date == null || date.getYear() < 1900 || date.isAfter(LocalDate.now(clock))
                || (user.getHireDate() != null && date.isBefore(user.getHireDate())))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
    private void validateSnapshot(Input input, Attendance current) {
        if (current == null ? input.expectedAttendanceId() != null || input.expectedVersion() != null
                : !Objects.equals(input.expectedAttendanceId(), current.getAttendanceId())
                || !Objects.equals(input.expectedVersion(), current.getVersion()))
            throw new BusinessException(ErrorCode.ATTENDANCE_CORRECTION_STALE);
    }
    private String display(LocalDateTime value) { return value == null ? "미기록" : value.toString(); }
}
