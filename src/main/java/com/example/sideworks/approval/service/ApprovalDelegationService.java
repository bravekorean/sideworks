package com.example.sideworks.approval.service;

import com.example.sideworks.approval.dto.ApprovalDelegationResponse;
import com.example.sideworks.approval.dto.ApprovalDelegationSaveRequest;
import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalDelegation;
import com.example.sideworks.approval.entity.ApprovalLine;
import com.example.sideworks.approval.entity.DocumentBehaviorType;
import com.example.sideworks.approval.repository.ApprovalCcRepository;
import com.example.sideworks.approval.repository.ApprovalDelegationRepository;
import com.example.sideworks.approval.repository.ApprovalLineRepository;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalDelegationService {
    private final ApprovalDelegationRepository delegations;
    private final UserRepository users;
    private final ApprovalLineRepository lines;
    private final ApprovalCcRepository ccs;
    private final Clock clock;

    @Transactional
    public ApprovalDelegationResponse create(String loginId, ApprovalDelegationSaveRequest request) {
        User actor = findActor(loginId);
        if (request == null || request.delegatorId() == null || request.delegateeId() == null
                || request.startDate() == null || request.endDate() == null
                || request.startDate().isAfter(request.endDate())
                || request.endDate().isBefore(LocalDate.now(clock))
                || Objects.equals(request.delegatorId(), request.delegateeId())) {
            throw new BusinessException(ErrorCode.DELEGATION_INVALID);
        }
        requireManager(actor, request.delegatorId());
        User delegator = users.lockDelegator(request.delegatorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User delegatee = users.findById(request.delegateeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (delegatee.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.DELEGATION_INVALID);
        }
        if (!delegations.findOverlappingForUpdate(
                delegator.getUserId(), request.startDate(), request.endDate()).isEmpty()) {
            throw new BusinessException(ErrorCode.DELEGATION_OVERLAP);
        }
        return ApprovalDelegationResponse.from(delegations.save(
                ApprovalDelegation.create(delegator, delegatee, request.startDate(), request.endDate(), actor)));
    }

    @Transactional
    public void cancel(String loginId, Long delegationId) {
        User actor = findActor(loginId);
        ApprovalDelegation delegation = delegations.findById(delegationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELEGATION_NOT_FOUND));
        Long delegatorId = delegation.getDelegator().getUserId();
        requireManager(actor, delegatorId);
        users.lockDelegator(delegatorId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (delegations.cancelIfActive(delegationId, LocalDateTime.now(clock)) == 0) {
            throw new BusinessException(ErrorCode.DELEGATION_CANCELED);
        }
    }

    public List<ApprovalDelegationResponse> list(String loginId, Long delegatorId) {
        User actor = findActor(loginId);
        Long targetId = delegatorId == null ? actor.getUserId() : delegatorId;
        requireManager(actor, targetId);
        return delegations.findByDelegator_UserIdOrderByStartDateDescApprovalDelegationIdDesc(targetId)
                .stream().map(ApprovalDelegationResponse::from).toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assignFirstStep(Approval approval, List<ApprovalLine> approvalLines, List<User> ccUsers) {
        if (approvalLines.isEmpty()) return;
        assign(approval, approvalLines.getFirst(), approvalLines,
                ccUsers.stream().map(User::getUserId).toList());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assignNextStep(Approval approval, ApprovalLine nextLine) {
        Optional<User> delegatee = activeDelegate(nextLine.getApprover().getUserId());
        if (delegatee.isEmpty()) return;
        List<ApprovalLine> allLines = lines.findAllByApproval_ApprovalIdOrderByApprovalStepAsc(approval.getApprovalId());
        List<Long> ccIds = ccs.findByApproval_ApprovalId(approval.getApprovalId()).stream()
                .map(cc -> cc.getCcUser().getUserId()).toList();
        assignIfNoConflict(approval, nextLine, allLines, ccIds, delegatee.get());
    }

    private void assign(Approval approval, ApprovalLine target, List<ApprovalLine> allLines, List<Long> ccIds) {
        activeDelegate(target.getApprover().getUserId())
                .ifPresent(delegatee -> assignIfNoConflict(approval, target, allLines, ccIds, delegatee));
    }

    private void assignIfNoConflict(Approval approval, ApprovalLine target, List<ApprovalLine> allLines,
                                    List<Long> ccIds, User delegatee) {
        Long delegateeId = delegatee.getUserId();
        if (approval.getDocumentType().getBehaviorType() == DocumentBehaviorType.ATTENDANCE_CORRECTION
                && delegatee.getUserRole() != UserRole.HR_MANAGER
                && delegatee.getUserRole() != UserRole.SUPER_ADMIN) return;
        if (Objects.equals(approval.getWriter().getUserId(), delegateeId) || ccIds.contains(delegateeId)
                || allLines.stream().anyMatch(line -> line != target &&
                (Objects.equals(line.getApprover().getUserId(), delegateeId)
                        || (line.getOriginalApprover() != null
                        && Objects.equals(line.getOriginalApprover().getUserId(), delegateeId))))) return;
        target.assignDelegate(delegatee);
    }

    private Optional<User> activeDelegate(Long delegatorId) {
        LocalDate today = LocalDate.now(clock);
        List<ApprovalDelegation> active = delegations
                .findByDelegator_UserIdAndCanceledAtIsNullAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        delegatorId, today, today);
        if (active.size() > 1) throw new BusinessException(ErrorCode.DELEGATION_OVERLAP);
        if (active.isEmpty() || active.getFirst().getDelegatee().getStatus() != UserStatus.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(active.getFirst().getDelegatee());
    }

    private User findActor(String loginId) {
        return users.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void requireManager(User actor, Long delegatorId) {
        if (actor.getUserRole() != UserRole.SUPER_ADMIN && !Objects.equals(actor.getUserId(), delegatorId)) {
            throw new BusinessException(ErrorCode.DELEGATION_FORBIDDEN);
        }
    }
}
