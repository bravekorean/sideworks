package com.example.sideworks.approval.attachment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "app.attachment")
@Getter
@Setter
public class AttachmentProperties {

    private int maxFileCount;
    private DataSize maxFileSize;
    private DataSize maxTotalSize;
    private Path storagePath;
}
