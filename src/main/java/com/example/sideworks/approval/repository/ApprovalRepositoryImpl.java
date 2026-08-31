package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.dto.ApprovalCcResponse;
import com.example.sideworks.approval.dto.ApprovalActivityResponse;
import com.example.sideworks.approval.dto.ApprovalDetailHeaderResponse;
import com.example.sideworks.approval.dto.ApprovalHistoryResponse;
import com.example.sideworks.approval.dto.ApprovalLineResponse;
import com.example.sideworks.approval.dto.ApprovalListResponse;
import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.user.entity.QUser;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static com.example.sideworks.approval.entity.QApproval.approval;
import static com.example.sideworks.approval.entity.QApprovalCc.approvalCc;
import static com.example.sideworks.approval.entity.QApprovalHistory.approvalHistory;
import static com.example.sideworks.approval.entity.QApprovalLine.approvalLine;

@RequiredArgsConstructor
public class ApprovalRepositoryImpl implements ApprovalRepositoryCustom {

    private final JPAQueryFactory queryFactory;

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
    public Page<ApprovalListResponse> findPendingByApproverId(Long approverId, String keyword, Pageable pageable) {
        List<ApprovalListResponse> content = queryFactory
                .select(approvalListProjection())
                .from(approvalLine)
                .join(approvalLine.approval, approval)
                .join(approval.writer)
                .where(
                        approvalLine.approver.userId.eq(approverId),
                        approvalLine.approvalStatus.eq(ApprovalLineStatus.PENDING),
                        approval.approvalStatus.eq(ApprovalStatus.IN_PROGRESS),
                        containsKeyword(keyword)
                )
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
                .where(
                        approvalLine.approver.userId.eq(approverId),
                        approvalLine.approvalStatus.eq(ApprovalLineStatus.PENDING),
                        approval.approvalStatus.eq(ApprovalStatus.IN_PROGRESS),
                        containsKeyword(keyword)
                )
                .fetchOne();

        return toPage(content, pageable, total);
    }

    @Override
    public Page<ApprovalListResponse> findProcessedByApproverId(Long approverId, String keyword, ApprovalStatus status, Pageable pageable) {
        List<ApprovalListResponse> content = queryFactory
                .select(approvalListProjection())
                .from(approvalLine)
                .join(approvalLine.approval, approval)
                .join(approval.writer)
                .where(
                        approvalLine.approver.userId.eq(approverId),
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
                        approvalLine.approver.userId.eq(approverId),
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
        List<ApprovalActivityResponse> content = queryFactory
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
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(approvalHistory.count())
                .from(approvalHistory)
                .join(approvalHistory.approval, approval)
                .where(accessibleByUser(userId))
                .fetchOne();

        return toPage(content, pageable, total);
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
                .where(approval.approvalId.eq(approvalId))
                .fetchOne();

        return Optional.ofNullable(header);
    }

    @Override
    public Optional<ApprovalDetailHeaderResponse> findAccessibleDetailHeader(
            Long approvalId,
            Long userId
    ) {
        ApprovalDetailHeaderResponse header = queryFactory
                .select(approvalDetailHeaderProjection())
                .from(approval)
                .join(approval.writer)
                .where(
                        approval.approvalId.eq(approvalId),
                        accessibleByUser(approvalId, userId)
                )
                .fetchOne();

        return Optional.ofNullable(header);
    }

    @Override
    public List<ApprovalLineResponse> findDetailLinesByApprovalId(Long approvalId) {
        return queryFactory
                .select(Projections.constructor(
                        ApprovalLineResponse.class,
                        approvalLine.approvalLineId,
                        approvalLine.approver.userId,
                        approvalLine.approver.userName,
                        approvalLine.approvalStep,
                        approvalLine.approvalStatus,
                        approvalLine.approvalComment,
                        approvalLine.processedAt
                ))
                .from(approvalLine)
                .join(approvalLine.approver)
                .where(approvalLine.approval.approvalId.eq(approvalId))
                .orderBy(
                        approvalLine.approvalStep.asc(),
                        approvalLine.approvalLineId.asc()
                )
                .fetch();
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
        return queryFactory
                .select(Projections.constructor(
                        ApprovalHistoryResponse.class,
                        approvalHistory.approvalHistoryId,
                        approvalHistory.actor.userId,
                        approvalHistory.actor.userName,
                        approvalHistory.actionStep,
                        approvalHistory.actionType,
                        approvalHistory.comment,
                        approvalHistory.createdAt
                ))
                .from(approvalHistory)
                .join(approvalHistory.actor)
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
                approval.completedAt
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
