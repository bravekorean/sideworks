package com.example.sideworks.approval.service;

import com.example.sideworks.approval.dto.ApprovalDraftRequest;
import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.entity.DocumentBehaviorType;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.approval.repository.ApprovalDocumentTypeRepository;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static com.example.sideworks.approval.entity.ApprovalDocumentTypeFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceDraftTypeTest {
    @Mock ApprovalRepository approvals;
    @Mock UserRepository users;
    @Mock ApprovalDocumentTypeRepository types;
    private ApprovalService service;

    @BeforeEach void setUp() {
        service = new ApprovalService(approvals, users, null, null, null, null, null, null, null, null,
                null, null, new ApprovalDocumentTypeService(types),
                org.mockito.Mockito.mock(com.example.sideworks.notification.service.ApprovalNotificationWorkflow.class),
                org.mockito.Mockito.mock(ApprovalTemplateService.class),
                org.mockito.Mockito.mock(ApprovalDelegationService.class));
    }

    @Test void 세부종류_FK와_기존_업무분류를_함께_저장한다() {
        User writer = mock(User.class);
        var purchase = type(2L, "SUPPLY_PURCHASE", DocumentBehaviorType.GENERAL, true);
        when(users.findByLoginId("writer")).thenReturn(Optional.of(writer));
        when(types.findById(2L)).thenReturn(Optional.of(purchase));
        when(approvals.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.createDraft("writer", request(2L));

        var captured = ArgumentCaptor.forClass(Approval.class);
        verify(approvals).save(captured.capture());
        assertThat(captured.getValue().getDocumentType()).isSameAs(purchase);
        assertThat(captured.getValue().getLegacyDocumentType()).isEqualTo("GENERAL_PROPOSAL");
        assertThat(captured.getValue().getContent()).isEqualTo("작성한 본문");
    }

    @Test void 일반_임시저장의_문서종류를_변경한다() {
        Approval draft = editableGeneralDraft();
        var purchase = type(2L, "SUPPLY_PURCHASE", DocumentBehaviorType.GENERAL, true);
        when(types.findById(2L)).thenReturn(Optional.of(purchase));
        service.updateDraft(10L, "writer", request(2L));
        assertThat(draft.getDocumentType()).isSameAs(purchase);
        assertThat(draft.getContent()).isEqualTo("작성한 본문");
    }

    @Test void 일반_수정_API로_근태정정_종류를_선택할_수_없다() {
        Approval draft = editableGeneralDraft();
        when(types.findById(6L)).thenReturn(Optional.of(type(6L, "ATTENDANCE_CORRECTION",
                DocumentBehaviorType.ATTENDANCE_CORRECTION, true)));
        assertError(() -> service.updateDraft(10L, "writer", request(6L)), ErrorCode.DOCUMENT_TYPE_NOT_ALLOWED);
        assertThat(draft.getDocumentType().getTypeCode()).isEqualTo("GENERAL_PROPOSAL");
    }

    @Test void 근태정정_문서를_일반_문서로_바꾸지_못한다() {
        Approval draft = Approval.createDraft(mock(User.class), "정정", "본문",
                type(6L, "ATTENDANCE_CORRECTION", DocumentBehaviorType.ATTENDANCE_CORRECTION, true));
        when(approvals.findByApprovalIdAndWriter_LoginId(10L, "writer")).thenReturn(Optional.of(draft));
        assertError(() -> service.updateDraft(10L, "writer", request(1L)), ErrorCode.DOCUMENT_TYPE_NOT_ALLOWED);
        verifyNoInteractions(types);
    }

    @Test void 저장_이후_종류가_비활성화되면_상신을_막는다() {
        Approval draft = editableGeneralDraft();
        when(types.findById(1L)).thenReturn(Optional.of(type(1L, "GENERAL_PROPOSAL", DocumentBehaviorType.GENERAL, false)));
        assertError(() -> service.submitApproval(10L, "writer", null), ErrorCode.DOCUMENT_TYPE_INACTIVE);
        assertThat(draft.isDraft()).isTrue();
    }

    @Test void 종류가_없는_구버전_임시저장은_선택_후_저장해야_상신할_수_있다() {
        Approval draft = editableGeneralDraft();
        ReflectionTestUtils.setField(draft, "documentType", null);
        assertError(() -> service.submitApproval(10L, "writer", null), ErrorCode.INVALID_REQUEST);
        verifyNoInteractions(types);
    }

    @Test void 구버전_일반_임시저장의_누락된_FK는_수정_저장으로_연결한다() {
        Approval draft = editableGeneralDraft();
        ReflectionTestUtils.setField(draft, "documentType", null);
        var general = general();
        when(types.findById(1L)).thenReturn(Optional.of(general));
        service.updateDraft(10L, "writer", request(1L));
        assertThat(draft.getDocumentType()).isSameAs(general);
    }

    private Approval editableGeneralDraft() {
        Approval draft = Approval.createDraft(mock(User.class), "제목", "원래 본문", general());
        when(approvals.findByApprovalIdAndWriter_LoginId(10L, "writer")).thenReturn(Optional.of(draft));
        return draft;
    }

    private ApprovalDraftRequest request(Long typeId) {
        ApprovalDraftRequest request = new ApprovalDraftRequest();
        ReflectionTestUtils.setField(request, "documentTypeId", typeId);
        ReflectionTestUtils.setField(request, "title", "제목");
        ReflectionTestUtils.setField(request, "content", "작성한 본문");
        return request;
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
