package com.example.sideworks.leave.service;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.leave.entity.AnnualLeaveBalance;
import com.example.sideworks.leave.entity.AnnualLeaveHistory;
import com.example.sideworks.leave.repository.AnnualLeaveBalanceRepository;
import com.example.sideworks.leave.repository.AnnualLeaveHistoryRepository;
import com.example.sideworks.leave.policy.AnnualLeaveGrantPolicy;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnnualLeaveBalanceServiceTest {
    @Mock UserRepository users;
    @Mock AnnualLeaveBalanceRepository balances;
    @Mock AnnualLeaveHistoryRepository histories;

    private AnnualLeaveBalanceService service() {
        return new AnnualLeaveBalanceService(users, balances, histories, new AnnualLeaveGrantPolicy(),
                Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test void 첫조회에서_잔액과_GRANT이력을_함께_저장한다() {
        User user = activeUser(LocalDate.of(2026, 9, 1));
        when(users.lockLeaveOwner("employee")).thenReturn(Optional.of(user));
        when(balances.findByUser_UserIdAndLeaveYear(1L, 2026)).thenReturn(Optional.empty());
        when(balances.saveAndFlush(any())).thenAnswer(invocation -> {
            AnnualLeaveBalance balance = invocation.getArgument(0);
            ReflectionTestUtils.setField(balance, "annualLeaveBalanceId", 10L);
            return balance;
        });

        var result = service().getMyBalance("employee", 2026);

        assertThat(result.grantedDays()).isEqualByComparingTo("11.0");
        assertThat(result.remainingDays()).isEqualByComparingTo("11.0");
        var history = org.mockito.ArgumentCaptor.forClass(AnnualLeaveHistory.class);
        verify(histories).saveAndFlush(history.capture());
        assertThat(history.getValue().getActionType().name()).isEqualTo("GRANT");
        assertThat(history.getValue().getSourceKey()).isEqualTo("GRANT:1:2026");
        assertThat(history.getValue().getBalanceAfter()).isEqualByComparingTo("11.0");
    }

    @Test void 이미_부여된_연도는_다시_생성하지_않는다() {
        User user = activeUser(LocalDate.of(2024, 3, 1));
        AnnualLeaveBalance existing = AnnualLeaveBalance.grant(user, 2026, new java.math.BigDecimal("15.0"));
        when(users.lockLeaveOwner("employee")).thenReturn(Optional.of(user));
        when(balances.findByUser_UserIdAndLeaveYear(1L, 2026)).thenReturn(Optional.of(existing));

        var result = service().getMyBalance("employee", 2026);

        assertThat(result.remainingDays()).isEqualByComparingTo("15.0");
        verify(balances, never()).saveAndFlush(any());
        verifyNoInteractions(histories);
    }

    @Test void 이력조회도_처음이면_부여한뒤_이력을_반환한다() {
        User user = activeUser(LocalDate.of(2026, 9, 1));
        when(users.lockLeaveOwner("employee")).thenReturn(Optional.of(user));
        when(balances.findByUser_UserIdAndLeaveYear(1L, 2026)).thenReturn(Optional.empty());
        when(balances.saveAndFlush(any())).thenAnswer(invocation -> {
            AnnualLeaveBalance balance = invocation.getArgument(0);
            ReflectionTestUtils.setField(balance, "annualLeaveBalanceId", 10L);
            return balance;
        });
        AnnualLeaveHistory[] saved = new AnnualLeaveHistory[1];
        when(histories.saveAndFlush(any())).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved[0], "annualLeaveHistoryId", 20L);
            return saved[0];
        });
        when(histories.findByBalance_AnnualLeaveBalanceIdOrderByCreatedAtDescAnnualLeaveHistoryIdDesc(10L))
                .thenAnswer(invocation -> List.of(saved[0]));

        var result = service().getMyHistory("employee", 2026);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.actionType().name()).isEqualTo("GRANT");
            assertThat(item.daysDelta()).isEqualByComparingTo("11.0");
            assertThat(item.balanceAfter()).isEqualByComparingTo("11.0");
        });
        verify(histories).findByBalance_AnnualLeaveBalanceIdOrderByCreatedAtDescAnnualLeaveHistoryIdDesc(10L);
    }

    @Test void 연도_생략시_서버_현재연도를_사용한다() {
        User user = activeUser(LocalDate.of(2026, 9, 1));
        AnnualLeaveBalance existing = AnnualLeaveBalance.grant(user, 2026, new java.math.BigDecimal("11.0"));
        when(users.lockLeaveOwner("employee")).thenReturn(Optional.of(user));
        when(balances.findByUser_UserIdAndLeaveYear(1L, 2026)).thenReturn(Optional.of(existing));

        assertThat(service().getMyBalance("employee", null).leaveYear()).isEqualTo(2026);
        verify(balances).findByUser_UserIdAndLeaveYear(1L, 2026);
    }

    @Test void 입사일이_없으면_부여하지_않는다() {
        when(users.lockLeaveOwner("employee")).thenReturn(Optional.of(activeUser(null)));
        assertThatThrownBy(() -> service().getMyBalance("employee", 2026))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LEAVE_HIRE_DATE_REQUIRED));
        verifyNoInteractions(balances, histories);
    }

    @Test void 입사전_연도와_범위밖_연도는_부여하지_않는다() {
        when(users.lockLeaveOwner("employee")).thenReturn(Optional.of(activeUser(LocalDate.of(2026, 9, 1))));
        assertThatThrownBy(() -> service().getMyBalance("employee", 2025)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service().getMyBalance("employee", 999)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(balances, histories);
    }

    @Test void 비활성직원에게는_부여하지_않는다() {
        User user = activeUser(LocalDate.of(2026, 9, 1));
        user.changeStatus(UserStatus.INACTIVE);
        when(users.lockLeaveOwner("employee")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service().getMyBalance("employee", 2026))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_NOT_ACTIVE));
    }

    private User activeUser(LocalDate hireDate) {
        User user = User.create("employee", "unused", "직원", null, null, "EMP01",
                null, hireDate, null, null, com.example.sideworks.user.entity.UserRole.USER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "userId", 1L);
        return user;
    }
}
