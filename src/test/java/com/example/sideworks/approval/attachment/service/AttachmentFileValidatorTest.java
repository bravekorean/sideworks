package com.example.sideworks.approval.attachment.service;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentFileValidatorTest {

    private final AttachmentFileValidator validator = new AttachmentFileValidator();

    @Test
    void PDF_시그니처가_유효하면_허용한다() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "견적서.pdf", "application/pdf", "%PDF-1.7 sample".getBytes()
        );

        AttachmentFileValidator.ValidatedFile result = validator.validate(file);

        assertThat(result.originalFileName()).isEqualTo("견적서.pdf");
        assertThat(result.extension()).isEqualTo("pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void 확장자만_PDF인_위장_파일은_거부한다() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "위장.pdf", "application/pdf", "executable".getBytes()
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED);
    }

    @Test
    void 경로가_포함된_파일명은_거부한다() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "../견적서.pdf", "application/pdf", "%PDF-1.7".getBytes()
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED);
    }
}
