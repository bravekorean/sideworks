package com.example.sideworks.leave.service;

import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.leave.dto.AnnualLeaveBalanceResponse;
import com.example.sideworks.leave.repository.LeaveRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnualLeaveAvailabilityServiceTest {
    @Mock AnnualLeaveBalanceService balances;
    @Mock LeaveRequestRepository requests;
    @InjectMocks AnnualLeaveAvailabilityService service;

    @Test
    void 진행_중_신청량을_잔여량에서_제외한다() {
        when(balances.getMyBalance("employee", 2026)).thenReturn(new AnnualLeaveBalanceResponse(
                2026, new BigDecimal("15.0"), new BigDecimal("12.0"), new BigDecimal("3.0")));
        when(requests.sumPendingDays("employee", 2026, ApprovalStatus.IN_PROGRESS)).thenReturn(new BigDecimal("2.5"));

        var result = service.getMyAvailability("employee", 2026);

        assertThat(result.pendingDays()).isEqualByComparingTo("2.5");
        assertThat(result.availableDays()).isEqualByComparingTo("9.5");
        verify(requests).sumPendingDays("employee", 2026, ApprovalStatus.IN_PROGRESS);
    }

    @Test
    void 신청이_없으면_대기량은_영이다() {
        when(balances.getMyBalance("employee", null)).thenReturn(new AnnualLeaveBalanceResponse(
                2026, new BigDecimal("15.0"), new BigDecimal("15.0"), BigDecimal.ZERO));

        var result = service.getMyAvailability("employee", null);

        assertThat(result.pendingDays()).isEqualByComparingTo("0.0");
        assertThat(result.availableDays()).isEqualByComparingTo("15.0");
    }
}
