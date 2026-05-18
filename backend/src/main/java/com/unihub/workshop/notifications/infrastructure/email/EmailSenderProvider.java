package com.unihub.workshop.notifications.infrastructure.email;

public interface EmailSenderProvider {

    void sendEmail(String to, String subject, String htmlBody);
}
