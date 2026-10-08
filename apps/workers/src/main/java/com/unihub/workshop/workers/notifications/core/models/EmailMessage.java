package com.unihub.workshop.workers.notifications.core.models;

public record EmailMessage(
    String recipientAddress,
    String subject,
    String htmlBody
) {
    public EmailMessage {
        if (recipientAddress == null || recipientAddress.isBlank()) {
            throw new IllegalArgumentException(
                "recipientAddress must not be null or blank"
            );
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException(
                "subject must not be null or blank"
            );
        }
        if (htmlBody == null) {
            throw new IllegalArgumentException("htmlBody must not be null");
        }
    }
}
