package com.example.sideworks.approval.service;

import com.example.sideworks.approval.dto.ApprovalDocumentTypeAdminResponse;
import com.example.sideworks.approval.dto.ApprovalDocumentTypeCreateRequest;
import com.example.sideworks.approval.dto.ApprovalDocumentTypeUpdateRequest;
import com.example.sideworks.approval.entity.ApprovalDocumentType;
import com.example.sideworks.approval.entity.DocumentBehaviorType;
import com.example.sideworks.approval.repository.ApprovalDocumentTypeRepository;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalDocumentTypeAdminService {
    private static final int MAX_TEMPLATE_LENGTH = 20_000;
    private final ApprovalDocumentTypeRepository repository;

    public List<ApprovalDocumentTypeAdminResponse> findAll() {
        return repository.findAllByOrderBySortOrderAscApprovalDocumentTypeIdAsc().stream()
                .map(ApprovalDocumentTypeAdminResponse::from).toList();
    }

    @Transactional
    public ApprovalDocumentTypeAdminResponse create(ApprovalDocumentTypeCreateRequest request) {
        if (request == null) throw invalidRequest();
        String code = request.getTypeCode() == null ? "" : request.getTypeCode().trim();
        if (!code.matches("[A-Z][A-Z0-9_]{0,29}")) throw invalidRequest();
        String name = validateFields(request.getTypeName(), request.getDescription(), request.getContentTemplate(), request.getSortOrder());
        if (repository.existsByTypeCodeIgnoreCase(code) || repository.existsByTypeNameIgnoreCase(name)) {
            throw new BusinessException(ErrorCode.DOCUMENT_TYPE_DUPLICATE);
        }
        return save(ApprovalDocumentType.createGeneral(code, name, normalizeDescription(request.getDescription()),
                request.getContentTemplate(), request.getSortOrder()));
    }

    @Transactional
    public ApprovalDocumentTypeAdminResponse update(Long id, ApprovalDocumentTypeUpdateRequest request) {
        if (id == null || id <= 0 || request == null || request.getActive() == null
                || request.getVersion() == null || request.getVersion() < 0) throw invalidRequest();
        String name = validateFields(request.getTypeName(), request.getDescription(), request.getContentTemplate(), request.getSortOrder());
        ApprovalDocumentType type = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_TYPE_NOT_FOUND));
        if (type.isSystem() || type.getBehaviorType() != DocumentBehaviorType.GENERAL) {
            throw new BusinessException(ErrorCode.DOCUMENT_TYPE_SYSTEM_PROTECTED);
        }
        if (!Objects.equals(type.getVersion(), request.getVersion())) {
            throw new BusinessException(ErrorCode.DOCUMENT_TYPE_STALE);
        }
        if (repository.existsByTypeNameIgnoreCaseAndApprovalDocumentTypeIdNot(name, id)) {
            throw new BusinessException(ErrorCode.DOCUMENT_TYPE_DUPLICATE);
        }
        type.updateGeneral(name, normalizeDescription(request.getDescription()), request.getContentTemplate(),
                request.getSortOrder(), request.getActive());
        return save(type);
    }

    private ApprovalDocumentTypeAdminResponse save(ApprovalDocumentType type) {
        try {
            // UNIQUE·낙관적 잠금 충돌을 트랜잭션 종료 전에 감지해 업무 오류로 응답한다.
            return ApprovalDocumentTypeAdminResponse.from(repository.saveAndFlush(type));
        } catch (OptimisticLockingFailureException exception) {
            throw new BusinessException(ErrorCode.DOCUMENT_TYPE_STALE);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DOCUMENT_TYPE_DUPLICATE);
        }
    }

    private String validateFields(String name, String description, String template, Integer sortOrder) {
        if (name == null || name.isBlank() || name.trim().length() > 100
                || (description != null && description.length() > 500)
                || (template != null && template.length() > MAX_TEMPLATE_LENGTH)
                || sortOrder == null || sortOrder < 0) throw invalidRequest();
        return name.trim();
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private BusinessException invalidRequest() {
        return new BusinessException(ErrorCode.INVALID_REQUEST);
    }
}
