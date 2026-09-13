package com.example.sideworks.approval.attachment.service;

import com.example.sideworks.approval.attachment.config.AttachmentProperties;
import com.example.sideworks.approval.attachment.entity.ApprovalAttachment;
import com.example.sideworks.approval.attachment.repository.ApprovalAttachmentRepository;
import com.example.sideworks.approval.attachment.storage.FileStorage;
import com.example.sideworks.approval.entity.Approval;
import com.example.sideworks.approval.repository.ApprovalRepository;
import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import com.example.sideworks.user.entity.User;
import com.example.sideworks.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalAttachmentServiceTest {

    @Mock private ApprovalAttachmentRepository attachmentRepository;
    @Mock private ApprovalRepository approvalRepository;
    @Mock private UserRepository userRepository;
    @Mock private FileStorage fileStorage;
    @Mock private AttachmentFileValidator fileValidator;

    private ApprovalAttachmentService service;

    @BeforeEach
    void setUp() {
        AttachmentProperties properties = new AttachmentProperties();
        properties.setMaxFileCount(5);
        properties.setMaxFileSize(DataSize.ofMegabytes(20));
        properties.setMaxTotalSize(DataSize.ofMegabytes(100));
        properties.setStoragePath(java.nio.file.Path.of("attachments"));
        service = new ApprovalAttachmentService(
                attachmentRepository, approvalRepository, userRepository,
                fileStorage, fileValidator, properties
        );
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void 임시저장_작성자는_여러_파일을_업로드한다() {
        Approval approval = editableApproval();
        MockMultipartFile first = file("견적서.pdf", 10);
        MockMultipartFile second = file("비교표.xlsx", 20);
        when(approvalRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(approval));
        when(attachmentRepository.findAllByApproval_ApprovalIdOrderByApprovalAttachmentIdAsc(1L)).thenReturn(List.of());
        when(fileValidator.validate(first)).thenReturn(validated(first, "견적서.pdf", "pdf", "application/pdf"));
        when(fileValidator.validate(second)).thenReturn(validated(second, "비교표.xlsx", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        when(fileStorage.store(1L, first)).thenReturn(new FileStorage.StoredFile("1/first"));
        when(fileStorage.store(1L, second)).thenReturn(new FileStorage.StoredFile("1/second"));
        when(attachmentRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.upload(1L, "writer", List.of(first, second))).hasSize(2);

        verify(attachmentRepository).saveAll(anyList());
    }

    @Test
    void 기존_첨부를_포함해_다섯_개를_초과하면_거부한다() {
        Approval approval = editableApproval();
        ApprovalAttachment existing = mock(ApprovalAttachment.class);
        when(approvalRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(approval));
        when(attachmentRepository.findAllByApproval_ApprovalIdOrderByApprovalAttachmentIdAsc(1L))
                .thenReturn(Collections.nCopies(5, existing));

        assertThatThrownBy(() -> service.upload(1L, "writer", List.of(file("추가.pdf", 1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ATTACHMENT_LIMIT_EXCEEDED);

        verify(fileStorage, never()).store(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 파일당_이십_메가바이트를_초과하면_거부한다() {
        Approval approval = editableApproval();
        MockMultipartFile oversized = new MockMultipartFile(
                "files", "대용량.pdf", "application/pdf", new byte[(20 * 1024 * 1024) + 1]
        );
        when(approvalRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(approval));
        when(attachmentRepository.findAllByApproval_ApprovalIdOrderByApprovalAttachmentIdAsc(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.upload(1L, "writer", List.of(oversized)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ATTACHMENT_LIMIT_EXCEEDED);
    }

    private Approval editableApproval() {
        Approval approval = mock(Approval.class);
        User writer = mock(User.class);
        when(approval.getWriter()).thenReturn(writer);
        when(writer.getLoginId()).thenReturn("writer");
        when(approval.isDraft()).thenReturn(true);
        return approval;
    }

    private MockMultipartFile file(String name, int size) {
        return new MockMultipartFile("files", name, "application/octet-stream", new byte[size]);
    }

    private AttachmentFileValidator.ValidatedFile validated(
            MockMultipartFile file, String name, String extension, String contentType
    ) {
        return new AttachmentFileValidator.ValidatedFile(file, name, extension, contentType);
    }
}
