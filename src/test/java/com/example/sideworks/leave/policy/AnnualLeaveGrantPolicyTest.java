package com.example.sideworks.leave.policy;

import com.example.sideworks.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class AnnualLeaveGrantPolicyTest {
    private final AnnualLeaveGrantPolicy policy = new AnnualLeaveGrantPolicy();
    private final LocalDate hireDate = LocalDate.of(2026, 9, 1);

    @Test void 입사연도에는_11일을_부여한다() {
        assertThat(policy.calculate(hireDate, 2026)).isEqualByComparingTo(new BigDecimal("11.0"));
    }

    @Test void 첫해와_두번째_다음해에는_15일을_부여한다() {
        assertThat(policy.calculate(hireDate, 2027)).isEqualByComparingTo(new BigDecimal("15.0"));
        assertThat(policy.calculate(hireDate, 2028)).isEqualByComparingTo(new BigDecimal("15.0"));
    }

    @Test void 그다음해부터_1일씩_증가하고_25일에서_멈춘다() {
        assertThat(policy.calculate(hireDate, 2029)).isEqualByComparingTo(new BigDecimal("16.0"));
        assertThat(policy.calculate(hireDate, 2038)).isEqualByComparingTo(new BigDecimal("25.0"));
        assertThat(policy.calculate(hireDate, 2099)).isEqualByComparingTo(new BigDecimal("25.0"));
    }

    @Test void 입사일_누락이나_입사전_연도는_계산하지_않는다() {
        assertThatThrownBy(() -> policy.calculate(null, 2026)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> policy.calculate(hireDate, 2025)).isInstanceOf(BusinessException.class);
    }
}
