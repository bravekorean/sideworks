package com.example.sideworks.notification.event;

import com.example.sideworks.notification.dto.NotificationResponse;

public record NotificationCreated(Long recipientId, NotificationResponse notification) {}
