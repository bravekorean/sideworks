package com.example.sideworks.approval.service;

import com.example.sideworks.approval.repository.ApprovalTemplateRepository;
import com.example.sideworks.approval.entity.ApprovalTemplate;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.notification.service.NotificationService;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserRole;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ApprovalTemplateBlockService {
    private final ApprovalTemplateRepository templates;
    private final UserRepository users;
    private final NotificationService notifications;

    // 적용 요청이 409로 롤백되어도 발견된 인사정보 불일치는 남겨야 한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long block(Long templateId, Long expectedVersion) {
        ApprovalTemplate template = templates.findForBlock(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));
        if (!Objects.equals(template.getVersion(), expectedVersion)) {
            throw new BusinessException(ErrorCode.TEMPLATE_STALE);
        }
        if (!template.block()) return template.getVersion();

        Map<Long, User> recipients = new LinkedHashMap<>();
        users.findAllByStatusAndUserRoleInOrderByUserNameAsc(UserStatus.ACTIVE, List.of(UserRole.SUPER_ADMIN))
                .forEach(user -> recipients.put(user.getUserId(), user));
        if (template.getDepartment() != null && template.getDepartment().getManagerUserId() != null) {
            users.findById(template.getDepartment().getManagerUserId())
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .ifPresent(user -> recipients.put(user.getUserId(), user));
        }
        String eventKey = "BLOCKED:" + expectedVersion;
        String message = template.getTemplateName() + " · 결재자 또는 참조자의 부서·계정 상태가 변경됐습니다.";
        recipients.values().forEach(user -> notifications.recordTemplate(templateId, user, eventKey,
                "결재선 템플릿 수정 필요", message.length() > 500 ? message.substring(0, 500) : message));
        templates.flush();
        return template.getVersion();
    }
}
