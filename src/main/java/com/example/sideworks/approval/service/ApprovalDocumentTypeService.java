package com.example.sideworks.approval.service;

import com.example.sideworks.approval.dto.ApprovalDocumentTypeResponse;
import com.example.sideworks.approval.entity.ApprovalDocumentType;
import com.example.sideworks.approval.entity.DocumentBehaviorType;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.approval.repository.ApprovalDocumentTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalDocumentTypeService {

    private final ApprovalDocumentTypeRepository repository;

    public ApprovalDocumentType requireActiveGeneral(Long id) {
        if (id == null || id <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        ApprovalDocumentType type = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_FOUND));
        validateActive(type);
        if (type.getBehaviorType() != DocumentBehaviorType.GENERAL) {
            throw new BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_ALLOWED);
        }
        return type;
    }

    public ApprovalDocumentType requireAttendanceCorrection() {
        ApprovalDocumentType type = repository.findByTypeCode("ATTENDANCE_CORRECTION")
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_FOUND));
        validateActive(type);
        if (!type.isSystem() || type.getBehaviorType() != DocumentBehaviorType.ATTENDANCE_CORRECTION) {
            throw new BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_ALLOWED);
        }
        return type;
    }

    public ApprovalDocumentType requireSystemType(String code, DocumentBehaviorType behavior) {
        ApprovalDocumentType type = repository.findByTypeCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_FOUND));
        validateActive(type);
        if (!type.isSystem() || type.getBehaviorType() != behavior) {
            throw new BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_ALLOWED);
        }
        return type;
    }

    private void validateActive(ApprovalDocumentType type) {
        if (!type.isActive()) throw new BusinessException(ErrorCode.DOCUMENT_TYPE_INACTIVE);
    }

    public List<ApprovalDocumentTypeResponse> getActiveDocumentTypes() {
        return repository
                .findByActiveTrueOrderBySortOrderAscApprovalDocumentTypeIdAsc()
                .stream()
                .map(ApprovalDocumentTypeResponse::from)
                .toList();
    }
}
