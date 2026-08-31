package com.example.sideworks.user.service;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.department.entity.Department;
import com.example.sideworks.department.repository.DepartmentRepository;
import com.example.sideworks.position.entity.Position;
import com.example.sideworks.position.repository.PositionRepository;
import com.example.sideworks.user.dto.UserAssignmentRequest;
import com.example.sideworks.user.dto.UserCreateRequest;
import com.example.sideworks.user.dto.UserCreateResponse;
import com.example.sideworks.user.dto.UserDetailResponse;
import com.example.sideworks.user.dto.UserRoleUpdateRequest;
import com.example.sideworks.user.dto.UserStatusUpdateRequest;
import com.example.sideworks.user.dto.UserSummaryResponse;
import com.example.sideworks.user.dto.UserUpdateRequest;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAdminService {

    private final UserRepository userRepository;

    private final DepartmentRepository departmentRepository;

    private final PositionRepository positionRepository;

    private final PasswordEncoder passwordEncoder;

    private final EmployeeNumberGenerator employeeNumberGenerator;

    // 전체 사용자를 최신 생성일 기준으로 조회한다.
    public Page<UserSummaryResponse> findAllUsers(Pageable pageable) {
        return userRepository
                .findAllByOrderByCreatedAtDescUserIdDesc(pageable)
                .map(UserSummaryResponse::from);
    }

    // 사용자 고유 ID로 상세 정보를 조회한다.
    public UserDetailResponse findUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return UserDetailResponse.from(user);
    }

    // 부서 또는 직급이 아직 배정되지 않은 사용자 목록을 조회한다.
    public Page<UserSummaryResponse> findUnassignedUsers(Pageable pageable) {
        return userRepository
                .findAllByDepartmentIsNullOrPositionIsNullOrderByCreatedAtDescUserIdDesc(pageable)
                .map(UserSummaryResponse::from);
    }

    // 관리자가 신규 사용자를 생성한다. SUPER_ADMIN은 초기 DB 계정으로만 관리한다.
    @Transactional
    public UserCreateResponse createUser(UserCreateRequest request) {

        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (request.getJobFamily() == null || request.getHireDate() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (request.getUserRole() == UserRole.SUPER_ADMIN) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
        }

        Position position = null;
        if (request.getPositionId() != null) {
            position = positionRepository.findById(request.getPositionId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.POSITION_NOT_FOUND));
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        String employeeNo = employeeNumberGenerator.generate(
                request.getJobFamily(),
                request.getHireDate()
        );

        User user = User.create(
                request.getLoginId(),
                encodedPassword,
                request.getUserName(),
                request.getUserEmail(),
                request.getUserPhone(),
                employeeNo,
                request.getJobFamily(),
                request.getHireDate(),
                department,
                position,
                request.getUserRole(),
                request.getStatus()
        );

        Long userId = userRepository.save(user).getUserId();

        log.info("Admin user created. userId={}, loginId={}, employeeNo={}, role={}, status={}", userId, request.getLoginId(), employeeNo, request.getUserRole(), request.getStatus());

        return new UserCreateResponse(userId, employeeNo);
    }

    // 부서와 직급은 각각 독립적으로 배정될 수 있으므로 요청에 포함된 값만 수정한다.
    @Transactional
    public void assignUser(Long userId, UserAssignmentRequest request) {

        if (request.getDepartmentId() == null && request.getPositionId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId()).orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND));
            user.assignDepartment(department);
        }

        if (request.getPositionId() != null) {
            Position position = positionRepository.findById(request.getPositionId()).orElseThrow(() -> new BusinessException(ErrorCode.POSITION_NOT_FOUND));

            user.assignPosition(position);
        }

        log.info("Admin user assignment updated. userId={}, departmentId={}, positionId={}", userId, request.getDepartmentId(), request.getPositionId());
    }

    @Transactional
    public void updateUser(Long userId, UserUpdateRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.updateBasicInfo(request.getUserName(), request.getUserEmail(), request.getUserPhone());

        log.info("Admin user updated. userId={}", userId);
    }

    @Transactional
    public void changeUserStatus(Long userId, UserStatusUpdateRequest request) {

        if (request.getStatus() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.changeStatus(request.getStatus());

        log.info("Admin user status changed. userId={}, status={}", userId, request.getStatus());
    }

    @Transactional
    public void changeUserRole(Long userId, UserRoleUpdateRequest request) {

        if (request.getUserRole() == null || request.getUserRole() == UserRole.SUPER_ADMIN) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.changeRole(request.getUserRole());

        log.info("Admin user role changed. userId={}, role={}", userId, request.getUserRole());
    }
}
