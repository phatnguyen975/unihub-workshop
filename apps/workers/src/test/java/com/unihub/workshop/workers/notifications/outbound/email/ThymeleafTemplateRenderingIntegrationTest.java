package com.unihub.workshop.workers.notifications.outbound.email;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class ThymeleafTemplateRenderingIntegrationTest {

    private ThymeleafEmailRenderer renderer;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver =
            new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        renderer = new ThymeleafEmailRenderer(engine);
    }

    @Test
    @DisplayName(
        "Should render otp-activation template with English text, header/footer fragments, and dynamic OTP"
    )
    void shouldRenderOtpActivationTemplateWithFragmentsAndOtp() {
        String html = renderer.render(
            "otp-activation",
            Map.of("otp", "849201")
        );

        assertNotNull(html);
        assertTrue(
            html.contains("UniHub Workshop"),
            "Must contain header fragment title"
        );
        assertTrue(
            html.contains("Skills &amp; Career Week — VNU-HCM"),
            "Must contain header fragment subtitle"
        );
        assertTrue(
            html.contains("Activate Your UniHub Account"),
            "Must contain English activation heading"
        );
        assertTrue(
            html.contains("849201"),
            "Must dynamically interpolate the OTP value"
        );
        assertTrue(
            html.contains("University of Science"),
            "Must contain footer fragment university name"
        );
    }

    @Test
    @DisplayName(
        "Should render otp-password-reset template with English text, header/footer fragments, and dynamic OTP"
    )
    void shouldRenderOtpPasswordResetTemplateWithFragmentsAndOtp() {
        String html = renderer.render(
            "otp-password-reset",
            Map.of("otp", "551920")
        );

        assertNotNull(html);
        assertTrue(
            html.contains("UniHub Workshop"),
            "Must contain header fragment title"
        );
        assertTrue(
            html.contains("Password Reset Request"),
            "Must contain English password reset heading"
        );
        assertTrue(
            html.contains("551920"),
            "Must dynamically interpolate the OTP value"
        );
        assertTrue(
            html.contains("University of Science"),
            "Must contain footer fragment university name"
        );
    }

    @Test
    @DisplayName(
        "Should render staff-assigned template with fragments and dynamic workshop info"
    )
    void shouldRenderStaffAssignedTemplate() {
        String html = renderer.render(
            "email/staff-assigned",
            Map.of(
                "workshopTitle",
                "Advanced Cloud Architecture",
                "startsAt",
                "2026-10-15 09:00 AM",
                "location",
                "Auditorium 1"
            )
        );

        assertNotNull(html);
        assertTrue(html.contains("UniHub Workshop"));
        assertTrue(html.contains("Workshop Staff Assignment"));
        assertTrue(html.contains("Advanced Cloud Architecture"));
        assertTrue(html.contains("2026-10-15 09:00 AM"));
        assertTrue(html.contains("Auditorium 1"));
        assertTrue(html.contains("University of Science"));
    }

    @Test
    @DisplayName(
        "Should render staff-revoked template with fragments and dynamic workshop info"
    )
    void shouldRenderStaffRevokedTemplate() {
        String html = renderer.render(
            "email/staff-revoked",
            Map.of("workshopTitle", "Advanced Cloud Architecture")
        );

        assertNotNull(html);
        assertTrue(html.contains("UniHub Workshop"));
        assertTrue(html.contains("Staff Assignment Update"));
        assertTrue(html.contains("Advanced Cloud Architecture"));
        assertTrue(html.contains("University of Science"));
    }

    @Test
    @DisplayName(
        "Should render user-provisioned template with fragments and dynamic temporary password"
    )
    void shouldRenderUserProvisionedTemplate() {
        String html = renderer.render(
            "email/user-provisioned",
            Map.of(
                "fullName",
                "Nguyen Van A",
                "temporaryPassword",
                "TempSecret99!"
            )
        );

        assertNotNull(html);
        assertTrue(html.contains("UniHub Workshop"));
        assertTrue(html.contains("Welcome to UniHub Workshop"));
        assertTrue(html.contains("Nguyen Van A"));
        assertTrue(html.contains("TempSecret99!"));
        assertTrue(html.contains("University of Science"));
    }

    @Test
    @DisplayName(
        "Should render user-status-changed template with fragments and dynamic status"
    )
    void shouldRenderUserStatusChangedTemplate() {
        String html = renderer.render(
            "email/user-status-changed",
            Map.of("fullName", "Tran Thi B", "status", "ACTIVE")
        );

        assertNotNull(html);
        assertTrue(html.contains("UniHub Workshop"));
        assertTrue(html.contains("Account Status Update"));
        assertTrue(html.contains("Tran Thi B"));
        assertTrue(html.contains("ACTIVE"));
        assertTrue(html.contains("University of Science"));
    }

    @Test
    @DisplayName(
        "Should render ingestion-completed template with fragments and dynamic file key"
    )
    void shouldRenderIngestionCompletedTemplate() {
        String html = renderer.render(
            "email/ingestion-completed",
            Map.of(
                "recipientName",
                "Organizer One",
                "sourceFileKey",
                "students-2026-10-07.csv",
                "actionUrl",
                "/admin/import"
            )
        );

        assertNotNull(html);
        assertTrue(html.contains("UniHub Workshop"));
        assertTrue(html.contains("Student Directory Ingestion Completed"));
        assertTrue(html.contains("Organizer One"));
        assertTrue(html.contains("students-2026-10-07.csv"));
        assertTrue(html.contains("/admin/import"));
        assertTrue(html.contains("University of Science"));
    }

    @Test
    @DisplayName(
        "Should render ingestion-rejected template with fragments and dynamic file key"
    )
    void shouldRenderIngestionRejectedTemplate() {
        String html = renderer.render(
            "email/ingestion-rejected",
            Map.of(
                "recipientName",
                "Organizer Two",
                "sourceFileKey",
                "students-corrupted.csv",
                "actionUrl",
                "/admin/import"
            )
        );

        assertNotNull(html);
        assertTrue(html.contains("UniHub Workshop"));
        assertTrue(html.contains("Student Directory Ingestion Rejected"));
        assertTrue(html.contains("Organizer Two"));
        assertTrue(html.contains("students-corrupted.csv"));
        assertTrue(html.contains("/admin/import"));
        assertTrue(html.contains("University of Science"));
    }
}
