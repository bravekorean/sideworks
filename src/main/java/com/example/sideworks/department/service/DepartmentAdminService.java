package com.example.sideworks.department.service;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.department.dto.DepartmentCreateRequest;
import com.example.sideworks.department.dto.DepartmentResponse;
import com.example.sideworks.department.dto.DepartmentUpdateRequest;
import com.example.sideworks.department.dto.DepartmentManagerUpdateRequest;
import com.example.sideworks.department.entity.Department;
import com.example.sideworks.department.entity.DepartmentStatus;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.department.repository.DepartmentRepository;
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentAdminService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public Page<DepartmentResponse> findAllDepartments(Pageable pageable) {
        return departmentRepository
                .findAllByOrderByCreatedAtDescDepartmentIdDesc(pageable)
                .map(DepartmentResponse::from);
    }

    @Transactional
    public Long createDepartment(DepartmentCreateRequest request) {
        String departmentName = validateDepartmentName(request.getDepartmentName());

        Department parentDepartment = null;

        if (request.getParentDepartmentId() != null) {
            parentDepartment = findActiveDepartment(request.getParentDepartmentId());
        }

        Department department = Department.create(departmentName.trim(), parentDepartment);

        return departmentRepository.save(department).getDepartmentId();
    }

    @Transactional
    public void updateDepartment(Long departmentId, DepartmentUpdateRequest request) {
        String departmentName = validateDepartmentName(request.getDepartmentName());

        Department department = findActiveDepartment(departmentId);

        Department parentDepartment = null;

        if (request.getParentDepartmentId() != null) {
            parentDepartment = findActiveDepartment(request.getParentDepartmentId());
            validateHierarchy(department, parentDepartment);
        }

        department.update(departmentName, parentDepartment);
    }

    private String validateDepartmentName(String departmentName) {

        if (departmentName == null || departmentName.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        String trimmedName = departmentName.trim();

        if (trimmedName.length() > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        return trimmedName;
    }

    private void validateHierarchy(Department department, Department parentDepartment) {
        Department current = parentDepartment;

        while (current != null) {
            if (current.getDepartmentId().equals(department.getDepartmentId())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            current = current.getParentDepartment();
        }
    }

    @Transactional
    public void updateDepartmentManager(Long departmentId, DepartmentManagerUpdateRequest request) {
        Department department = findActiveDepartment(departmentId);

        Long managerUserId = request.getManagerUserId();

        // null은 현재 부서장의 해제를 의미한다.
        if (managerUserId == null) {
            department.removeManager();
            return;
        }

        User manager = userRepository.findById(managerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (manager.getDepartment() == null || !manager.getDepartment().getDepartmentId().equals(departmentId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (manager.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (manager.getUserRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        department.assignManager(managerUserId);
    }

    private Department findActiveDepartment(Long departmentId) {
        return departmentRepository
                .findByDepartmentIdAndStatus(
                        departmentId,
                        DepartmentStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    @Transactional
    public void deleteDepartment(Long departmentId) {
        Department department = findActiveDepartment(departmentId);

        boolean hasChildDepartments = departmentRepository.existsByParentDepartment_DepartmentIdAndStatusNot(departmentId, DepartmentStatus.DELETED);

        boolean hasAssignedUsers = userRepository.existsByDepartment_DepartmentId(departmentId);

        if (hasChildDepartments || hasAssignedUsers) {
            throw new BusinessException(ErrorCode.DEPARTMENT_IN_USE);
        }

        department.delete();
    }
}
