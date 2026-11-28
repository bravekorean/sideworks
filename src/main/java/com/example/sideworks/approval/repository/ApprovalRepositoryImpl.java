package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.dto.ApprovalCcResponse;
import com.example.sideworks.approval.dto.ApprovalActivityResponse;
import com.example.sideworks.approval.dto.ApprovalDelegationState;
import com.example.sideworks.approval.dto.ApprovalDetailHeaderResponse;
import com.example.sideworks.approval.dto.ApprovalHistoryResponse;
import com.example.sideworks.approval.dto.ApprovalLineResponse;
import com.example.sideworks.approval.dto.ApprovalListResponse;
import com.example.sideworks.approval.entity.ApprovalActionType;
import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.approval.entity.QApprovalDelegation;
import com.example.sideworks.approval.entity.QApprovalLine;
import com.example.sideworks.user.entity.QUser;
import com.example.sideworks.user.entity.UserStatus;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.example.sideworks.approval.entity.QApproval.approval;
import static com.example.sideworks.approval.entity.QApprovalDocumentType.approvalDocumentType;
import static com.example.sideworks.approval.entity.QApprovalCc.approvalCc;
import static com.example.sideworks.approval.entity.QApprovalHistory.approvalHistory;
import static com.example.sideworks.approval.entity.QApprovalLine.approvalLine;

@RequiredArgsConstructor
public class ApprovalRepositoryImpl implements ApprovalRepositoryCustom {

    // 대시보드 최근 활동은 전체 이력 중 최신 구간부터 확인한다. 측정 근거는 PERFORMANCE.md를 참조한다.
    private static final int LATEST_ACTIVITY_PROBE_SIZE = 100;

    // 접근 가능한 문서 ID를 역할별 인덱스로 모은 뒤 이력을 조인한다.
    // JPQL/QueryDSL은 UNION과 CTE를 생성하지 않으므로 이 경로만 네이티브 SQL로 둔다.
    // 반환 컬럼·정렬·임시저장 권한은 accessibleByUser와 동일해야 한다.
    private static final String VISIBLE_APPROVALS_CTE = """
            with visible as (
                select a.approval_id from approvaltbl a where a.writer_id = :userId
                union
                select l.approval_id from approval_linetbl l
                join approvaltbl a on a.approval_id = l.approval_id
                where l.approver_id = :userId and a.approval_status <> 'DRAFT'
                union
                select l.approval_id from approval_linetbl l
                join approvaltbl a on a.approval_id = l.approval_id
                where l.processed_by_id = :userId and a.approval_status <> 'DRAFT'
                union
                select c.approval_id from approval_cctbl c
                join approvaltbl a on a.approval_id = c.approval_id
                where c.user_id = :userId and a.approval_status <> 'DRAFT'
            )
            """;

    private static final String VISIBLE_ACTIVITIES_PAGE_SQL = VISIBLE_APPROVALS_CTE + """
            , latest as (
                select h.approval_history_id, h.approval_id, h.actor_id, h.action_step, h.action_type, h.created_at
                from visible v
                join approval_historytbl h on h.approval_id = v.approval_id
                order by h.created_at desc, h.approval_history_id desc
                limit :limit offset :offset
            )
            select h.approval_history_id, a.approval_id, a.title, u.user_id, u.user_name,
                   h.action_step, h.action_type, h.created_at
            from latest h
            join approvaltbl a on a.approval_id = h.approval_id
            join usertbl u on u.user_id = h.actor_id
            order by h.created_at desc, h.approval_history_id desc
            """;

    private static final String VISIBLE_ACTIVITIES_COUNT_SQL = VISIBLE_APPROVALS_CTE + """
            select count(*)
            from visible v
            join approval_historytbl h on h.approval_id = v.approval_id
            """;

    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;

    @Override
    public long countInProgressByWriterId(Long writerId) {
        Long total = queryFactory
                .select(approval.count())
                .from(approval)
                .join(approval.writer)
                .where(
                        approval.writer.userId.eq(writerId),
                        approval.approvalStatus.eq(ApprovalStatus.IN_PROGRESS)
                )
                .fetchOne();
        return total == null ? 0L : total;
    }

