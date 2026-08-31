package com.example.sideworks.user.service;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.JobFamily;
import com.example.sideworks.user.repository.EmployeeNumberSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class EmployeeNumberGenerator {

    private static final int MAX_YEARLY_SEQUENCE = 999;

    private final EmployeeNumberSequenceRepository sequenceRepository;

    public String generate(JobFamily jobFamily, LocalDate hireDate) {
        int hireYear = hireDate.getYear();

        sequenceRepository.incrementSequence(jobFamily.name(), hireYear);
        Integer sequence = sequenceRepository.findLastSequence(jobFamily.name(), hireYear);

        if (sequence == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (sequence > MAX_YEARLY_SEQUENCE) {
            throw new BusinessException(ErrorCode.EMPLOYEE_NUMBER_EXHAUSTED);
        }

        return "%s-%02d%03d".formatted(
                jobFamily.getEmployeeNumberPrefix(),
                hireYear % 100,
                sequence
        );
    }
}
