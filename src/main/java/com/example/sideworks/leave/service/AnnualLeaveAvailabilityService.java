package com.example.sideworks.leave.service;

import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.leave.dto.AnnualLeaveAvailabilityResponse;
import com.example.sideworks.leave.dto.AnnualLeaveBalanceResponse;
import com.example.sideworks.leave.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnnualLeaveAvailabilityService {
    private final AnnualLeaveBalanceService balances;
    private final LeaveRequestRepository requests;

    @Transactional
    public AnnualLeaveAvailabilityResponse getMyAvailability(String loginId, Integer year) {
        // 첫 조회 시 부여와 원장을 생성하며, 동일 트랜잭션에서 진행 중 신청량을 계산한다.
        AnnualLeaveBalanceResponse balance = balances.getMyBalance(loginId, year);
        return AnnualLeaveAvailabilityResponse.of(balance,
                requests.sumPendingDays(loginId, balance.leaveYear(), ApprovalStatus.IN_PROGRESS));
    }
}
