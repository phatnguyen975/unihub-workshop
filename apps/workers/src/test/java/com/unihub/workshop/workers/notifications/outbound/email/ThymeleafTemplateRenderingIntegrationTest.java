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
}
