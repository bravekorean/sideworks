package com.example.sideworks.attendance.service;

import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class AttendanceAudit {
    private AttendanceAudit() {}

    static void afterCommit(String event, Long actorId, Long targetId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            write(event, actorId, targetId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                write(event, actorId, targetId);
            }
        });
    }

    private static void write(String event, Long actorId, Long targetId) {
        LoggerFactory.getLogger("AUDIT").atInfo().addKeyValue("event", event)
                .addKeyValue("actorId", actorId).addKeyValue("targetId", targetId)
                .log("Attendance administration committed");
    }
}
