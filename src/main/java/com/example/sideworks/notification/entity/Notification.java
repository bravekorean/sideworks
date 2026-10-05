package com.example.sideworks.notification.entity;

import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.common.entity.BaseCreatedEntity;
import com.example.sideworks.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "notificationtbl")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseCreatedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_id")
    private Approval approval;

    // 원본 템플릿은 물리 삭제될 수 있으므로 FK 없이 당시 ID만 보존한다.
    @Column(name = "approval_template_id")
    private Long approvalTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 40)
    private NotificationType notificationType;

    @Column(name = "event_key", nullable = false, length = 64)
    private String eventKey;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public static Notification create(User recipient, Approval approval, NotificationType type, String eventKey, String title, String message) {
        Notification notification = new Notification();
        notification.recipient = Objects.requireNonNull(recipient);
        notification.approval = Objects.requireNonNull(approval);
        notification.notificationType = Objects.requireNonNull(type);
        notification.eventKey = Objects.requireNonNull(eventKey);
        notification.title = Objects.requireNonNull(title);
        notification.message = Objects.requireNonNull(message);
        return notification;
    }

    public static Notification createTemplate(User recipient, Long templateId, String eventKey,
                                              String title, String message) {
        Notification notification = new Notification();
        notification.recipient = Objects.requireNonNull(recipient);
        notification.approvalTemplateId = Objects.requireNonNull(templateId);
        notification.notificationType = NotificationType.APPROVAL_TEMPLATE_BLOCKED;
        notification.eventKey = Objects.requireNonNull(eventKey);
        notification.title = Objects.requireNonNull(title);
        notification.message = Objects.requireNonNull(message);
        return notification;
    }

    public void markRead(LocalDateTime now) {
        if (readAt == null) {
            readAt = Objects.requireNonNull(now);
        }
    }
}
