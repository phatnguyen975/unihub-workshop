package com.unihub.workshop.workers.notifications.outbound.email;

import com.unihub.workshop.workers.notifications.core.ports.out.EmailRendererPort;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThymeleafEmailRenderer implements EmailRendererPort {

    private final ITemplateEngine templateEngine;

    @Override
    public String render(
        String templateName,
        Map<String, Object> templateModel
    ) {
        Context context = new Context();
        if (templateModel != null) {
            templateModel.forEach(context::setVariable);
        }

        String templatePath = templateName.startsWith("email/")
            ? templateName
            : "email/" + templateName;

        log.debug("Rendering Thymeleaf email template: '{}'", templatePath);
        return templateEngine.process(templatePath, context);
    }
}
