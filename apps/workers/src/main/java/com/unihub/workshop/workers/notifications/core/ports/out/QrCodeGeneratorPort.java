package com.unihub.workshop.workers.notifications.core.ports.out;

public interface QrCodeGeneratorPort {
    /**
     * Encodes the given textual content into a QR code PNG image.
     *
     * @param content text/JSON content to encode
     * @param width image width in pixels
     * @param height image height in pixels
     * @return raw PNG image bytes
     */
    byte[] generateQrPng(String content, int width, int height);
}
