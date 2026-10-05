package com.example.sideworks.approval.service;

import com.example.sideworks.approval.dto.ApprovalTemplateResolveResponse;
import com.example.sideworks.approval.dto.ApprovalTemplateDepartmentResponse;
import com.example.sideworks.approval.dto.ApprovalTemplateResponse;
import com.example.sideworks.approval.dto.ApprovalTemplateSaveRequest;
import com.example.sideworks.approval.entity.*;
import com.example.sideworks.approval.repository.ApprovalTemplateCcRepository;
import com.example.sideworks.approval.repository.ApprovalTemplateLineRepository;
import com.example.sideworks.approval.repository.ApprovalTemplateRepository;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.department.entity.Department;
import com.example.sideworks.department.entity.DepartmentStatus;
import com.example.sideworks.department.repository.DepartmentRepository;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.dto.UserDirectoryResponse;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalTemplateService {
    private final ApprovalTemplateRepository templates;
    private final ApprovalTemplateLineRepository lines;
    private final ApprovalTemplateCcRepository ccs;
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final ApprovalTemplateBlockService blocker;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<UserDirectoryResponse> manageableMembers(String loginId, ApprovalTemplateScope scope,
                                                          Long departmentId, boolean reference, Pageable pageable) {
        User actor = actor(loginId);
        if (scope == null || (scope == ApprovalTemplateScope.COMMON && departmentId != null)
                || (scope == ApprovalTemplateScope.DEPARTMENT && departmentId == null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Department department = scope == ApprovalTemplateScope.DEPARTMENT
                ? departments.findByDepartmentIdAndStatus(departmentId, DepartmentStatus.ACTIVE)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND))
                : null;
        requireManager(actor, scope, department);
        Page<User> members = reference || department == null
                ? users.findAllByStatusOrderByUserNameAscUserIdAsc(UserStatus.ACTIVE, pageable)
                : users.findAllByStatusAndDepartment_DepartmentIdOrderByUserNameAscUserIdAsc(
                        UserStatus.ACTIVE, department.getDepartmentId(), pageable);
        return members.map(UserDirectoryResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ApprovalTemplateDepartmentResponse> manageableDepartments(String loginId) {
        User actor = actor(loginId);
        List<Department> owned = actor.getUserRole() == UserRole.SUPER_ADMIN
                ? departments.findAllByStatusOrderByDepartmentNameAscDepartmentIdAsc(DepartmentStatus.ACTIVE)
                : departments.findAllByManagerUserId(actor.getUserId()).stream()
                    .filter(department -> department.getStatus() == DepartmentStatus.ACTIVE).toList();
        return owned.stream().map(department -> new ApprovalTemplateDepartmentResponse(
                department.getDepartmentId(), department.getDepartmentName())).toList();
    }

    @Transactional
    public Page<ApprovalTemplateResponse> available(String loginId, Pageable pageable) {
        User actor = actor(loginId);
        Long departmentId = departmentId(actor);
        Page<ApprovalTemplate> page = templates.findVisible(ApprovalTemplateStatus.ACTIVE,
                ApprovalTemplateScope.COMMON, departmentId, pageable);
        return responses(page);
    }

    @Transactional
    public Page<ApprovalTemplateResponse> manageable(String loginId, Pageable pageable) {
        User actor = actor(loginId);
        List<Long> departmentIds = managedDepartmentIds(actor);
        if (actor.getUserRole() != UserRole.SUPER_ADMIN && departmentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.TEMPLATE_FORBIDDEN);
        }
        // 한 사용자가 여러 부서를 맡을 수 있으므로 관리 범위는 현재 manager_user_id로 판별한다.
        Page<ApprovalTemplate> page = templates.findManageable(actor.getUserRole() == UserRole.SUPER_ADMIN,
                departmentIds.isEmpty() ? List.of(-1L) : departmentIds, pageable);
        return responses(page);
    }

    @Transactional
    public ApprovalTemplateResponse manageDetail(String loginId, Long id) {
        User actor = actor(loginId);
        ApprovalTemplate template = template(id);
        requireManager(actor, template.getScope(), template.getDepartment());
        return response(template, lines.findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalStepAsc(id),
                ccs.findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalTemplateCcIdAsc(id));
    }

    @Transactional
    public ApprovalTemplateResolveResponse resolve(String loginId, Long id) {
        User actor = actor(loginId);
        ApprovalTemplate template = template(id);
        requireVisible(actor, template);
        if (template.getStatus() != ApprovalTemplateStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.TEMPLATE_INACTIVE);
        }
        List<ApprovalTemplateLine> approvers = lines.findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalStepAsc(id);
        List<ApprovalTemplateCc> ccUsers = ccs.findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalTemplateCcIdAsc(id);
        if (!membersValid(approvers, ccUsers)) {
            blocker.block(id, template.getVersion());
            throw new BusinessException(ErrorCode.TEMPLATE_BLOCKED);
        }
        if (template.getValidationStatus() != ApprovalTemplateValidationStatus.VALID) {
            throw new BusinessException(ErrorCode.TEMPLATE_BLOCKED);
        }
        List<Long> approverIds = approvers.stream().map(line -> line.getApprover().getUserId()).toList();
        List<Long> ccIds = ccUsers.stream().map(cc -> cc.getCcUser().getUserId()).toList();
        return new ApprovalTemplateResolveResponse(id, template.getVersion(), approverIds, ccIds,
                approverIds.contains(actor.getUserId()),
                approvers.stream().map(line -> member(line.getApprover(), line.getApprovalStep())).toList(),
                ccUsers.stream().map(cc -> member(cc.getCcUser(), 0)).toList());
    }

    @Transactional
    public ApprovalTemplateResponse create(String loginId, ApprovalTemplateSaveRequest request) {
        User actor = actor(loginId);
        Department department = requestedDepartment(request);
        requireManager(actor, request.scope(), department);
        Members members = validatedMembers(request, department);
        ApprovalTemplate template = ApprovalTemplate.create(request.templateName(), request.description(),
                request.scope(), department, actor);
        if (request.status() == ApprovalTemplateStatus.INACTIVE) {
            template.updateAfterValidation(request.templateName(), request.description(), request.scope(),
                    department, ApprovalTemplateStatus.INACTIVE, actor);
        }
        templates.save(template);
        saveMembers(template, members);
        templates.flush();
        return response(template, lines.findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalStepAsc(template.getApprovalTemplateId()),
                ccs.findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalTemplateCcIdAsc(template.getApprovalTemplateId()));
    }

    @Transactional
    public ApprovalTemplateResponse update(String loginId, Long id, ApprovalTemplateSaveRequest request) {
        User actor = actor(loginId);
        ApprovalTemplate template = template(id);
        requireManager(actor, template.getScope(), template.getDepartment());
        requireVersion(template, request.version());
        Department department = requestedDepartment(request);
        requireManager(actor, request.scope(), department);
        Members members = validatedMembers(request, department);
        entityManager.lock(template, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        lines.deleteAllByApprovalTemplate_ApprovalTemplateId(id);
        ccs.deleteAllByApprovalTemplate_ApprovalTemplateId(id);
        entityManager.flush();
        template.updateAfterValidation(request.templateName(), request.description(), request.scope(), department,
                request.status() == null ? ApprovalTemplateStatus.ACTIVE : request.status(), actor);
        saveMembers(template, members);
        entityManager.flush();
        entityManager.refresh(template);
        return response(template, lines.findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalStepAsc(id),
                ccs.findAllByApprovalTemplate_ApprovalTemplateIdOrderByApprovalTemplateCcIdAsc(id));
    }

    @Transactional
    public void delete(String loginId, Long id, Long version) {
        User actor = actor(loginId);
        ApprovalTemplate template = template(id);
        requireManager(actor, template.getScope(), template.getDepartment());
        requireVersion(template, version);
        templates.delete(template); // DB FK가 템플릿 하위 두 테이블만 CASCADE한다.
        templates.flush();
    }

    // 일반 문서 상신 시 원본 버전·권한·인사정보를 다시 검증한다. 수동 결재선이면 호출하지 않는다.
    @Transactional
    public void validateSubmission(String loginId, Long id, Long version) {
        ApprovalTemplateResolveResponse resolved = resolve(loginId, id);
        if (!Objects.equals(resolved.version(), version)) {
            throw new BusinessException(ErrorCode.TEMPLATE_STALE);
        }
        // 템플릿 수정과 상신이 겹치면 커밋 시 버전 변경을 감지한다.
        entityManager.lock(template(id), LockModeType.OPTIMISTIC);
    }

    private Page<ApprovalTemplateResponse> responses(Page<ApprovalTemplate> page) {
        List<Long> ids = page.getContent().stream().map(ApprovalTemplate::getApprovalTemplateId).toList();
        if (ids.isEmpty()) return page.map(t -> null);
        Map<Long, List<ApprovalTemplateLine>> lineGroups = lines
                .findAllByApprovalTemplate_ApprovalTemplateIdInOrderByApprovalTemplate_ApprovalTemplateIdAscApprovalStepAsc(ids)
                .stream().collect(Collectors.groupingBy(line -> line.getApprovalTemplate().getApprovalTemplateId()));
        Map<Long, List<ApprovalTemplateCc>> ccGroups = ccs
                .findAllByApprovalTemplate_ApprovalTemplateIdInOrderByApprovalTemplate_ApprovalTemplateIdAscApprovalTemplateCcIdAsc(ids)
                .stream().collect(Collectors.groupingBy(cc -> cc.getApprovalTemplate().getApprovalTemplateId()));
        return page.map(template -> response(template,
                lineGroups.getOrDefault(template.getApprovalTemplateId(), List.of()),
                ccGroups.getOrDefault(template.getApprovalTemplateId(), List.of())));
    }

    private ApprovalTemplateResponse response(ApprovalTemplate template, List<ApprovalTemplateLine> approvers,
                                              List<ApprovalTemplateCc> ccUsers) {
        boolean valid = membersValid(approvers, ccUsers);
        Long version = valid ? template.getVersion() : blocker.block(template.getApprovalTemplateId(), template.getVersion());
        Department department = template.getDepartment();
        return new ApprovalTemplateResponse(template.getApprovalTemplateId(), template.getTemplateName(),
                template.getDescription(), template.getScope(), departmentId(department),
                department == null ? null : department.getDepartmentName(), template.getStatus(),
                valid ? template.getValidationStatus() : ApprovalTemplateValidationStatus.BLOCKED, version,
                valid ? (template.getValidationStatus() == ApprovalTemplateValidationStatus.BLOCKED
                        ? "관리자가 구성원을 재검증해야 합니다." : null)
                        : "결재자 또는 참조자의 부서·계정 상태가 변경됐습니다.",
                approvers.stream().map(line -> member(line.getApprover(), line.getApprovalStep())).toList(),
                ccUsers.stream().map(cc -> member(cc.getCcUser(), 0)).toList());
    }

    private ApprovalTemplateResponse.Member member(User user, int step) {
        return new ApprovalTemplateResponse.Member(user.getUserId(), user.getUserName(),
                user.getDepartment() == null ? null : user.getDepartment().getDepartmentName(),
                user.getPosition() == null ? null : user.getPosition().getPositionName(), user.getStatus().name(), step);
    }

    private boolean membersValid(List<ApprovalTemplateLine> approvers, List<ApprovalTemplateCc> ccUsers) {
        if (approvers.isEmpty()) return false;
        for (ApprovalTemplateLine line : approvers) {
            if (!sameDepartment(line.getBaselineDepartmentId(), line.getApprover())
                    || !eligibleApprover(line.getApprover())) return false;
        }
        for (ApprovalTemplateCc cc : ccUsers) {
            if (!sameDepartment(cc.getBaselineDepartmentId(), cc.getCcUser())
                    || cc.getCcUser().getStatus() != UserStatus.ACTIVE) return false;
        }
        return true;
    }

    private boolean sameDepartment(Long baseline, User user) {
        return Objects.equals(baseline, departmentId(user.getDepartment()));
    }

    private boolean eligibleApprover(User user) {
        return user.getStatus() == UserStatus.ACTIVE;
    }

    private Members validatedMembers(ApprovalTemplateSaveRequest request, Department department) {
        if (request == null || request.approverIds() == null || request.approverIds().isEmpty()) {
            throw new BusinessException(ErrorCode.TEMPLATE_INVALID_MEMBERS);
        }
        List<Long> approverIds = request.approverIds();
        List<Long> ccIds = request.ccUserIds() == null ? List.of() : request.ccUserIds();
        if (approverIds.stream().anyMatch(Objects::isNull) || ccIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(approverIds).size() != approverIds.size()
                || new HashSet<>(ccIds).size() != ccIds.size()
                || ccIds.stream().anyMatch(new HashSet<>(approverIds)::contains)) {
            throw new BusinessException(ErrorCode.TEMPLATE_INVALID_MEMBERS);
        }
        List<Long> allIds = new ArrayList<>(approverIds);
        allIds.addAll(ccIds);
        Map<Long, User> byId = users.findAllByUserIdIn(allIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        if (byId.size() != allIds.size()) throw new BusinessException(ErrorCode.TEMPLATE_INVALID_MEMBERS);
        if (department != null && approverIds.stream().map(byId::get).anyMatch(user ->
                !Objects.equals(department.getDepartmentId(), departmentId(user)))) {
            throw new BusinessException(ErrorCode.TEMPLATE_INVALID_MEMBERS);
        }
        List<User> approvers = approverIds.stream().map(byId::get).toList();
        List<User> ccUsers = ccIds.stream().map(byId::get).toList();
        if (approvers.stream().anyMatch(user -> !eligibleApprover(user))
                || ccUsers.stream().anyMatch(user -> user.getStatus() != UserStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.TEMPLATE_INVALID_MEMBERS);
        }
        return new Members(approvers, ccUsers);
    }

    private void saveMembers(ApprovalTemplate template, Members members) {
        List<ApprovalTemplateLine> savedLines = new ArrayList<>();
        for (int i = 0; i < members.approvers().size(); i++) {
            savedLines.add(ApprovalTemplateLine.create(template, members.approvers().get(i), i + 1));
        }
        lines.saveAll(savedLines);
        ccs.saveAll(members.ccUsers().stream().map(user -> ApprovalTemplateCc.create(template, user)).toList());
    }

    private Department requestedDepartment(ApprovalTemplateSaveRequest request) {
        if (request == null || request.scope() == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (request.scope() == ApprovalTemplateScope.COMMON) {
            if (request.departmentId() != null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            return null;
        }
        if (request.departmentId() == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return departments.findByDepartmentIdAndStatus(request.departmentId(), DepartmentStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    private void requireManager(User actor, ApprovalTemplateScope scope, Department department) {
        if (actor.getUserRole() == UserRole.SUPER_ADMIN) return;
        if (scope != ApprovalTemplateScope.DEPARTMENT || department == null
                || department.getStatus() != DepartmentStatus.ACTIVE
                || !Objects.equals(department.getManagerUserId(), actor.getUserId())) {
            throw new BusinessException(ErrorCode.TEMPLATE_FORBIDDEN);
        }
    }

    private void requireVisible(User actor, ApprovalTemplate template) {
        if (template.getScope() == ApprovalTemplateScope.COMMON) return;
        if (!Objects.equals(departmentId(actor), departmentId(template.getDepartment()))) {
            throw new BusinessException(ErrorCode.TEMPLATE_FORBIDDEN);
        }
    }

    private void requireVersion(ApprovalTemplate template, Long version) {
        if (version == null || !Objects.equals(template.getVersion(), version)) {
            throw new BusinessException(ErrorCode.TEMPLATE_STALE);
        }
    }

    private List<Long> managedDepartmentIds(User actor) {
        return departments.findAllByManagerUserId(actor.getUserId()).stream()
                .filter(department -> department.getStatus() == DepartmentStatus.ACTIVE)
                .map(Department::getDepartmentId).toList();
    }

    private ApprovalTemplate template(Long id) {
        return templates.findWithDepartment(id).orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));
    }

    private User actor(String loginId) {
        return users.findByLoginId(loginId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Long departmentId(User user) { return departmentId(user.getDepartment()); }
    private Long departmentId(Department department) { return department == null ? null : department.getDepartmentId(); }

    private record Members(List<User> approvers, List<User> ccUsers) {}
}
