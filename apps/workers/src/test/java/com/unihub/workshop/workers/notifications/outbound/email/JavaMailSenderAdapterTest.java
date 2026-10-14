package com.unihub.workshop.workers.notifications.outbound.email;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workers.notifications.core.models.EmailMessage;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class JavaMailSenderAdapterTest {

    @Mock
    private JavaMailSender mailSender;

    private JavaMailSenderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JavaMailSenderAdapter(
            mailSender,
            "noreply@unihub.edu.vn",
            "UniHub Workshop"
        );
    }

    @Test
    @DisplayName("Should create MimeMessage and dispatch via JavaMailSender")
    void shouldSendMimeMessageSuccessfully() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        EmailMessage message = EmailMessage.builder()
            .recipientAddress("student@hcmus.edu.vn")
            .subject("OTP verification code")
            .htmlBody("<html>123456</html>")
            .build();

        assertDoesNotThrow(() -> adapter.send(message));
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName(
        "Should wrap MailException into RuntimeException on delivery failure"
    )
    void shouldWrapExceptionOnFailure() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP server down"))
            .when(mailSender)
            .send(any(MimeMessage.class));

        EmailMessage message = EmailMessage.builder()
            .recipientAddress("student@hcmus.edu.vn")
            .subject("OTP verification code")
            .htmlBody("<html>123456</html>")
            .build();

        assertThrows(RuntimeException.class, () -> adapter.send(message));
    }
}
