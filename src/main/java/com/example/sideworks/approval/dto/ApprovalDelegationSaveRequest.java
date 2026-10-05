package com.example.sideworks.approval.dto;

import java.time.LocalDate;

public record ApprovalDelegationSaveRequest(Long delegatorId, Long delegateeId,
                                            LocalDate startDate, LocalDate endDate) {
}
