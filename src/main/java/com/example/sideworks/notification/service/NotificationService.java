package com.example.sideworks.notification.service;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.notification.dto.NotificationResponse;
import com.example.sideworks.notification.entity.Notification;
import com.example.sideworks.notification.entity.NotificationType;
import com.example.sideworks.notification.event.NotificationCreated;
import com.example.sideworks.notification.repository.NotificationRepository;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final ApplicationEventPublisher publisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Approval approval, User recipient, NotificationType type,
                       String eventKey, String title, String message) {
        if (eventKey == null || eventKey.isBlank() || eventKey.length() > 64
                || title == null || title.isBlank() || title.length() > 200
                || message == null || message.isBlank() || message.length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Notification saved = notifications.save(Notification.create(recipient, approval, type,
                eventKey, title, message));
        publisher.publishEvent(new NotificationCreated(recipient.getUserId(), NotificationResponse.from(saved)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordTemplate(Long templateId, User recipient, String eventKey, String title, String message) {
        if (templateId == null || recipient == null || eventKey == null || eventKey.isBlank() || eventKey.length() > 64
                || title == null || title.isBlank() || title.length() > 200
                || message == null || message.isBlank() || message.length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Notification saved = notifications.save(Notification.createTemplate(recipient, templateId, eventKey, title, message));
        publisher.publishEvent(new NotificationCreated(recipient.getUserId(), NotificationResponse.from(saved)));
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(String loginId, Pageable pageable) {
        Long userId = currentUserId(loginId);
        return notifications.findByRecipient_UserIdOrderByCreatedAtDescNotificationIdDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String loginId) {
        return notifications.countByRecipient_UserIdAndReadAtIsNull(currentUserId(loginId));
    }

    @Transactional
    public void markRead(String loginId, Long notificationId) {
        Long userId = currentUserId(loginId);
        Notification notification = notifications.findByNotificationIdAndRecipient_UserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Long currentUserId(String loginId) {
        return users.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)).getUserId();
    }
}
