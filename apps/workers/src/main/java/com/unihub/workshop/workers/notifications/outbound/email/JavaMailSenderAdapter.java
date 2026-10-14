package com.unihub.workshop.workers.notifications.outbound.email;

import com.unihub.workshop.workers.notifications.core.models.EmailMessage;
import com.unihub.workshop.workers.notifications.core.ports.out.EmailSenderPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JavaMailSenderAdapter implements EmailSenderPort {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    public JavaMailSenderAdapter(
        JavaMailSender mailSender,
        @Value(
            "${app.notifications.mail.from:noreply@unihub.edu.vn}"
        ) String fromAddress,
        @Value(
            "${app.notifications.mail.from-name:UniHub Workshop}"
        ) String fromName
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    @CircuitBreaker(name = "emailProvider")
    public void send(EmailMessage message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                true,
                "UTF-8"
            );
            helper.setFrom(fromAddress, fromName);
            helper.setTo(message.recipientAddress());
            helper.setSubject(message.subject());
            helper.setText(message.htmlBody(), true);

            if (message.inlineAttachments() != null) {
                for (Map.Entry<String, byte[]> entry : message
                    .inlineAttachments()
                    .entrySet()) {
                    helper.addInline(
                        entry.getKey(),
                        new ByteArrayResource(entry.getValue()),
                        "image/png"
                    );
                }
            }

            mailSender.send(mimeMessage);
            log.info(
                "Dispatched email via SMTP to '{}'",
                message.recipientAddress()
            );
        } catch (Exception e) {
            log.error(
                "Failed to dispatch email via SMTP to '{}': {}",
                message.recipientAddress(),
                e.getMessage()
            );
            throw new RuntimeException(
                "SMTP delivery failed: " + e.getMessage(),
                e
            );
        }
    }
}
