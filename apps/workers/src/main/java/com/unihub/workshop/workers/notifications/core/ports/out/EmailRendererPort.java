package com.unihub.workshop.workers.notifications.core.ports.out;

import java.util.Map;

public interface EmailRendererPort {
    String render(String templateName, Map<String, Object> templateModel);
}
