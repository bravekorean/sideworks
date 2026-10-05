package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.entity.ApprovalTemplateLine;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ApprovalTemplateLineRepository extends JpaRepository<ApprovalTemplateLine, Long> {

    @EntityGraph(attributePaths = {"approver", "approver.department", "approver.position"})
    List<ApprovalTemplateLine> findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalStepAsc(Long templateId);

    @EntityGraph(attributePaths = {"approver", "approver.department", "approver.position"})
    List<ApprovalTemplateLine> findAllByApprovalTemplate_ApprovalTemplateIdInOrderByApprovalTemplate_ApprovalTemplateIdAscApprovalStepAsc(
            Collection<Long> templateIds);

    void deleteAllByApprovalTemplate_ApprovalTemplateId(Long templateId);
}
