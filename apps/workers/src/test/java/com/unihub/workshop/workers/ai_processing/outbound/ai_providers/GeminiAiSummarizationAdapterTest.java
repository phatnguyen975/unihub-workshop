package com.unihub.workshop.workers.ai_processing.outbound.ai_providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unihub.workshop.workers.ai_processing.outbound.config.AiProcessingProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class GeminiAiSummarizationAdapterTest {

    @Test
    @DisplayName(
        "Should throw IllegalStateException when Gemini API key is missing"
    )
    void shouldThrowWhenApiKeyMissing() {
        AiProcessingProperties properties = new AiProcessingProperties();
        properties.getGemini().setApiKey("");

        RestClient.Builder builder = RestClient.builder();
        GeminiAiSummarizationAdapter adapter = new GeminiAiSummarizationAdapter(
            properties,
            builder
        );

        assertThatThrownBy(() -> adapter.summarizeText("Sample content"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AI_GEMINI_API_KEY");
    }

    @Test
    @DisplayName("Should parse and return summary from Gemini response")
    void shouldReturnSummaryFromGeminiResponse() {
        AiProcessingProperties properties = new AiProcessingProperties();
        properties.getGemini().setApiKey("test-key-123");
        properties.getGemini().setModel("gemini-3.8-flash");
        properties.getGemini().setEndpoint("https://test.gemini.api");

        RestClient.Builder builder = RestClient.builder().baseUrl(
            "https://test.gemini.api"
        );
        MockRestServiceServer server = MockRestServiceServer.bindTo(
            builder
        ).build();
        GeminiAiSummarizationAdapter adapter = new GeminiAiSummarizationAdapter(
            properties,
            builder
        );

        String jsonResponse = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {
                    "text": "## Workshop Summary\\n\\n### Objectives\\nLearn Gemini"
                  }
                ]
              }
            }
          ]
        }
        """;

        server
            .expect(
                MockRestRequestMatchers.requestTo(
                    "https://test.gemini.api/v1beta/models/gemini-3.8-flash:generateContent"
                )
            )
            .andExpect(
                MockRestRequestMatchers.header("x-goog-api-key", "test-key-123")
            )
            .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
            .andRespond(
                MockRestResponseCreators.withSuccess(
                    jsonResponse,
                    MediaType.APPLICATION_JSON
                )
            );

        String result = adapter.summarizeText("Sample brochure text");

        assertThat(result).isEqualTo(
            "## Workshop Summary\n\n### Objectives\nLearn Gemini"
        );
        server.verify();
    }

    @Test
    @DisplayName(
        "Should throw clear exception when prompt is blocked by safety filters"
    )
    void shouldThrowWhenPromptIsBlockedBySafety() {
        AiProcessingProperties properties = new AiProcessingProperties();
        properties.getGemini().setApiKey("test-key-123");
        properties.getGemini().setModel("gemini-3.8-flash");
        properties.getGemini().setEndpoint("https://test.gemini.api");

        RestClient.Builder builder = RestClient.builder().baseUrl(
            "https://test.gemini.api"
        );
        MockRestServiceServer server = MockRestServiceServer.bindTo(
            builder
        ).build();
        GeminiAiSummarizationAdapter adapter = new GeminiAiSummarizationAdapter(
            properties,
            builder
        );

        String jsonResponse = """
        {
          "promptFeedback": {
            "blockReason": "SAFETY"
          }
        }
        """;

        server
            .expect(
                MockRestRequestMatchers.requestTo(
                    "https://test.gemini.api/v1beta/models/gemini-3.8-flash:generateContent"
                )
            )
            .andExpect(
                MockRestRequestMatchers.header("x-goog-api-key", "test-key-123")
            )
            .andRespond(
                MockRestResponseCreators.withSuccess(
                    jsonResponse,
                    MediaType.APPLICATION_JSON
                )
            );

        assertThatThrownBy(() -> adapter.summarizeText("Blocked content"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("safety policy: SAFETY");
    }

    @Test
    @DisplayName(
        "Should throw clear exception when candidate is blocked with finishReason SAFETY"
    )
    void shouldThrowWhenCandidateFinishReasonIsSafety() {
        AiProcessingProperties properties = new AiProcessingProperties();
        properties.getGemini().setApiKey("test-key-123");
        properties.getGemini().setModel("gemini-3.8-flash");
        properties.getGemini().setEndpoint("https://test.gemini.api");

        RestClient.Builder builder = RestClient.builder().baseUrl(
            "https://test.gemini.api"
        );
        MockRestServiceServer server = MockRestServiceServer.bindTo(
            builder
        ).build();
        GeminiAiSummarizationAdapter adapter = new GeminiAiSummarizationAdapter(
            properties,
            builder
        );

        String jsonResponse = """
        {
          "candidates": [
            {
              "finishReason": "SAFETY"
            }
          ]
        }
        """;

        server
            .expect(
                MockRestRequestMatchers.requestTo(
                    "https://test.gemini.api/v1beta/models/gemini-3.8-flash:generateContent"
                )
            )
            .andExpect(
                MockRestRequestMatchers.header("x-goog-api-key", "test-key-123")
            )
            .andRespond(
                MockRestResponseCreators.withSuccess(
                    jsonResponse,
                    MediaType.APPLICATION_JSON
                )
            );

        assertThatThrownBy(() -> adapter.summarizeText("Blocked candidate"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("blocked by safety filters");
    }

    @Test
    @DisplayName(
        "Should be annotated with @CircuitBreaker(name = 'aiProvider')"
    )
    void shouldHaveCircuitBreakerAnnotation() throws NoSuchMethodException {
        var method = GeminiAiSummarizationAdapter.class.getMethod(
            "summarizeText",
            String.class
        );
        var annotation = method.getAnnotation(CircuitBreaker.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isEqualTo("aiProvider");
    }

    @Test
    @DisplayName(
        "Should propagate error when Gemini API returns HTTP 429 rate limit"
    )
    void shouldPropagateErrorWhenApiReturns429() {
        AiProcessingProperties properties = new AiProcessingProperties();
        properties.getGemini().setApiKey("test-key-123");
        properties.getGemini().setModel("gemini-3.8-flash");
        properties.getGemini().setEndpoint("https://test.gemini.api");

        RestClient.Builder builder = RestClient.builder().baseUrl(
            "https://test.gemini.api"
        );
        MockRestServiceServer server = MockRestServiceServer.bindTo(
            builder
        ).build();
        GeminiAiSummarizationAdapter adapter = new GeminiAiSummarizationAdapter(
            properties,
            builder
        );

        server
            .expect(
                MockRestRequestMatchers.requestTo(
                    "https://test.gemini.api/v1beta/models/gemini-3.8-flash:generateContent"
                )
            )
            .andExpect(
                MockRestRequestMatchers.header("x-goog-api-key", "test-key-123")
            )
            .andRespond(
                MockRestResponseCreators.withStatus(
                    HttpStatus.TOO_MANY_REQUESTS
                )
            );

        assertThatThrownBy(() ->
            adapter.summarizeText("Sample text")
        ).isInstanceOf(RestClientResponseException.class);
    }
}
