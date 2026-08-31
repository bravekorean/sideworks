package com.example.sideworks.user.service;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.JobFamily;
import com.example.sideworks.user.repository.EmployeeNumberSequenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeNumberGeneratorTest {

    @Mock
    private EmployeeNumberSequenceRepository sequenceRepository;

    @InjectMocks
    private EmployeeNumberGenerator employeeNumberGenerator;

    @Test
    void 기술직렬의_입사연도와_순번으로_사번을_생성한다() {
        when(sequenceRepository.findLastSequence("TECHNICAL", 2026)).thenReturn(1);

        String employeeNo = employeeNumberGenerator.generate(
                JobFamily.TECHNICAL,
                LocalDate.of(2026, 8, 31)
        );

        assertThat(employeeNo).isEqualTo("TC-26001");
        verify(sequenceRepository).incrementSequence("TECHNICAL", 2026);
    }

    @Test
    void 경영직렬의_과거_입사연도로_사번을_생성한다() {
        when(sequenceRepository.findLastSequence("CORPORATE", 2018)).thenReturn(12);

        String employeeNo = employeeNumberGenerator.generate(
                JobFamily.CORPORATE,
                LocalDate.of(2018, 3, 2)
        );

        assertThat(employeeNo).isEqualTo("CP-18012");
    }

    @Test
    void 연간_직렬별_순번이_세자리를_초과하면_발급하지_않는다() {
        when(sequenceRepository.findLastSequence("TECHNICAL", 2026)).thenReturn(1000);

        assertThatThrownBy(() -> employeeNumberGenerator.generate(
                JobFamily.TECHNICAL,
                LocalDate.of(2026, 8, 31)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMPLOYEE_NUMBER_EXHAUSTED);
    }
}
