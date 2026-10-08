package com.unihub.workshop.workers.notifications.outbound.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.IContext;

@ExtendWith(MockitoExtension.class)
class ThymeleafEmailRendererTest {

    @Mock
    private ITemplateEngine templateEngine;

    private ThymeleafEmailRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new ThymeleafEmailRenderer(templateEngine);
    }

    @Test
    @DisplayName(
        "Should prefix template path with email/ and render variables successfully"
    )
    void shouldRenderTemplateSuccessfully() {
        when(
            templateEngine.process(
                eq("email/otp-activation"),
                any(IContext.class)
            )
        ).thenReturn("<html><body>OTP: 123456</body></html>");

        String result = renderer.render(
            "otp-activation",
            Map.of("otp", "123456")
        );

        assertEquals("<html><body>OTP: 123456</body></html>", result);
        verify(templateEngine).process(
            eq("email/otp-activation"),
            any(IContext.class)
        );
    }

    @Test
    @DisplayName(
        "Should avoid double email/ prefix if templateName already contains email/"
    )
    void shouldAvoidDoublePrefix() {
        when(
            templateEngine.process(
                eq("email/otp-password-reset"),
                any(IContext.class)
            )
        ).thenReturn("<html><body>Reset</body></html>");

        String result = renderer.render("email/otp-password-reset", Map.of());

        assertEquals("<html><body>Reset</body></html>", result);
        verify(templateEngine).process(
            eq("email/otp-password-reset"),
            any(IContext.class)
        );
    }
}
