package com.example.sideworks.leave.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeavePeriodTest {
    @Test
    void 오전과_오후는_공존하고_전일은_모든_시간대와_충돌한다() {
        assertThat(LeavePeriod.AM.overlaps(LeavePeriod.PM)).isFalse();
        assertThat(LeavePeriod.AM.overlaps(LeavePeriod.AM)).isTrue();
        assertThat(LeavePeriod.FULL.overlaps(LeavePeriod.PM)).isTrue();
        assertThat(LeavePeriod.PM.overlaps(LeavePeriod.FULL)).isTrue();
    }
}
