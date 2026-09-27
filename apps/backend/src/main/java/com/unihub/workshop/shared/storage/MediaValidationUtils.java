package com.unihub.workshop.shared.storage;

import com.unihub.workshop.shared.exception.ValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

public final class MediaValidationUtils {

    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB = 5,242,880 bytes

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        ".jpg",
        ".jpeg",
        ".png",
        ".webp"
    );

    private static final byte[] PNG_SIGNATURE = new byte[] {
        (byte) 0x89,
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    };

    private MediaValidationUtils() {}

    /**
     * Validates a multipart image file against size, MIME type, extension, and magic bytes.
     *
     * @param file the uploaded multipart file
     * @return the normalized file extension with leading dot (e.g. ".jpg", ".png")
     * @throws ValidationException if validation fails
     */
    public static String validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("file", "File is empty or missing.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException(
                "file",
                "File size exceeds the 5MB maximum limit."
            );
        }

        String contentType = file.getContentType();
        if (
            contentType == null ||
            !ALLOWED_MIME_TYPES.contains(
                contentType.toLowerCase(Locale.ROOT).trim()
            )
        ) {
            throw new ValidationException(
                "file",
                "Unsupported MIME type: " +
                    contentType +
                    ". Allowed types: image/jpeg, image/jpg, image/png, image/webp."
            );
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ValidationException(
                "file",
                "Unsupported file extension: " +
                    extension +
                    ". Allowed extensions: .jpg, .jpeg, .png, .webp."
            );
        }

        byte[] headerBytes = readHeaderBytes(file, 12);
        if (
            !verifyMagicBytes(
                headerBytes,
                contentType.toLowerCase(Locale.ROOT).trim(),
                extension
            )
        ) {
            throw new ValidationException(
                "file",
                "File content does not match declared image format."
            );
        }

        return extension;
    }

    /**
     * Validates raw image bytes against size, MIME type, extension, and magic bytes.
     *
     * @param bytes the binary data
     * @param originalFilename original file name
     * @param contentType MIME type
     * @return the normalized file extension
     */
    public static String validateImageBytes(
        byte[] bytes,
        String originalFilename,
        String contentType
    ) {
        if (bytes == null || bytes.length == 0) {
            throw new ValidationException("file", "File is empty or missing.");
        }

        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException(
                "file",
                "File size exceeds the 5MB maximum limit."
            );
        }

        if (
            contentType == null ||
            !ALLOWED_MIME_TYPES.contains(
                contentType.toLowerCase(Locale.ROOT).trim()
            )
        ) {
            throw new ValidationException(
                "file",
                "Unsupported MIME type: " +
                    contentType +
                    ". Allowed types: image/jpeg, image/jpg, image/png, image/webp."
            );
        }

        String extension = extractExtension(originalFilename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ValidationException(
                "file",
                "Unsupported file extension: " +
                    extension +
                    ". Allowed extensions: .jpg, .jpeg, .png, .webp."
            );
        }

        byte[] header = new byte[Math.min(12, bytes.length)];
        System.arraycopy(bytes, 0, header, 0, header.length);
        if (
            !verifyMagicBytes(
                header,
                contentType.toLowerCase(Locale.ROOT).trim(),
                extension
            )
        ) {
            throw new ValidationException(
                "file",
                "File content does not match declared image format."
            );
        }

        return extension;
    }

    public static String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {
            return null;
        }
        return filename.substring(lastDotIndex).toLowerCase(Locale.ROOT).trim();
    }

    private static byte[] readHeaderBytes(MultipartFile file, int maxBytes) {
        try (InputStream is = file.getInputStream()) {
            return is.readNBytes(maxBytes);
        } catch (IOException e) {
            throw new ValidationException(
                "file",
                "Unable to read file content for validation."
            );
        }
    }

    private static boolean verifyMagicBytes(
        byte[] header,
        String mimeType,
        String extension
    ) {
        if (header == null || header.length < 3) {
            return false;
        }

        if (mimeType.equals("image/jpeg") || mimeType.equals("image/jpg")) {
            return (
                isJpeg(header) &&
                (extension.equals(".jpg") || extension.equals(".jpeg"))
            );
        }

        if (mimeType.equals("image/png")) {
            return isPng(header) && extension.equals(".png");
        }

        if (mimeType.equals("image/webp")) {
            return isWebP(header) && extension.equals(".webp");
        }

        return false;
    }

    private static boolean isJpeg(byte[] header) {
        return (
            header.length >= 3 &&
            (header[0] & 0xFF) == 0xFF &&
            (header[1] & 0xFF) == 0xD8 &&
            (header[2] & 0xFF) == 0xFF
        );
    }

    private static boolean isPng(byte[] header) {
        if (header.length < 8) {
            return false;
        }
        for (int i = 0; i < 8; i++) {
            if (header[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWebP(byte[] header) {
        if (header.length < 12) {
            return false;
        }
        return (
            header[0] == 'R' &&
            header[1] == 'I' &&
            header[2] == 'F' &&
            header[3] == 'F' &&
            header[8] == 'W' &&
            header[9] == 'E' &&
            header[10] == 'B' &&
            header[11] == 'P'
        );
    }
}
