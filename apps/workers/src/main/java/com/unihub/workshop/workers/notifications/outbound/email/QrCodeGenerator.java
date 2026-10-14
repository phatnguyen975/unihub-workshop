package com.unihub.workshop.workers.notifications.outbound.email;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.unihub.workshop.workers.notifications.core.ports.out.QrCodeGeneratorPort;
import java.io.ByteArrayOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class QrCodeGenerator implements QrCodeGeneratorPort {

    @Override
    public byte[] generateQrPng(String content, int width, int height) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException(
                "QR code content must not be null or blank"
            );
        }
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(
                content,
                BarcodeFormat.QR_CODE,
                width,
                height
            );
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate QR code PNG: {}", e.getMessage(), e);
            throw new RuntimeException(
                "Failed to generate QR code: " + e.getMessage(),
                e
            );
        }
    }
}
