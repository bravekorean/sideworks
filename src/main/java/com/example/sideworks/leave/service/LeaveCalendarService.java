package com.example.sideworks.leave.service;

import com.example.sideworks.approval.entity.ApprovalStatus;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.leave.dto.LeaveDayResponse;
import com.example.sideworks.leave.repository.LeaveRequestDayRepository;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.entity.UserStatus;
import com.example.sideworks.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveCalendarService {
    private final UserRepository users;
    private final LeaveRequestDayRepository days;

    public List<LeaveDayResponse> getMyApprovedDays(String loginId, int year, int month) {
        if (year < 2000 || year > 9999) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        YearMonth period;
        try {
            period = YearMonth.of(year, month);
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        User user = users.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        return days.findApprovedDays(user.getUserId(), period.atDay(1), period.atEndOfMonth(),
                ApprovalStatus.APPROVED).stream().map(LeaveDayResponse::from).toList();
    }
}
