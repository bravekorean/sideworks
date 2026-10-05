package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.entity.ApprovalTemplateCc;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ApprovalTemplateCcRepository extends JpaRepository<ApprovalTemplateCc, Long> {

    @EntityGraph(attributePaths = {"ccUser", "ccUser.department", "ccUser.position"})
    List<ApprovalTemplateCc> findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalTemplateCcIdAsc(Long templateId);

    @EntityGraph(attributePaths = {"ccUser", "ccUser.department", "ccUser.position"})
    List<ApprovalTemplateCc> findAllByApprovalTemplate_ApprovalTemplateIdInOrderByApprovalTemplate_ApprovalTemplateIdAscApprovalTemplateCcIdAsc(
            Collection<Long> templateIds);

    void deleteAllByApprovalTemplate_ApprovalTemplateId(Long templateId);
}
