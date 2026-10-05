package com.example.sideworks.notification.dto;

import com.example.sideworks.notification.entity.Notification;
import com.example.sideworks.notification.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(Long notificationId, Long approvalId, Long approvalTemplateId, NotificationType type,
                                   String title, String message, LocalDateTime createdAt, LocalDateTime readAt) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getNotificationId(),
                notification.getApproval() == null ? null : notification.getApproval().getApprovalId(),
                notification.getApprovalTemplateId(),
                notification.getNotificationType(), notification.getTitle(), notification.getMessage(),
                notification.getCreatedAt(), notification.getReadAt());
    }
}
