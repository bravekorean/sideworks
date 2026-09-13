package com.example.sideworks.approval.attachment.service;

import com.example.sideworks.common.exception.BusinessException;
import com.example.sideworks.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class AttachmentFileValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "docx", "xlsx");

    public ValidatedFile validate(MultipartFile file) {
        String originalFileName = normalizeFileName(file.getOriginalFilename());
        String extension = extractExtension(originalFileName);

        if (file.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension) || !hasValidSignature(file, extension)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED);
        }

        return new ValidatedFile(file, originalFileName, extension, resolveContentType(extension));
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.length() > 255
                || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException(ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED);
        }
        return fileName.trim();
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 1 || dotIndex == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED);
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private boolean hasValidSignature(MultipartFile file, String extension) {
        try {
            return switch (extension) {
                case "pdf" -> startsWith(file, new byte[]{'%', 'P', 'D', 'F', '-'});
                case "png" -> startsWith(file, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
                case "jpg", "jpeg" -> startsWith(file, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
                case "docx" -> containsOfficeDirectory(file, "word/");
                case "xlsx" -> containsOfficeDirectory(file, "xl/");
                default -> false;
            };
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.ATTACHMENT_STORAGE_FAILED);
        }
    }

    private boolean startsWith(MultipartFile file, byte[] signature) throws IOException {
        try (InputStream input = new BufferedInputStream(file.getInputStream())) {
            byte[] actual = input.readNBytes(signature.length);
            return java.util.Arrays.equals(actual, signature);
        }
    }

    private boolean containsOfficeDirectory(MultipartFile file, String requiredDirectory) throws IOException {
        boolean hasContentTypes = false;
        boolean hasRequiredDirectory = false;

        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                hasContentTypes |= "[Content_Types].xml".equals(name);
                hasRequiredDirectory |= name.startsWith(requiredDirectory);
                if (hasContentTypes && hasRequiredDirectory) {
                    return true;
                }
            }
        }
        return false;
    }

    private String resolveContentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }

    public record ValidatedFile(
            MultipartFile file,
            String originalFileName,
            String extension,
            String contentType
    ) {
    }
}
