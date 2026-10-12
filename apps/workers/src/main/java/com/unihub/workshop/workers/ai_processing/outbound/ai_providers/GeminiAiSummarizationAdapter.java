package com.unihub.workshop.workers.ai_processing.outbound.ai_providers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.unihub.workshop.workers.ai_processing.core.ports.out.AiSummarizationPort;
import com.unihub.workshop.workers.ai_processing.outbound.config.AiProcessingProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini")
public class GeminiAiSummarizationAdapter implements AiSummarizationPort {

    private static final String SYSTEM_PROMPT = """
    You are an AI assistant designed to extract and structure educational workshop summaries.
    Summarize the provided brochure text into a standardized Markdown format with exactly these sections:
    ### Objectives
    ### Target Audience
    ### Key Highlights / Agenda
    ### Prerequisites & Speaker Info
    Ensure the output is clean, concise, and structured in valid Markdown.
    """;

    private final RestClient restClient;
    private final AiProcessingProperties properties;

    public GeminiAiSummarizationAdapter(
        AiProcessingProperties properties,
        RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.restClient = restClientBuilder
            .baseUrl(properties.getGemini().getEndpoint())
            .build();
    }

    public GeminiAiSummarizationAdapter(
        AiProcessingProperties properties,
        RestClient restClient
    ) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    @CircuitBreaker(name = "aiProvider")
    public String summarizeText(String text) {
        String apiKey = properties.getGemini().getApiKey();
        String model = properties.getGemini().getModel();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "Google Gemini API key (AI_GEMINI_API_KEY) is not configured."
            );
        }

        String prompt = SYSTEM_PROMPT + "\n\nBrochure Content:\n" + text;

        GeminiRequest request = new GeminiRequest(
            List.of(new Content(List.of(new Part(prompt)))),
            Map.of("temperature", 0.2)
        );

        String uri =
            "/v1beta/models/" + model + ":generateContent?key=" + apiKey;
        log.debug("Calling Google Gemini API model '{}'", model);

        GeminiResponse response = restClient
            .post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(GeminiResponse.class);

        if (
            response == null ||
            response.candidates() == null ||
            response.candidates().isEmpty()
        ) {
            throw new RuntimeException(
                "Gemini API returned empty candidates list."
            );
        }

        Candidate candidate = response.candidates().getFirst();
        if (
            candidate.content() == null ||
            candidate.content().parts() == null ||
            candidate.content().parts().isEmpty()
        ) {
            throw new RuntimeException(
                "Gemini API candidate does not contain text parts."
            );
        }

        return candidate.content().parts().getFirst().text();
    }

    public record GeminiRequest(
        List<Content> contents,
        Map<String, Object> generationConfig
    ) {}

    public record Content(List<Part> parts) {}

    public record Part(String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeminiResponse(List<Candidate> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Content content) {}
}
