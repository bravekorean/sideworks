package com.example.sideworks.leave.entity;

import java.math.BigDecimal;

public enum LeavePeriod {
    FULL("1.0"), AM("0.5"), PM("0.5");

    private final BigDecimal days;

    LeavePeriod(String days) {
        this.days = new BigDecimal(days);
    }

    public BigDecimal days() {
        return days;
    }

    public boolean overlaps(LeavePeriod other) {
        return this == FULL || other == FULL || this == other;
    }
}
