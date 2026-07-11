package com.example.sideworks.approval.factory;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.ApprovalActionType;
import com.example.sideworks.approval.entity.ApprovalCc;
import com.example.sideworks.approval.entity.ApprovalHistory;
import com.example.sideworks.approval.entity.ApprovalLine;
import com.example.sideworks.approval.entity.ApprovalLineStatus;
import com.example.sideworks.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ApprovalSubmissionFactory {

    private static final int SUBMISSION_ACTION_STEP = 0;
    private static final int FIRST_APPROVAL_STEP = 1;

    public List<ApprovalLine> createLines(Approval approval, List<User> approvers) {
        List<ApprovalLine> approvalLines = new ArrayList<>();

        for (int index = 0; index < approvers.size(); index++) {
            approvalLines.add(
                    ApprovalLine.create(
                            approval,
                            approvers.get(index),
                            index + FIRST_APPROVAL_STEP,
                            initialLineStatus(index)
                    )
            );
        }

        return approvalLines;
    }

    public List<ApprovalCc> createCcs(Approval approval, List<User> ccUsers) {
        List<ApprovalCc> approvalCcs = new ArrayList<>();

        for (User ccUser : ccUsers) {
            approvalCcs.add(ApprovalCc.create(approval, ccUser));
        }

        return approvalCcs;
    }

    public ApprovalHistory createHistory(Approval approval) {
        return ApprovalHistory.create(
                approval,
                approval.getWriter(),
                SUBMISSION_ACTION_STEP,
                ApprovalActionType.SUBMITTED,
                null
        );
    }

    private ApprovalLineStatus initialLineStatus(int index) {
        return index == 0
                ? ApprovalLineStatus.PENDING
                : ApprovalLineStatus.WAITING;
    }
}
