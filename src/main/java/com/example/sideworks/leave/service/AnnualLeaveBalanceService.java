package com.example.sideworks.leave.service;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.leave.dto.AnnualLeaveBalanceResponse;
import com.example.sideworks.leave.dto.AnnualLeaveHistoryResponse;
import com.example.sideworks.leave.entity.AnnualLeaveBalance;
import com.example.sideworks.leave.entity.AnnualLeaveHistory;
import com.example.sideworks.leave.policy.AnnualLeaveGrantPolicy;
import com.example.sideworks.leave.repository.AnnualLeaveBalanceRepository;
import com.example.sideworks.leave.repository.AnnualLeaveHistoryRepository;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnualLeaveBalanceService {
    private final UserRepository users;
    private final AnnualLeaveBalanceRepository balances;
    private final AnnualLeaveHistoryRepository histories;
    private final AnnualLeaveGrantPolicy grantPolicy;
    private final Clock clock;

    @Transactional
    public AnnualLeaveBalanceResponse getMyBalance(String loginId, Integer requestedYear) {
        return AnnualLeaveBalanceResponse.from(ensureBalance(loginId, requestedYear));
    }

    @Transactional
    public List<AnnualLeaveHistoryResponse> getMyHistory(String loginId, Integer requestedYear) {
        AnnualLeaveBalance balance = ensureBalance(loginId, requestedYear);
        return histories.findByBalance_AnnualLeaveBalanceIdOrderByCreatedAtDescAnnualLeaveHistoryIdDesc(
                        balance.getAnnualLeaveBalanceId()).stream()
                .map(AnnualLeaveHistoryResponse::from).toList();
    }

    private AnnualLeaveBalance ensureBalance(String loginId, Integer requestedYear) {
        int year = requestedYear == null ? LocalDate.now(clock).getYear() : requestedYear;
        if (year < 2000 || year > 9999) {
            throw new BusinessException(ErrorCode.LEAVE_YEAR_NOT_ELIGIBLE);
        }

        // 동일 직원의 첫 조회를 DB 행 잠금으로 직렬화한다. 두 요청이 동시에 잔액을 만들지 않는다.
        User user = users.lockLeaveOwner(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        if (user.getHireDate() == null) {
            throw new BusinessException(ErrorCode.LEAVE_HIRE_DATE_REQUIRED);
        }

        var grantedDays = grantPolicy.calculate(user.getHireDate(), year);
        var existing = balances.findByUser_UserIdAndLeaveYear(user.getUserId(), year);
        if (existing.isPresent()) {
            return existing.get();
        }

        AnnualLeaveBalance balance = balances.saveAndFlush(AnnualLeaveBalance.grant(user, year, grantedDays));
        histories.saveAndFlush(AnnualLeaveHistory.grant(balance));
        return balance;
    }
}
