package com.example.sideworks.approval.repository;

import com.example.sideworks.approval.entity.ApprovalDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalDocumentTypeRepository extends JpaRepository<ApprovalDocumentType, Long> {

    List<ApprovalDocumentType> findByActiveTrueOrderBySortOrderAscApprovalDocumentTypeIdAsc();
    Optional<ApprovalDocumentType> findByTypeCode(String typeCode);
    List<ApprovalDocumentType> findAllByOrderBySortOrderAscApprovalDocumentTypeIdAsc();
    boolean existsByTypeCodeIgnoreCase(String typeCode);
    boolean existsByTypeNameIgnoreCase(String typeName);
    boolean existsByTypeNameIgnoreCaseAndApprovalDocumentTypeIdNot(String typeName, Long id);
}
