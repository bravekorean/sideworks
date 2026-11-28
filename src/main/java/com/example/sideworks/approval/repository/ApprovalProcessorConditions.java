package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.entity.DocumentBehaviorType;
import com.example.sideworks.approval.entity.QApproval;
import com.example.sideworks.approval.entity.QApprovalCc;
import com.example.sideworks.approval.entity.QApprovalDelegation;
import com.example.sideworks.approval.entity.QApprovalDocumentType;
import com.example.sideworks.approval.entity.QApprovalLine;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;

import java.time.LocalDate;

/**
 * 대기 단계의 실제 처리자 규칙을 조회 쿼리용 조건으로 만든다.
 * 대기함 목록·건수, 대시보드, 상세 접근·처리 가능 여부, 관리자 차단 목록이 모두 이 조건을 쓴다.
 * 처리 API의 같은 규칙은 ApprovalDelegationService.resolveProcessor에 있다(잠금 읽기가 필요해서 분리).
 *
 * 규칙: 원 결재자에게 오늘(서버 시간대 기준 날짜) 유효한 위임이 있고, 대결자가 ACTIVE이며
 * 문서의 다른 참여자와 겹치지 않으면 대결자가 처리한다. 아니면 원 결재자가 처리한다.
 * 여러 조건을 한 쿼리 안에서 함께 쓰므로 별칭(alias)이 겹치지 않도록 호출마다 이름을 받는다.
 */
final class ApprovalProcessorConditions {

    private ApprovalProcessorConditions() {
    }

    /** userId가 지금 이 대기 단계를 처리하는 사람인가. 단계 상태(PENDING)는 호출하는 쪽에서 건다. */
    static BooleanExpression isCurrentProcessor(QApprovalLine line, QApproval doc, Long userId,
                                                LocalDate today, String alias) {
        BooleanExpression delegatedToUser = effectiveDelegation(line, doc, today, alias + "To", userId);
        BooleanExpression originalWithoutDelegation = line.approver.userId.eq(userId)
                .and(effectiveDelegation(line, doc, today, alias + "None", null).not());
        return delegatedToUser.or(originalWithoutDelegation);
    }

    /** 이 대기 단계가 오늘 delegateeId에게 넘어가 있는가. */
    static BooleanExpression isDelegatedTo(QApprovalLine line, QApproval doc, Long delegateeId,
                                           LocalDate today, String alias) {
        return effectiveDelegation(line, doc, today, alias, delegateeId);
    }

    /** 이 대기 단계가 오늘 대결자에게 넘어가 있는가. */
    static BooleanExpression isDelegated(QApprovalLine line, QApproval doc, LocalDate today, String alias) {
        return effectiveDelegation(line, doc, today, alias, null);
    }

    /** 위임 행 d가 이 단계의 원 결재자에게 오늘 유효하고 충돌이 없는가. 대결자 이름을 직접 조회할 때 쓴다. */
    static BooleanExpression appliesTo(QApprovalDelegation d, QApprovalLine line, QApproval doc,
                                       LocalDate today, String alias) {
        return activeFor(d, line, today).and(noConflict(d, line, doc, alias));
    }

    private static BooleanExpression effectiveDelegation(QApprovalLine line, QApproval doc, LocalDate today,
                                                         String alias, Long delegateeId) {
        QApprovalDelegation d = new QApprovalDelegation(alias + "Delegation");
        return JPAExpressions.selectOne()
                .from(d)
                .where(
                        appliesTo(d, line, doc, today, alias),
                        delegateeId == null ? null : d.delegatee.userId.eq(delegateeId)
                )
                .exists();
    }

    // 시작일·종료일은 양 끝을 포함한다. today는 호출하는 쪽이 애플리케이션 Clock으로 계산해 넘긴다(DB CURDATE 미사용).
    private static BooleanExpression activeFor(QApprovalDelegation d, QApprovalLine line, LocalDate today) {
        return d.delegator.userId.eq(line.approver.userId)
                .and(d.canceledAt.isNull())
                .and(d.startDate.loe(today))
                .and(d.endDate.goe(today))
                .and(d.delegatee.status.eq(UserStatus.ACTIVE));
    }

    // 대결자가 작성자·참조자·다른 단계 결재자·다른 단계 실제 처리자이거나,
    // 근태 정정 문서인데 인사 권한이 없으면 원 결재자가 처리한다.
    private static BooleanExpression noConflict(QApprovalDelegation d, QApprovalLine line, QApproval doc,
                                                String alias) {
        QApprovalCc cc = new QApprovalCc(alias + "Cc");
        QApprovalLine otherLine = new QApprovalLine(alias + "OtherLine");
        QApprovalDocumentType type = new QApprovalDocumentType(alias + "Type");

        BooleanExpression isCc = JPAExpressions.selectOne().from(cc)
                .where(cc.approval.approvalId.eq(doc.approvalId),
                        cc.ccUser.userId.eq(d.delegatee.userId))
                .exists();
        BooleanExpression isOtherParticipant = JPAExpressions.selectOne().from(otherLine)
                .where(otherLine.approval.approvalId.eq(doc.approvalId),
                        otherLine.approvalLineId.ne(line.approvalLineId),
                        otherLine.approver.userId.eq(d.delegatee.userId)
                                .or(otherLine.processedBy.userId.eq(d.delegatee.userId)))
                .exists();
        BooleanExpression isAttendanceCorrection = JPAExpressions.selectOne().from(type)
                .where(type.approvalDocumentTypeId.eq(doc.documentType.approvalDocumentTypeId),
                        type.behaviorType.eq(DocumentBehaviorType.ATTENDANCE_CORRECTION))
                .exists();

        return doc.writer.userId.ne(d.delegatee.userId)
                .and(isCc.not())
                .and(isOtherParticipant.not())
                .and(isAttendanceCorrection.not()
                        .or(d.delegatee.userRole.in(UserRole.HR_MANAGER, UserRole.SUPER_ADMIN)));
    }
}
