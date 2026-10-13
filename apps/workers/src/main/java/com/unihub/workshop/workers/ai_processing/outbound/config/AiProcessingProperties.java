package com.unihub.workshop.workers.ai_processing.outbound.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.ai")
public class AiProcessingProperties {

    private String queue = "q.ai.summarization";
    private String exchange = "unihub.events";
    private String routingKey = "ai.summarization";
    private String dlx = "unihub.dlx";
    private String dlqRoutingKey = "dlq";
    private String provider = "mock";
    private String bucket = "unihub-brochures";
    private int maxAttempts = 3;
    private long backoffInitialSeconds = 5;
    private double backoffMultiplier = 2.0;

    private Gemini gemini = new Gemini();

    @Getter
    @Setter
    public static class Gemini {

        private String model = "gemini-3.8-flash";
        private String apiKey = "";
        private String endpoint = "https://generativelanguage.googleapis.com";
        private int timeoutSeconds = 30;
    }
}
