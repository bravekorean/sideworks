package com.example.sideworks.notification.service;

import com.example.sideworks.notification.event.NotificationCreated;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationStream {
    private final Map<Long, Set<SseEmitter>> clients = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(15 * 60_000L);
        clients.compute(userId, (ignored, existing) -> {
            Set<SseEmitter> userClients = existing == null ? ConcurrentHashMap.newKeySet() : existing;
            userClients.add(emitter);
            return userClients;
        });
        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception error) {
            cleanup.run();
            emitter.completeWithError(error);
        }
        return emitter;
    }

    public void send(NotificationCreated event) {
        Set<SseEmitter> userClients = clients.get(event.recipientId());
        if (userClients == null) return;
        for (SseEmitter emitter : userClients) {
            try {
                emitter.send(SseEmitter.event()
                        .id(event.notification().notificationId().toString())
                        .name("notification").data(event.notification()));
            } catch (Exception error) {
                remove(event.recipientId(), emitter);
                emitter.complete();
            }
        }
    }

    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        clients.forEach((userId, userClients) -> {
            for (SseEmitter emitter : userClients) {
                try {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                } catch (Exception error) {
                    remove(userId, emitter);
                    emitter.complete();
                }
            }
        });
    }

    private void remove(Long userId, SseEmitter emitter) {
        clients.computeIfPresent(userId, (ignored, userClients) -> {
            userClients.remove(emitter);
            return userClients.isEmpty() ? null : userClients;
        });
    }
}
