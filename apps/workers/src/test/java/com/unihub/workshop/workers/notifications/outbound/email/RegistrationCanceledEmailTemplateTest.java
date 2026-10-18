package com.unihub.workshop.workers.notifications.outbound.email;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class RegistrationCanceledEmailTemplateTest {

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
        "Should render free registration cancellation email with workshop details and free notice"
    )
    void shouldRenderFreeRegistrationCancellationEmail() {
        Map<String, Object> model = new HashMap<>();
        model.put("studentName", "Nguyen Van An");
        model.put("workshopTitle", "Advanced Cloud Architecture");
        model.put("startsAt", "2026-11-10 09:00 AM");
        model.put("endsAt", "2026-11-10 12:00 PM");
        model.put("location", "Hall A");
        model.put("canceledByRole", "STUDENT");
        model.put("actionUrl", "/workshops/advanced-cloud-architecture");

        String html = renderer.render("registration-canceled", model);

        assertNotNull(html);
        assertTrue(
            html.contains("UniHub Workshop"),
            "Must contain header fragment"
        );
        assertTrue(
            html.contains("Registration Canceled"),
            "Must contain cancellation title"
        );
        assertTrue(html.contains("Nguyen Van An"), "Must contain student name");
        assertTrue(
            html.contains("Advanced Cloud Architecture"),
            "Must contain workshop title"
        );
        assertTrue(
            html.contains("2026-11-10 09:00 AM"),
            "Must contain startsAt"
        );
        assertTrue(html.contains("Hall A"), "Must contain location");
        assertTrue(html.contains("STUDENT"), "Must contain canceler role");
        assertTrue(
            html.contains("This was a free registration"),
            "Must contain free notice"
        );
        assertFalse(
            html.contains("Refund Details"),
            "Free registration must not show refund details"
        );
        assertTrue(
            html.contains("/workshops/advanced-cloud-architecture"),
            "Must contain actionUrl"
        );
    }

    @Test
    @DisplayName(
        "Should render paid registration cancellation email with refund amount and currency"
    )
    void shouldRenderPaidRegistrationCancellationEmail() {
        Map<String, Object> model = new HashMap<>();
        model.put("studentName", "Tran Thi Binh");
        model.put("workshopTitle", "Full-Stack React & Spring");
        model.put("startsAt", "2026-11-15 02:00 PM");
        model.put("endsAt", "2026-11-15 05:00 PM");
        model.put("location", "Room 302");
        model.put("canceledByRole", "ORGANIZER");
        model.put("refundAmount", new BigDecimal("50.00"));
        model.put("currency", "USD");
        model.put("reason", "Organizer scheduling conflict");
        model.put("actionUrl", "/workshops/full-stack-react");

        String html = renderer.render("registration-canceled", model);

        assertNotNull(html);
        assertTrue(html.contains("Tran Thi Binh"), "Must contain student name");
        assertTrue(
            html.contains("Full-Stack React &amp; Spring"),
            "Must contain workshop title"
        );
        assertTrue(html.contains("ORGANIZER"), "Must contain canceler role");
        assertTrue(
            html.contains("Organizer scheduling conflict"),
            "Must contain reason"
        );
        assertTrue(
            html.contains("Refund Details"),
            "Must contain refund details header"
        );
        assertTrue(html.contains("50.00"), "Must contain refund amount");
        assertTrue(html.contains("USD"), "Must contain currency");
        assertFalse(
            html.contains("This was a free registration"),
            "Paid registration must not show free notice"
        );
    }

    @Test
    @DisplayName("Should properly HTML-escape reason to prevent XSS attacks")
    void shouldProperlyHtmlEscapeReason() {
        Map<String, Object> model = new HashMap<>();
        model.put("studentName", "Le Van Cuong");
        model.put("workshopTitle", "Security Workshop");
        model.put("canceledByRole", "STUDENT");
        model.put("reason", "<b>Need break</b> <script>alert('xss')</script>");

        String html = renderer.render("registration-canceled", model);

        assertNotNull(html);
        assertTrue(
            html.contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;"),
            "Reason must be HTML-escaped"
        );
        assertFalse(
            html.contains("<script>alert('xss')</script>"),
            "Raw unescaped script tag must not be present"
        );
    }

    @Test
    @DisplayName("Should render gracefully when optional fields are null")
    void shouldRenderGracefullyWithNullOptionalFields() {
        Map<String, Object> model = new HashMap<>();
        model.put("workshopTitle", "Sample Workshop");

        String html = renderer.render("registration-canceled", model);

        assertNotNull(html);
        assertTrue(
            html.contains("Student"),
            "Default greeting when studentName is null"
        );
        assertTrue(
            html.contains("Sample Workshop"),
            "Must contain workshop title"
        );
        assertTrue(
            html.contains("This was a free registration"),
            "Must default to free registration notice"
        );
        assertTrue(
            html.contains("/workshops"),
            "Must fallback to /workshops link"
        );
    }
}