    @Override
    public long countDraftsByWriterId(Long writerId) {
        Long total = queryFactory
                .select(approval.count())
                .from(approval)
                .join(approval.writer)
                .where(
                        approval.writer.userId.eq(writerId),
                        approval.approvalStatus.eq(ApprovalStatus.DRAFT)
                )
                .fetchOne();
        return total == null ? 0L : total;
    }

    @Override
    public long countCcByUserId(Long userId) {
        Long total = queryFactory
                .select(approvalCc.count())
                .from(approvalCc)
                .join(approvalCc.approval, approval)
                .join(approval.writer)
                .where(approvalCc.ccUser.userId.eq(userId))
                .fetchOne();
        return total == null ? 0L : total;
    }

    @Override
    public List<ApprovalActivityResponse> findLatestActivitiesByUserId(Long userId, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("최근 활동 조회 개수는 1 이상이어야 합니다.");
        }
        return findVisibleActivities(userId, 0, limit);
    }

    // 최신 이력 구간에서 offset+limit건을 채우면 그 결과를 쓰고, 부족하면 접근 문서 집합으로 다시 조회한다.
    // 구간 안에서 채웠다면 그보다 최신인 접근 가능 이력은 구간 밖에 존재할 수 없으므로 결과가 같다.
    private List<ApprovalActivityResponse> findVisibleActivities(Long userId, long offset, int limit) {
        long needed = offset + limit;
        if (needed <= LATEST_ACTIVITY_PROBE_SIZE) {
            List<ApprovalActivityResponse> probed = findVisibleAmongLatestHistories(userId, (int) needed);
            if (probed.size() == needed) {
                return probed.subList((int) offset, probed.size());
            }
        }
        return findVisibleActivitiesPage(userId, offset, limit);
    }

    // 이력 정렬 인덱스로 최신 N건의 ID만 읽고, 그 안에서 접근 범위를 판정한다.
    private List<ApprovalActivityResponse> findVisibleAmongLatestHistories(Long userId, int limit) {
        List<Long> latestHistoryIds = queryFactory
                .select(approvalHistory.approvalHistoryId)
                .from(approvalHistory)
                .orderBy(
                        approvalHistory.createdAt.desc(),
                        approvalHistory.approvalHistoryId.desc()
                )
                .limit(LATEST_ACTIVITY_PROBE_SIZE)
                .fetch();
        if (latestHistoryIds.isEmpty()) {
            return List.of();
        }
        return recentActivitiesQuery(userId)
                .where(approvalHistory.approvalHistoryId.in(latestHistoryIds))
                .limit(limit)
                .fetch();
    }

    @SuppressWarnings("unchecked")
    private List<ApprovalActivityResponse> findVisibleActivitiesPage(Long userId, long offset, int limit) {
        List<Object[]> rows = entityManager.createNativeQuery(VISIBLE_ACTIVITIES_PAGE_SQL)
                .setParameter("userId", userId)
                .setParameter("limit", limit)
                .setParameter("offset", offset)
                .getResultList();
        return rows.stream().map(ApprovalRepositoryImpl::toActivityResponse).toList();
    }

    // OR + EXISTS count는 참여 문서가 없어도 이력 전체를 판정하므로, 접근 문서 집합에서 센다.
    private long countVisibleActivities(Long userId) {
        Number total = (Number) entityManager.createNativeQuery(VISIBLE_ACTIVITIES_COUNT_SQL)
                .setParameter("userId", userId)
                .getSingleResult();
        return total.longValue();
    }

    private static ApprovalActivityResponse toActivityResponse(Object[] row) {
        return new ApprovalActivityResponse(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                (String) row[2],
                ((Number) row[3]).longValue(),
                (String) row[4],
                ((Number) row[5]).intValue(),
                ApprovalActionType.valueOf((String) row[6]),
                toLocalDateTime(row[7])
        );
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return (LocalDateTime) value;
    }

    @Override
    public Page<ApprovalListResponse> findForManagement(String keyword, ApprovalStatus status,
                                                       boolean blockedOnly, LocalDate today, Pageable pageable) {
        BooleanExpression filter = approval.submittedAt.isNotNull()
                .and(approval.approvalStatus.ne(ApprovalStatus.DRAFT));
        if (blockedOnly) filter = filter.and(hasInactiveCurrentProcessor(today));
        List<ApprovalListResponse> content = queryFactory.select(approvalListProjection())
                .from(approval).join(approval.writer)
                .where(filter, containsKeyword(keyword), hasStatus(status))
                .orderBy(approval.submittedAt.desc(), approval.approvalId.desc())
                .offset(pageable.getOffset()).limit(pageable.getPageSize()).fetch();
        Long total = queryFactory.select(approval.count()).from(approval).join(approval.writer)
                .where(filter, containsKeyword(keyword), hasStatus(status)).fetchOne();
        return toPage(content, pageable, total);
    }

    // 실제 처리자 기준으로 판별하고, 미래 단계의 비활성 계정은 제외한다.
    // 대결자는 ACTIVE일 때만 유효하므로 "원 결재자 비활성 + 유효한 대결 없음"이 곧 처리자 비활성이다.
    private BooleanExpression hasInactiveCurrentProcessor(LocalDate today) {
        QApprovalLine currentLine = new QApprovalLine("managementCurrentLine");
        QUser currentApprover = new QUser("managementCurrentApprover");
        return approval.approvalStatus.eq(ApprovalStatus.IN_PROGRESS).and(JPAExpressions.selectOne()
                .from(currentLine).join(currentLine.approver, currentApprover)
                .where(currentLine.approval.approvalId.eq(approval.approvalId),
                        currentLine.approvalStep.eq(approval.currentStep),
                        currentLine.approvalStatus.eq(ApprovalLineStatus.PENDING),
                        currentApprover.status.ne(UserStatus.ACTIVE),
                        ApprovalProcessorConditions.isDelegated(currentLine, approval, today, "management").not())
                .exists());
    }

    @Override
    public Page<ApprovalListResponse> findDraftsByWriterId(Long writerId, String keyword, Pageable pageable) {
        List<ApprovalListResponse> content = queryFactory
                .select(approvalListProjection())
                .from(approval)
                .join(approval.writer)
                .where(
                        approval.writer.userId.eq(writerId),
                        approval.approvalStatus.eq(ApprovalStatus.DRAFT),
                        containsKeyword(keyword)
                )
                .orderBy(approval.createdAt.desc(),
                         approval.approvalId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(approval.count())
                .from(approval)
                .join(approval.writer)
                .where(
                        approval.writer.userId.eq(writerId),
                        approval.approvalStatus.eq(ApprovalStatus.DRAFT),
                        containsKeyword(keyword)
                )
                .fetchOne();

        return toPage(content, pageable, total);
    }

    @Override
    public Page<ApprovalListResponse> findSentByWriterId(Long writerId, String keyword, ApprovalStatus status, Pageable pageable) {
        List<ApprovalListResponse> content = queryFactory
                .select(approvalListProjection())
                .from(approval)
                .join(approval.writer)
                .where(
                        approval.writer.userId.eq(writerId),
                        approval.approvalStatus.ne(ApprovalStatus.DRAFT),
                        containsKeyword(keyword),
                        hasStatus(status)
                )
                .orderBy(
                        approval.submittedAt.desc(),
                        approval.approvalId.desc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(approval.count())
                .from(approval)
                .join(approval.writer)
                .where(
                        approval.writer.userId.eq(writerId),
                        approval.approvalStatus.ne(ApprovalStatus.DRAFT),
                        containsKeyword(keyword),
                        hasStatus(status)
                )
                .fetchOne();

        return toPage(content, pageable, total);
    }

    @Override
    public Page<ApprovalListResponse> findPendingByProcessorId(Long processorId, LocalDate today,
                                                               String keyword, Pageable pageable) {
        DelegationSnapshot delegations = findTodayDelegations(processorId, today);
        QUser lineApprover = new QUser("pendingApprover");
        BooleanExpression[] filter = {
                pendingForProcessor(processorId, today, delegations),
                approvalLine.approvalStatus.eq(ApprovalLineStatus.PENDING),
                approval.approvalStatus.eq(ApprovalStatus.IN_PROGRESS),
                containsKeyword(keyword)
        };

        List<Tuple> rows = queryFactory
                .select(approvalListProjection(), lineApprover.userId, lineApprover.userName)
                .from(approvalLine)
                .join(approvalLine.approval, approval)
                .join(approval.writer)
                .join(approvalLine.approver, lineApprover)
                .where(filter)
                .orderBy(
                        approval.createdAt.desc(),
                        approval.approvalId.desc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(approvalLine.count())
                .from(approvalLine)
                .join(approvalLine.approval, approval)
                .join(approval.writer)
                .where(filter)
                .fetchOne();

        List<ApprovalListResponse> content = rows.stream()
                .map(row -> markDelegation(row, lineApprover, processorId, delegations.delegating()))
                .toList();
        return toPage(content, pageable, total);
    }

    /**
     * 오늘 유효한 위임 중 processorId가 맡은 것(받은 위임)과 맡긴 것(보낸 위임)을 한 번에 읽는다.
     * 대기함 조건을 이 결과로 줄여서, 위임이 없는 사용자는 이전과 같은 approver_id 조건만 실행한다.
     */
    private record DelegationSnapshot(List<Long> delegatorIds, boolean delegating) {
    }

    private DelegationSnapshot findTodayDelegations(Long userId, LocalDate today) {
        QApprovalDelegation d = new QApprovalDelegation("todayDelegation");
        List<Tuple> rows = queryFactory.select(d.delegator.userId, d.delegatee.userId)
                .from(d)
                .where(d.delegatee.userId.eq(userId).or(d.delegator.userId.eq(userId)),
                        d.canceledAt.isNull(),
                        d.startDate.loe(today),
                        d.endDate.goe(today),
                        d.delegatee.status.eq(UserStatus.ACTIVE))
                .fetch();
        List<Long> delegatorIds = rows.stream()
                .filter(row -> Objects.equals(row.get(d.delegatee.userId), userId))
                .map(row -> row.get(d.delegator.userId))
                .toList();
        boolean delegating = rows.stream().anyMatch(row -> Objects.equals(row.get(d.delegator.userId), userId));
        return new DelegationSnapshot(delegatorIds, delegating);
    }

    // ApprovalProcessorConditions.isCurrentProcessor와 같은 규칙을, 미리 읽은 위임 정보로 필요한 부분만 남긴 형태다.
    // - 내 단계: 내가 오늘 위임 중일 때만 "대결로 넘어가지 않았는가"를 행마다 확인한다.
    // - 나에게 위임한 사람의 단계: 충돌이 없어 나에게 넘어왔는가를 행마다 확인한다.
    private BooleanExpression pendingForProcessor(Long processorId, LocalDate today, DelegationSnapshot delegations) {
        BooleanExpression own = approvalLine.approver.userId.eq(processorId);
        if (delegations.delegating()) {
            own = own.and(ApprovalProcessorConditions.isDelegated(approvalLine, approval, today, "pendingOwn").not());
        }
        if (delegations.delegatorIds().isEmpty()) {
            return own;
        }
        BooleanExpression delegatedToMe = approvalLine.approver.userId.in(delegations.delegatorIds())
                .and(ApprovalProcessorConditions.isDelegatedTo(approvalLine, approval, processorId, today, "pendingTo"));
        return own.or(delegatedToMe);
    }

    // 내 단계가 나에게 남아 있는데 오늘 내가 위임 중이라면, 규칙상 대결자와 충돌한 문서다.
    private ApprovalListResponse markDelegation(Tuple row, QUser lineApprover, Long processorId,
                                                boolean delegatingToday) {
        ApprovalListResponse item = row.get(0, ApprovalListResponse.class);
        if (!Objects.equals(row.get(lineApprover.userId), processorId)) {
            return item.withDelegation(ApprovalDelegationState.DELEGATED, row.get(lineApprover.userName));
        }
        return delegatingToday ? item.withDelegation(ApprovalDelegationState.DELEGATION_BLOCKED, null) : item;
    }

    private boolean hasEffectiveDelegation(Long delegatorId, LocalDate today) {
        QApprovalDelegation d = new QApprovalDelegation("ownDelegation");
        return queryFactory.selectOne()
                .from(d)
                .where(d.delegator.userId.eq(delegatorId),
                        d.canceledAt.isNull(),
                        d.startDate.loe(today),
                        d.endDate.goe(today),
                        d.delegatee.status.eq(UserStatus.ACTIVE))
                .fetchFirst() != null;
    }

    @Override
    public Page<ApprovalListResponse> findProcessedByProcessorId(Long processorId, String keyword,
                                                                 ApprovalStatus status, Pageable pageable) {
        List<ApprovalListResponse> content = queryFactory
                .select(approvalListProjection())
                .from(approvalLine)
                .join(approvalLine.approval, approval)
                .join(approval.writer)
                .where(
                        approvalLine.processedBy.userId.eq(processorId),
                        approvalLine.approvalStatus.in(
                                ApprovalLineStatus.APPROVED,
                                ApprovalLineStatus.REJECTED
                        ),
                        containsKeyword(keyword),
                        hasStatus(status)
                )
                .orderBy(
                        approvalLine.processedAt.desc(),
                        approvalLine.approvalLineId.desc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(approvalLine.count())
                .from(approvalLine)
                .join(approvalLine.approval, approval)
                .join(approval.writer)
                .where(
                        approvalLine.processedBy.userId.eq(processorId),
                        approvalLine.approvalStatus.in(
                                ApprovalLineStatus.APPROVED,
                                ApprovalLineStatus.REJECTED
                        ),
                        containsKeyword(keyword),
                        hasStatus(status)
                )
                .fetchOne();

        return toPage(content, pageable, total);
    }

    @Override
    public Page<ApprovalListResponse> findCcByUserId(Long userId, String keyword, ApprovalStatus status, Pageable pageable) {
        List<ApprovalListResponse> content = queryFactory
                .select(approvalListProjection())
                .from(approvalCc)
                .join(approvalCc.approval, approval)
                .join(approval.writer)
                .where(
                        approvalCc.ccUser.userId.eq(userId),
                        containsKeyword(keyword),
                        hasStatus(status)
                )
                .orderBy(
                        approval.createdAt.desc(),
                        approvalCc.approvalCcId.desc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(approvalCc.count())
                .from(approvalCc)
                .join(approvalCc.approval, approval)
                .join(approval.writer)
                .where(
                        approvalCc.ccUser.userId.eq(userId),
                        containsKeyword(keyword),
                        hasStatus(status)
                )
                .fetchOne();

        return toPage(content, pageable, total);
    }

    @Override
    public Page<ApprovalActivityResponse> findRecentActivitiesByUserId(Long userId, Pageable pageable) {
        List<ApprovalActivityResponse> content =
                findVisibleActivities(userId, pageable.getOffset(), pageable.getPageSize());
        return toPage(content, pageable, countVisibleActivities(userId));
    }

    // 최신 이력 구간 안의 접근 판정에 쓴다. 네이티브 fallback과 접근 범위·정렬이 같아야 한다.
    private JPAQuery<ApprovalActivityResponse> recentActivitiesQuery(Long userId) {
        return queryFactory
                .select(Projections.constructor(
                        ApprovalActivityResponse.class,
                        approvalHistory.approvalHistoryId,
                        approval.approvalId,
                        approval.title,
                        approvalHistory.actor.userId,
                        approvalHistory.actor.userName,
                        approvalHistory.actionStep,
                        approvalHistory.actionType,
                        approvalHistory.createdAt
                ))
                .from(approvalHistory)
                .join(approvalHistory.approval, approval)
                .join(approvalHistory.actor)
                .where(accessibleByUser(userId))
                .orderBy(
                        approvalHistory.createdAt.desc(),
                        approvalHistory.approvalHistoryId.desc()
                );
    }

    @Override
    public Page<ApprovalListResponse> searchApprovals(
            Long userId,
            boolean searchAll,
            String keyword,
            Pageable pageable
    ) {
        List<ApprovalListResponse> content = queryFactory
                .select(approvalListProjection())
                .from(approval)
                .join(approval.writer)
                .where(
                        searchAll ? null : accessibleByUser(userId),
                        matchesGlobalSearch(keyword)
                )
                .orderBy(
                        approval.createdAt.desc(),
                        approval.approvalId.desc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(approval.count())
                .from(approval)
                .join(approval.writer)
                .where(
                        searchAll ? null : accessibleByUser(userId),
                        matchesGlobalSearch(keyword)
                )
                .fetchOne();

        return toPage(content, pageable, total);
    }

    @Override
    public Optional<ApprovalDetailHeaderResponse> findDetailHeaderByApprovalId(Long approvalId) {
        ApprovalDetailHeaderResponse header = queryFactory
                .select(approvalDetailHeaderProjection())
                .from(approval)
                .join(approval.writer)
                .leftJoin(approval.documentType, approvalDocumentType)
                .where(approval.approvalId.eq(approvalId))
                .fetchOne();

        return Optional.ofNullable(header);
    }

    @Override
    public Optional<ApprovalDetailHeaderResponse> findAccessibleDetailHeader(
            Long approvalId,
            Long userId,
            LocalDate today
    ) {
        ApprovalDetailHeaderResponse header = queryFactory
                .select(approvalDetailHeaderProjection())
                .from(approval)
                .join(approval.writer)
                .leftJoin(approval.documentType, approvalDocumentType)
                .where(
                        approval.approvalId.eq(approvalId),
                        accessibleByUser(approvalId, userId).or(isCurrentProcessorOf(userId, today))
                )
                .fetchOne();

        return Optional.ofNullable(header);
    }

    @Override
    public List<ApprovalLineResponse> findDetailLinesByApprovalId(Long approvalId, LocalDate today) {
        QUser lineApprover = new QUser("detailApprover");
        QUser processor = new QUser("detailProcessor");
        List<Tuple> rows = queryFactory
                .select(approvalLine.approvalLineId, lineApprover.userId, lineApprover.userName,
                        lineApprover.status, processor.userId, processor.userName, approvalLine.approvalStep,
                        approvalLine.approvalStatus, approvalLine.approvalComment, approvalLine.processedAt,
                        approval.approvalStatus, approval.currentStep)
                .from(approvalLine)
                .join(approvalLine.approval, approval)
                .join(approvalLine.approver, lineApprover)
                .leftJoin(approvalLine.processedBy, processor)
                .where(approvalLine.approval.approvalId.eq(approvalId))
                .orderBy(
                        approvalLine.approvalStep.asc(),
                        approvalLine.approvalLineId.asc()
                )
                .fetch();

        return rows.stream()
                .map(row -> toLineResponse(row, lineApprover, processor, today))
                .toList();
    }

    private ApprovalLineResponse toLineResponse(Tuple row, QUser lineApprover, QUser processor, LocalDate today) {
        Long lineId = row.get(approvalLine.approvalLineId);
        Long approverId = row.get(lineApprover.userId);
        String approverName = row.get(lineApprover.userName);
        Long processorId = row.get(processor.userId);
        String processorName = row.get(processor.userName);
        ApprovalDelegationState state = null;

        if (processorId != null) {
            // 처리된 단계: 실제 처리자가 원 결재자와 다르면 대결로 처리한 것이다.
            state = Objects.equals(processorId, approverId) ? null : ApprovalDelegationState.DELEGATED;
        } else if (isCurrentPendingStep(row)) {
            Optional<Tuple> delegatee = findCurrentDelegatee(lineId, today);
            if (delegatee.isPresent()) {
                processorId = delegatee.get().get(0, Long.class);
                processorName = delegatee.get().get(1, String.class);
                state = ApprovalDelegationState.DELEGATED;
            } else {
                processorId = approverId;
                processorName = approverName;
                state = hasEffectiveDelegation(approverId, today)
                        ? ApprovalDelegationState.DELEGATION_BLOCKED : null;
            }
        }

        return new ApprovalLineResponse(lineId, approverId, approverName, row.get(lineApprover.status),
                processorId, processorName, state, row.get(approvalLine.approvalStep),
                row.get(approvalLine.approvalStatus), row.get(approvalLine.approvalComment),
                row.get(approvalLine.processedAt));
    }

    private boolean isCurrentPendingStep(Tuple row) {
        return row.get(approval.approvalStatus) == ApprovalStatus.IN_PROGRESS
                && row.get(approvalLine.approvalStatus) == ApprovalLineStatus.PENDING
                && Objects.equals(row.get(approval.currentStep), row.get(approvalLine.approvalStep));
    }

    // 대기함과 같은 조건(ApprovalProcessorConditions)으로 지금 이 단계를 맡은 대결자를 찾는다.
    private Optional<Tuple> findCurrentDelegatee(Long approvalLineId, LocalDate today) {
        QApprovalLine line = new QApprovalLine("currentDelegatedLine");
        QApprovalDelegation d = new QApprovalDelegation("currentDelegation");
        return Optional.ofNullable(queryFactory
                .select(d.delegatee.userId, d.delegatee.userName)
                .from(line, d)
                .where(line.approvalLineId.eq(approvalLineId),
                        ApprovalProcessorConditions.appliesTo(d, line, line.approval, today, "detail"))
                .fetchFirst());
    }

    @Override
    public List<ApprovalCcResponse> findDetailCcsByApprovalId(Long approvalId) {
        return queryFactory
                .select(Projections.constructor(
                        ApprovalCcResponse.class,
                        approvalCc.ccUser.userId,
                        approvalCc.ccUser.userName
                ))
                .from(approvalCc)
                .join(approvalCc.ccUser)
                .where(approvalCc.approval.approvalId.eq(approvalId))
                .orderBy(
                        approvalCc.createdAt.asc(),
                        approvalCc.approvalCcId.asc()
                )
                .fetch();
    }

    @Override
    public List<ApprovalHistoryResponse> findDetailHistoriesByApprovalId(Long approvalId) {
        // 같은 단계의 결재선을 붙여 원 결재자를 함께 읽는다. 행동한 사람과 다르면 대결 처리다.
        QApprovalLine stepLine = new QApprovalLine("historyStepLine");
        QUser stepApprover = new QUser("historyStepApprover");
        return queryFactory
                .select(Projections.constructor(
                        ApprovalHistoryResponse.class,
                        approvalHistory.approvalHistoryId,
                        approvalHistory.actor.userId,
                        approvalHistory.actor.userName,
                        approvalHistory.actionStep,
                        approvalHistory.actionType,
                        approvalHistory.comment,
                        approvalHistory.createdAt,
                        stepApprover.userId,
                        stepApprover.userName
                ))
                .from(approvalHistory)
                .join(approvalHistory.actor)
                .leftJoin(stepLine).on(
                        stepLine.approval.approvalId.eq(approvalHistory.approval.approvalId),
                        stepLine.approvalStep.eq(approvalHistory.actionStep))
                .leftJoin(stepLine.approver, stepApprover)
                .where(approvalHistory.approval.approvalId.eq(approvalId))
                .orderBy(
                        approvalHistory.createdAt.asc(),
                        approvalHistory.approvalHistoryId.asc()
                )
                .fetch();
    }

    private BooleanExpression accessibleByUser(Long approvalId, Long userId) {
        BooleanExpression isApprover = JPAExpressions
                .selectOne()
                .from(approvalLine)
                .where(
                        approvalLine.approval.approvalId.eq(approvalId),
                        approvalLine.approver.userId.eq(userId)
                                .or(approvalLine.processedBy.userId.eq(userId))
                )
                .exists();

        BooleanExpression isCcUser = JPAExpressions
                .selectOne()
                .from(approvalCc)
                .where(
                        approvalCc.approval.approvalId.eq(approvalId),
                        approvalCc.ccUser.userId.eq(userId)
                )
                .exists();

        return approval.writer.userId.eq(userId)
                .or(
                        approval.approvalStatus.ne(ApprovalStatus.DRAFT)
                                .and(isApprover.or(isCcUser))
                );
    }

    private BooleanExpression accessibleByUser(Long userId) {
        BooleanExpression isApprover = JPAExpressions
                .selectOne()
                .from(approvalLine)
                .where(
                        approvalLine.approval.approvalId.eq(approval.approvalId),
                        approvalLine.approver.userId.eq(userId)
                                .or(approvalLine.processedBy.userId.eq(userId))
                )
                .exists();

        BooleanExpression isCcUser = JPAExpressions
                .selectOne()
                .from(approvalCc)
                .where(
                        approvalCc.approval.approvalId.eq(approval.approvalId),
                        approvalCc.ccUser.userId.eq(userId)
                )
                .exists();

        return approval.writer.userId.eq(userId)
                .or(
                        approval.approvalStatus.ne(ApprovalStatus.DRAFT)
                                .and(isApprover.or(isCcUser))
                );
    }

    // 대결 기간 동안 대결자는 지금 처리할 문서의 상세를 볼 수 있다. 처리한 뒤에는 processedBy로 계속 볼 수 있다.
    // 검색·최근 활동은 문서 참여자(작성자·원 결재자·실제 처리자·참조자) 기준으로 두고, 임시 권한으로 넓히지 않는다.
    private BooleanExpression isCurrentProcessorOf(Long userId, LocalDate today) {
        QApprovalLine currentLine = new QApprovalLine("accessCurrentLine");
        return approval.approvalStatus.eq(ApprovalStatus.IN_PROGRESS).and(JPAExpressions.selectOne()
                .from(currentLine)
                .where(currentLine.approval.approvalId.eq(approval.approvalId),
                        currentLine.approvalStatus.eq(ApprovalLineStatus.PENDING),
                        ApprovalProcessorConditions.isCurrentProcessor(currentLine, approval, userId, today, "access"))
                .exists());
    }

    private BooleanExpression containsKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String normalizedKeyword = keyword.trim();
        return approval.title.containsIgnoreCase(normalizedKeyword)
                .or(approval.writer.userName.containsIgnoreCase(normalizedKeyword));
    }

    private BooleanExpression matchesGlobalSearch(String keyword) {
        String normalizedKeyword = keyword.trim();
        BooleanExpression matchesApprover = JPAExpressions
                .selectOne()
                .from(approvalLine)
                .where(
                        approvalLine.approval.approvalId.eq(approval.approvalId),
                        matchesUser(approvalLine.approver, normalizedKeyword)
                )
                .exists();
        BooleanExpression matchesCcUser = JPAExpressions
                .selectOne()
                .from(approvalCc)
                .where(
                        approvalCc.approval.approvalId.eq(approval.approvalId),
                        matchesUser(approvalCc.ccUser, normalizedKeyword)
                )
                .exists();

        return approval.title.containsIgnoreCase(normalizedKeyword)
                .or(matchesUser(approval.writer, normalizedKeyword))
                .or(matchesApprover)
                .or(matchesCcUser);
    }

    private BooleanExpression matchesUser(QUser user, String keyword) {
        return user.userName.containsIgnoreCase(keyword)
                .or(user.loginId.containsIgnoreCase(keyword))
                .or(user.employeeNo.containsIgnoreCase(keyword));
    }

    private BooleanExpression hasStatus(ApprovalStatus status) {
        return status == null ? null : approval.approvalStatus.eq(status);
    }

    private ConstructorExpression<ApprovalListResponse> approvalListProjection() {
        return Projections.constructor(
                ApprovalListResponse.class,
                approval.approvalId,
                approval.title,
                approval.writer.userId,
                approval.writer.userName,
                approval.approvalStatus,
                approval.currentStep,
                approval.createdAt,
                approval.submittedAt,
                approval.completedAt
        );
    }

    private ConstructorExpression<ApprovalDetailHeaderResponse> approvalDetailHeaderProjection() {
        return Projections.constructor(
                ApprovalDetailHeaderResponse.class,
                approval.approvalId,
                approval.writer.userId,
                approval.writer.userName,
                approval.title,
                approval.content,
                approval.approvalStatus,
                approval.currentStep,
                approval.createdAt,
                approval.updatedAt,
                approval.submittedAt,
                approval.completedAt,
                approvalDocumentType.approvalDocumentTypeId,
                approvalDocumentType.typeName,
                approvalDocumentType.behaviorType
        );
    }

    private <T> Page<T> toPage(List<T> content, Pageable pageable, Long total) {
        return new PageImpl<>(
                content,
                pageable,
                total == null ? 0L : total
        );
    }
}
