package com.unihub.workshop.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unihub.workshop.shared.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

class MediaValidationUtilsTest {

    private static final byte[] VALID_JPEG_BYTES = new byte[] {
        (byte) 0xFF,
        (byte) 0xD8,
        (byte) 0xFF,
        (byte) 0xE0,
        0x00,
        0x10,
        0x4A,
        0x46,
        0x49,
        0x46,
        0x00,
        0x01,
    };

    private static final byte[] VALID_PNG_BYTES = new byte[] {
        (byte) 0x89,
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
        0x00,
        0x00,
        0x00,
        0x0D,
    };

    private static final byte[] VALID_WEBP_BYTES = new byte[] {
        'R',
        'I',
        'F',
        'F',
        0x20,
        0x00,
        0x00,
        0x00,
        'W',
        'E',
        'B',
        'P',
    };

    @Nested
    @DisplayName("Successful image validations")
    class SuccessScenarios {

        @Test
        @DisplayName("Validates valid JPEG file with .jpg extension")
        void shouldValidateJpegJpg() {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.jpg",
                "image/jpeg",
                VALID_JPEG_BYTES
            );

            String ext = MediaValidationUtils.validateImage(file);
            assertThat(ext).isEqualTo(".jpg");
        }

        @Test
        @DisplayName("Validates valid JPEG file with .jpeg extension")
        void shouldValidateJpegJpeg() {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpeg",
                "image/jpeg",
                VALID_JPEG_BYTES
            );

            String ext = MediaValidationUtils.validateImage(file);
            assertThat(ext).isEqualTo(".jpeg");
        }

        @Test
        @DisplayName("Validates valid JPEG file with image/jpg MIME type")
        void shouldValidateImageJpgMime() {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpg",
                VALID_JPEG_BYTES
            );

            String ext = MediaValidationUtils.validateImage(file);
            assertThat(ext).isEqualTo(".jpg");
        }

        @Test
        @DisplayName("Validates valid PNG file")
        void shouldValidatePng() {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                "image/png",
                VALID_PNG_BYTES
            );

            String ext = MediaValidationUtils.validateImage(file);
            assertThat(ext).isEqualTo(".png");
        }

        @Test
        @DisplayName("Validates valid WebP file")
        void shouldValidateWebp() {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "banner.webp",
                "image/webp",
                VALID_WEBP_BYTES
            );

            String ext = MediaValidationUtils.validateImage(file);
            assertThat(ext).isEqualTo(".webp");
        }

        @Test
        @DisplayName(
            "Validates case-insensitive uppercase extension and MIME type"
        )
        void shouldHandleCaseInsensitiveMimeAndExtension() {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "COVER.JPG",
                "IMAGE/JPEG",
                VALID_JPEG_BYTES
            );

            String ext = MediaValidationUtils.validateImage(file);
            assertThat(ext).isEqualTo(".jpg");
        }

        @Test
        @DisplayName("Validates raw bytes using validateImageBytes")
        void shouldValidateRawBytes() {
            String ext = MediaValidationUtils.validateImageBytes(
                VALID_PNG_BYTES,
                "image.png",
                "image/png"
            );
            assertThat(ext).isEqualTo(".png");
        }
    }

    @Nested
    @DisplayName("Failure image validations")
    class FailureScenarios {

        @Test
        @DisplayName("Rejects null file")
        void shouldRejectNullFile() {
            assertThatThrownBy(() -> MediaValidationUtils.validateImage(null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("File is empty or missing.");
        }

        @Test
        @DisplayName("Rejects empty file")
        void shouldRejectEmptyFile() {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
            );

            assertThatThrownBy(() -> MediaValidationUtils.validateImage(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("File is empty or missing.");
        }

        @Test
        @DisplayName("Rejects file exceeding 5MB limit")
        void shouldRejectFileExceeding5MB() {
            byte[] largeBytes =
                new byte[(int) MediaValidationUtils.MAX_FILE_SIZE_BYTES + 1];
            System.arraycopy(
                VALID_JPEG_BYTES,
                0,
                largeBytes,
                0,
                VALID_JPEG_BYTES.length
            );

            MockMultipartFile file = new MockMultipartFile(
                "file",
                "huge.jpg",
                "image/jpeg",
                largeBytes
            );

            assertThatThrownBy(() -> MediaValidationUtils.validateImage(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                    "File size exceeds the 5MB maximum limit."
                );
        }

        @ParameterizedTest
        @ValueSource(
            strings = {
                "application/pdf",
                "text/plain",
                "image/gif",
                "application/octet-stream",
            }
        )
        @DisplayName("Rejects unsupported MIME types")
        void shouldRejectUnsupportedMime(String unsupportedMime) {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                unsupportedMime,
                VALID_JPEG_BYTES
            );

            assertThatThrownBy(() -> MediaValidationUtils.validateImage(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported MIME type");
        }

        @ParameterizedTest
        @ValueSource(
            strings = { "file.gif", "file.pdf", "file.exe", "file.txt", "file" }
        )
        @DisplayName("Rejects unsupported or missing extensions")
        void shouldRejectUnsupportedExtension(String filename) {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                "image/jpeg",
                VALID_JPEG_BYTES
            );

            assertThatThrownBy(() -> MediaValidationUtils.validateImage(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported file extension");
        }

        @Test
        @DisplayName("Rejects file with mismatched magic bytes (HTML in .jpg)")
        void shouldRejectMismatchedMagicBytes() {
            byte[] fakeJpeg =
                "<!DOCTYPE html><html><body>malicious</body></html>".getBytes();
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.jpg",
                "image/jpeg",
                fakeJpeg
            );

            assertThatThrownBy(() -> MediaValidationUtils.validateImage(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                    "File content does not match declared image format."
                );
        }

        @Test
        @DisplayName("Rejects PNG bytes named as .jpg")
        void shouldRejectPngBytesWithJpgExtension() {
            MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.jpg",
                "image/jpeg",
                VALID_PNG_BYTES
            );

            assertThatThrownBy(() -> MediaValidationUtils.validateImage(file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                    "File content does not match declared image format."
                );
        }

        @Test
        @DisplayName("Rejects raw bytes exceeding 5MB")
        void shouldRejectRawBytesExceeding5MB() {
            byte[] largeBytes =
                new byte[(int) MediaValidationUtils.MAX_FILE_SIZE_BYTES + 1];
            assertThatThrownBy(() ->
                MediaValidationUtils.validateImageBytes(
                    largeBytes,
                    "image.png",
                    "image/png"
                )
            )
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(
                    "File size exceeds the 5MB maximum limit."
                );
        }
    }
}
