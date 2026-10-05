package com.example.sideworks.approval.service;

import com.example.sideworks.approval.dto.*;
import com.example.sideworks.approval.entity.*;
import com.example.sideworks.approval.repository.ApprovalDocumentTypeRepository;
import com.example.sideworks.common.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalDocumentTypeAdminServiceTest {
    @Mock ApprovalDocumentTypeRepository repository;
    @InjectMocks ApprovalDocumentTypeAdminService service;

    @Test void 일반종류를_활성상태로_생성한다() {
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.create(createRequest("ESTIMATE", " 견적서 "));
        assertThat(result.typeName()).isEqualTo("견적서");
        assertThat(result.behaviorType()).isEqualTo(DocumentBehaviorType.GENERAL);
        assertThat(result.system()).isFalse();
        assertThat(result.active()).isTrue();
    }

    @Test void 중복코드는_거절한다() {
        when(repository.existsByTypeCodeIgnoreCase("ESTIMATE")).thenReturn(true);
        assertThatThrownBy(() -> service.create(createRequest("ESTIMATE", "견적서")))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void 잘못된_코드와_누락된_순서는_거절한다() {
        assertThatThrownBy(() -> service.create(createRequest("bad code", "견적서"))).isInstanceOf(BusinessException.class);
        var request = createRequest("ESTIMATE", "견적서");
        ReflectionTestUtils.setField(request, "sortOrder", null);
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository);
    }

    @Test void 버전이_같으면_비활성화할_수_있다() {
        var entity = existing(false);
        when(repository.saveAndFlush(entity)).thenReturn(entity);
        service.update(1L, updateRequest(3L));
        assertThat(entity.isActive()).isFalse();
        assertThat(entity.getTypeCode()).isEqualTo("ESTIMATE");
    }

    @Test void 오래된_버전으로_수정하지_못한다() {
        var entity = existing(false);
        assertThatThrownBy(() -> service.update(1L, updateRequest(2L))).isInstanceOf(BusinessException.class);
        assertThat(entity.isActive()).isTrue();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void 시스템종류는_수정하지_못한다() {
        existing(true);
        assertThatThrownBy(() -> service.update(1L, updateRequest(3L))).isInstanceOf(BusinessException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void 수정시_이름중복을_차단한다() {
        existing(false);
        when(repository.existsByTypeNameIgnoreCaseAndApprovalDocumentTypeIdNot("견적서", 1L)).thenReturn(true);
        assertError(() -> service.update(1L, updateRequest(3L)), ErrorCode.DOCUMENT_TYPE_DUPLICATE);
    }

    @Test void DB에서_발생한_동시중복도_충돌로_응답한다() {
        when(repository.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));
        assertError(() -> service.create(createRequest("ESTIMATE", "견적서")), ErrorCode.DOCUMENT_TYPE_DUPLICATE);
    }

    @Test void 저장중_낙관적잠금_충돌도_처리한다() {
        var entity = existing(false);
        when(repository.saveAndFlush(entity)).thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(ApprovalDocumentType.class, 1L));
        assertError(() -> service.update(1L, updateRequest(3L)), ErrorCode.DOCUMENT_TYPE_STALE);
    }

    @Test void 음수순서와_과도한_본문을_거절한다() {
        var request = createRequest("ESTIMATE", "견적서");
        ReflectionTestUtils.setField(request, "sortOrder", -1);
        assertError(() -> service.create(request), ErrorCode.INVALID_REQUEST);
        ReflectionTestUtils.setField(request, "sortOrder", 0);
        ReflectionTestUtils.setField(request, "contentTemplate", "a".repeat(20001));
        assertError(() -> service.create(request), ErrorCode.INVALID_REQUEST);
        verifyNoInteractions(repository);
    }

    @Test void 활성상태나_버전이_누락되면_수정하지_못한다() {
        assertError(() -> service.update(1L, updateRequest(null)), ErrorCode.INVALID_REQUEST);
        var request = updateRequest(3L);
        ReflectionTestUtils.setField(request, "active", null);
        assertError(() -> service.update(1L, request), ErrorCode.INVALID_REQUEST);
        verifyNoInteractions(repository);
    }

    @Test void 비활성종류도_관리목록에_포함한다() {
        var type = ApprovalDocumentType.createGeneral("ESTIMATE", "견적서", null, null, 0);
        type.updateGeneral("견적서", null, null, 0, false);
        when(repository.findAllByOrderBySortOrderAscApprovalDocumentTypeIdAsc()).thenReturn(java.util.List.of(type));
        assertThat(service.findAll()).singleElement().satisfies(result -> assertThat(result.active()).isFalse());
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }

    private ApprovalDocumentType existing(boolean system) {
        var entity = ApprovalDocumentType.createGeneral("ESTIMATE", "견적서", null, "본문", 10);
        ReflectionTestUtils.setField(entity, "system", system);
        ReflectionTestUtils.setField(entity, "version", 3L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        return entity;
    }

    private ApprovalDocumentTypeCreateRequest createRequest(String code, String name) {
        var request = new ApprovalDocumentTypeCreateRequest();
        ReflectionTestUtils.setField(request, "typeCode", code);
        ReflectionTestUtils.setField(request, "typeName", name);
        ReflectionTestUtils.setField(request, "sortOrder", 10);
        return request;
    }

    private ApprovalDocumentTypeUpdateRequest updateRequest(Long version) {
        var request = new ApprovalDocumentTypeUpdateRequest();
        ReflectionTestUtils.setField(request, "typeName", "견적서");
        ReflectionTestUtils.setField(request, "sortOrder", 10);
        ReflectionTestUtils.setField(request, "active", false);
        ReflectionTestUtils.setField(request, "version", version);
        return request;
    }
}
