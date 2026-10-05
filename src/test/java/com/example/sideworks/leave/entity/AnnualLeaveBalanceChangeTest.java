package com.example.sideworks.leave.entity;

import com.example.sideworks.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AnnualLeaveBalanceChangeTest {
    @Test
    void 승인_차감과_취소_복원은_원래_부여량을_넘지_않는다() {
        AnnualLeaveBalance balance = AnnualLeaveBalance.grant(mock(User.class), 2026,
                new BigDecimal("15.0"));

        balance.use(new BigDecimal("0.5"));
        assertThat(balance.getRemainingDays()).isEqualByComparingTo("14.5");
        balance.restore(new BigDecimal("0.5"));
        assertThat(balance.getRemainingDays()).isEqualByComparingTo("15.0");
        assertThatThrownBy(() -> balance.restore(new BigDecimal("0.5")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 잔액보다_많은_차감은_거부한다() {
        AnnualLeaveBalance balance = AnnualLeaveBalance.grant(mock(User.class), 2026,
                new BigDecimal("11.0"));
        assertThatThrownBy(() -> balance.use(new BigDecimal("11.5")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(balance.getRemainingDays()).isEqualByComparingTo("11.0");
    }
}
