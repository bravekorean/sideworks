package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.approval.entity.ApprovalStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.example.sideworks.approval.entity.QApproval.approval;
import static com.example.sideworks.approval.entity.QApprovalCc.approvalCc;
import static com.example.sideworks.approval.entity.QApprovalLine.approvalLine;

@RequiredArgsConstructor
public class ApprovalRepositoryImpl implements ApprovalRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Approval> findDraftsByWriterId(Long writerId) {
        return queryFactory
                .selectFrom(approval)
                .join(approval.writer).fetchJoin()
                .where(
                        approval.writer.userId.eq(writerId),
                        approval.approvalStatus.eq(ApprovalStatus.DRAFT)
                )
                .orderBy(approval.createdAt.desc())
                .fetch();
    }

    @Override
    public List<Approval> findSentByWriterId(Long writerId) {
        return queryFactory
                .selectFrom(approval)
                .join(approval.writer).fetchJoin()
                .where(approval.writer.userId.eq(writerId))
                .orderBy(approval.createdAt.desc())
                .fetch();
    }

    @Override
    public List<Approval> findPendingByApproverId(Long approverId) {
        return queryFactory
                .select(approval)
                .from(approvalLine)
                .join(approvalLine.approval, approval)
                .join(approval.writer).fetchJoin()
                .where(
                        approvalLine.approver.userId.eq(approverId),
                        approvalLine.approvalStatus.eq(ApprovalLineStatus.PENDING)
                )
                .orderBy(approval.createdAt.desc())
                .fetch();
    }

    @Override
    public List<Approval> findProcessedByApproverId(Long approverId) {
        return queryFactory
                .select(approval)
                .from(approvalLine)
                .join(approvalLine.approval, approval)
                .join(approval.writer).fetchJoin()
                .where(
                        approvalLine.approver.userId.eq(approverId),
                        approvalLine.approvalStatus.in(
                                ApprovalLineStatus.APPROVED,
                                ApprovalLineStatus.REJECTED
                        )
                )
                .orderBy(approvalLine.processedAt.desc())
                .fetch();
    }

    @Override
    public List<Approval> findCcByUserId(Long userId) {
        return queryFactory
                .select(approval)
                .from(approvalCc)
                .join(approvalCc.approval, approval)
                .join(approval.writer).fetchJoin()
                .where(approvalCc.ccUser.userId.eq(userId))
                .orderBy(approval.createdAt.desc())
                .fetch();
    }
}
