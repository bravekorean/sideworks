package com.example.sideworks.notification.service;

import com.example.sideworks.notification.event.NotificationCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDelivery {
    private final NotificationStream stream;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(NotificationCreated event) {
        try {
            stream.send(event);
        } catch (Exception error) {
            // 이미 커밋된 결재 응답을 실패로 바꾸지 않는다. 재접속 시 DB 목록으로 복구한다.
            log.warn("알림 SSE 전달 실패: notificationId={}", event.notification().notificationId(), error);
        }
    }
}
