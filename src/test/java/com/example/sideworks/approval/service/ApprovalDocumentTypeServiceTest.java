package com.example.sideworks.approval.service;

import com.example.sideworks.approval.entity.DocumentBehaviorType;
import com.example.sideworks.approval.repository.ApprovalDocumentTypeRepository;
import com.example.sideworks.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.example.sideworks.approval.entity.ApprovalDocumentTypeFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalDocumentTypeServiceTest {
    @Mock ApprovalDocumentTypeRepository repository;
    @InjectMocks ApprovalDocumentTypeService service;

    @Test void 활성_일반종류를_선택할_수_있다() {
        var type = general();
        when(repository.findById(1L)).thenReturn(Optional.of(type));
        assertThat(service.requireActiveGeneral(1L)).isSameAs(type);
    }

    @Test void 종류_ID_누락은_기본_품의서로_묵시적_변환하지_않는다() {
        assertThatThrownBy(() -> service.requireActiveGeneral(null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository);
    }

    @Test void 존재하지_않는_종류는_선택할_수_없다() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireActiveGeneral(99L)).isInstanceOf(BusinessException.class);
    }

    @Test void 비활성_일반종류는_선택할_수_없다() {
        when(repository.findById(1L)).thenReturn(Optional.of(type(1L, "GENERAL_PROPOSAL", DocumentBehaviorType.GENERAL, false)));
        assertThatThrownBy(() -> service.requireActiveGeneral(1L)).isInstanceOf(BusinessException.class);
    }

    @Test void 일반_결재에서_근태정정_종류를_사용할_수_없다() {
        when(repository.findById(6L)).thenReturn(Optional.of(type(6L, "ATTENDANCE_CORRECTION", DocumentBehaviorType.ATTENDANCE_CORRECTION, true)));
        assertThatThrownBy(() -> service.requireActiveGeneral(6L)).isInstanceOf(BusinessException.class);
    }

    @Test void 근태_전용경로는_서버가_시스템_코드를_조회한다() {
        var type = type(6L, "ATTENDANCE_CORRECTION", DocumentBehaviorType.ATTENDANCE_CORRECTION, true);
        when(repository.findByTypeCode("ATTENDANCE_CORRECTION")).thenReturn(Optional.of(type));
        assertThat(service.requireAttendanceCorrection()).isSameAs(type);
    }

    @Test void 비활성_근태정정도_신규_상신할_수_없다() {
        when(repository.findByTypeCode("ATTENDANCE_CORRECTION")).thenReturn(Optional.of(type(6L, "ATTENDANCE_CORRECTION", DocumentBehaviorType.ATTENDANCE_CORRECTION, false)));
        assertThatThrownBy(() -> service.requireAttendanceCorrection()).isInstanceOf(BusinessException.class);
    }
}
