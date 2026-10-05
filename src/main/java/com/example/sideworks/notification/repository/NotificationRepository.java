package com.example.sideworks.notification.repository;

import com.example.sideworks.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipient_UserIdOrderByCreatedAtDescNotificationIdDesc(
            Long userId, Pageable pageable);

    long countByRecipient_UserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByNotificationIdAndRecipient_UserId(
            Long notificationId, Long userId);
}
