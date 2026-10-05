package com.example.sideworks.notification.controller;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.notification.dto.NotificationResponse;
import com.example.sideworks.notification.service.NotificationService;
import com.example.sideworks.notification.service.NotificationStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    private final NotificationStream stream;

    @GetMapping
    public Page<NotificationResponse> list(Authentication authentication,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return notifications.list(authentication.getName(), PageRequest.of(page, size));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication authentication) {
        return Map.of("count", notifications.unreadCount(authentication.getName()));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(Authentication authentication, @PathVariable Long notificationId) {
        notifications.markRead(authentication.getName(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return stream.connect(notifications.currentUserId(authentication.getName()));
    }
}
