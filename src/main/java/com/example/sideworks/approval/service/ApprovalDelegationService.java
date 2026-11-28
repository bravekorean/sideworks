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

    /**
     * 처리 API 기준의 실제 처리자를 정한다. 문서 행을 잠근 뒤, 잠금 이후 서버 시각으로 계산한 날짜를 넘겨야 한다.
     * 유효한 위임(취소되지 않음, 시작일 ≤ today ≤ 종료일, 대결자 ACTIVE)이 있고 충돌이 없으면 대결자, 아니면 원 결재자다.
     * 조회 경로의 같은 규칙은 ApprovalProcessorConditions(QueryDSL)에 있다. 두 구현은 ApprovalDelegationIntegrationTest로 맞춘다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public User resolveProcessor(Approval approval, ApprovalLine line, LocalDate today) {
        User original = line.getApprover();
        return activeDelegatee(original.getUserId(), today)
                .filter(delegatee -> !conflictsWithParticipants(approval, line, delegatee))
                .orElse(original);
    }

    // 대결자가 이 문서의 다른 역할을 이미 맡고 있으면 한 사람이 두 번 판단하게 되므로 원 결재자가 처리한다.
    private boolean conflictsWithParticipants(Approval approval, ApprovalLine target, User delegatee) {
        Long delegateeId = delegatee.getUserId();
        if (approval.getDocumentType() != null
                && approval.getDocumentType().getBehaviorType() == DocumentBehaviorType.ATTENDANCE_CORRECTION
                && delegatee.getUserRole() != UserRole.HR_MANAGER
                && delegatee.getUserRole() != UserRole.SUPER_ADMIN) return true;
        if (Objects.equals(approval.getWriter().getUserId(), delegateeId)) return true;
        boolean isCc = ccs.findByApproval_ApprovalId(approval.getApprovalId()).stream()
                .anyMatch(cc -> Objects.equals(cc.getCcUser().getUserId(), delegateeId));
        if (isCc) return true;
        return lines.findAllByApproval_ApprovalIdOrderByApprovalStepAsc(approval.getApprovalId()).stream()
                .filter(line -> !Objects.equals(line.getApprovalLineId(), target.getApprovalLineId()))
                .anyMatch(line -> Objects.equals(line.getApprover().getUserId(), delegateeId)
                        || (line.getProcessedBy() != null
                        && Objects.equals(line.getProcessedBy().getUserId(), delegateeId)));
    }

    private Optional<User> activeDelegatee(Long delegatorId, LocalDate today) {
        List<ApprovalDelegation> active = delegations.findActiveForUpdate(delegatorId, today);
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
