package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.dto.ApprovalCcResponse;
import com.example.sideworks.approval.dto.ApprovalDetailHeaderResponse;
import com.example.sideworks.approval.dto.ApprovalHistoryResponse;
import com.example.sideworks.approval.dto.ApprovalLineResponse;
import com.example.sideworks.approval.dto.ApprovalListResponse;
import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.approval.entity.ApprovalStatus;
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
    public Page<ApprovalListResponse> findDraftsByWriterId(Long writerId, Pageable pageable) {
        List<ApprovalListResponse> content = queryFactory
                .select(approvalListProjection())
                .from(approval)
                .join(approval.writer)
                .where(
                        approval.writer.userId.eq(writerId),
                        approval.approvalStatus.eq(ApprovalStatus.DRAFT)
                )
                .orderBy(approval.createdAt.desc(),
                         approval.approvalId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(approval.count())
                .from(approval)
                .where(
                        approval.writer.userId.eq(writerId),
                        approval.approvalStatus.eq(ApprovalStatus.DRAFT)
                )
                .fetchOne();

        return toPage(content, pageable, total);
    }

    @Override
    public Page<ApprovalListResponse> findSentByWriterId(Long writerId, Pageable pageable) {
        List<ApprovalListResponse> content = queryFactory
                .select(approvalListProjection())
                .from(approval)
                .join(approval.writer)
                .where(
                        approval.writer.userId.eq(writerId),
                        approval.approvalStatus.ne(ApprovalStatus.DRAFT)
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
                .where(
                        approval.writer.userId.eq(writerId),
                        approval.approvalStatus.ne(ApprovalStatus.DRAFT)
                )
                .fetchOne();

        return toPage(content, pageable, total);
    }

    @Override
    public Page<ApprovalListResponse> findPendingByApproverId(Long approverId, Pageable pageable) {
        List<ApprovalListResponse> content = queryFactory
                .select(approvalListProjection())
                .from(approvalLine)
                .join(approvalLine.approval, approval)
                .join(approval.writer)
                .where(
                        approvalLine.approver.userId.eq(approverId),
                        approvalLine.approvalStatus.eq(ApprovalLineStatus.PENDING)
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
                .where(
                        approvalLine.approver.userId.eq(approverId),
                        approvalLine.approvalStatus.eq(ApprovalLineStatus.PENDING)
                )
                .fetchOne();

        return toPage(content, pageable, total);
    }

    @Override
    public Page<ApprovalListResponse> findProcessedByApproverId(Long approverId, Pageable pageable) {
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
                        )
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
                .where(
                        approvalLine.approver.userId.eq(approverId),
                        approvalLine.approvalStatus.in(
                                ApprovalLineStatus.APPROVED,
                                ApprovalLineStatus.REJECTED
                        )
                )
                .fetchOne();

        return toPage(content, pageable, total);
    }

    @Override
    public Page<ApprovalListResponse> findCcByUserId(Long userId, Pageable pageable) {
        List<ApprovalListResponse> content = queryFactory
                .select(approvalListProjection())
                .from(approvalCc)
                .join(approvalCc.approval, approval)
                .join(approval.writer)
                .where(approvalCc.ccUser.userId.eq(userId))
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
                .where(approvalCc.ccUser.userId.eq(userId))
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
