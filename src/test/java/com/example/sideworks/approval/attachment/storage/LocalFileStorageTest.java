package com.example.sideworks.approval.attachment.storage;

import com.example.sideworks.approval.attachment.config.AttachmentProperties;
import com.example.sideworks.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void UUID_키로_파일을_저장하고_읽고_삭제한다() throws Exception {
        AttachmentProperties properties = new AttachmentProperties();
        properties.setStoragePath(tempDir);
        LocalFileStorage storage = new LocalFileStorage(properties);
        storage.initialize();
        MockMultipartFile file = new MockMultipartFile("files", "견적서.pdf", "application/pdf", "%PDF-test".getBytes());

        FileStorage.StoredFile stored = storage.store(1L, file);
        Resource resource = storage.load(stored.storageKey());

        assertThat(stored.storageKey()).startsWith("1/");
        assertThat(resource.getContentAsByteArray()).isEqualTo(file.getBytes());

        storage.delete(stored.storageKey());
        assertThatThrownBy(() -> storage.load(stored.storageKey())).isInstanceOf(BusinessException.class);
    }

    @Test
    void 저장소_루트_밖의_경로는_읽을_수_없다() {
        AttachmentProperties properties = new AttachmentProperties();
        properties.setStoragePath(tempDir);
        LocalFileStorage storage = new LocalFileStorage(properties);
        storage.initialize();

        assertThatThrownBy(() -> storage.load("../outside"))
                .isInstanceOf(BusinessException.class);
    }
}
