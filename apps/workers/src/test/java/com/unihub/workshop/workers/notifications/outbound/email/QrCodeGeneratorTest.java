package com.unihub.workshop.workers.notifications.outbound.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrCodeGeneratorTest {

    private QrCodeGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new QrCodeGenerator();
    }

    @Test
    @DisplayName(
        "generateQrPng: generates valid PNG bytes that can be read back and match input content"
    )
    void generateQrPng_withValidContent_generatesValidPngBytesAndDecodesBack()
        throws Exception {
        String content =
            "{\"payload\":{\"registrationId\":\"a1b2c3d4\"},\"signature\":\"test-sig\"}";
        int width = 300;
        int height = 300;

        byte[] pngBytes = generator.generateQrPng(content, width, height);

        assertThat(pngBytes).isNotNull();
        assertThat(pngBytes.length).isGreaterThan(0);

        // Verify PNG magic bytes: 0x89 0x50 0x4E 0x47
        assertThat(pngBytes[0]).isEqualTo((byte) 0x89);
        assertThat(pngBytes[1]).isEqualTo((byte) 0x50);
        assertThat(pngBytes[2]).isEqualTo((byte) 0x4E);
        assertThat(pngBytes[3]).isEqualTo((byte) 0x47);

        // Decode back via ZXing
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(width);
        assertThat(image.getHeight()).isEqualTo(height);

        BinaryBitmap bitmap = new BinaryBitmap(
            new HybridBinarizer(new BufferedImageLuminanceSource(image))
        );
        Result result = new MultiFormatReader().decode(bitmap);

        assertThat(result.getText()).isEqualTo(content);
    }

    @Test
    @DisplayName(
        "generateQrPng: throws IllegalArgumentException when content is null or blank"
    )
    void generateQrPng_withNullOrBlankContent_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> generator.generateQrPng(null, 300, 300))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be null or blank");

        assertThatThrownBy(() -> generator.generateQrPng("   ", 300, 300))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be null or blank");
    }
}
