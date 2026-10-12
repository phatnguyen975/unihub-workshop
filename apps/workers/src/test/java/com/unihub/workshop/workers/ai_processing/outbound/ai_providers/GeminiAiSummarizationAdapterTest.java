package com.unihub.workshop.workers.ai_processing.outbound.ai_providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unihub.workshop.workers.ai_processing.outbound.config.AiProcessingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiAiSummarizationAdapterTest {

    @Test
    @DisplayName(
        "Should throw IllegalStateException when Gemini API key is missing"
    )
    void shouldThrowWhenApiKeyMissing() {
        AiProcessingProperties properties = new AiProcessingProperties();
        properties.getGemini().setApiKey("");

        RestClient restClient = RestClient.builder().build();
        GeminiAiSummarizationAdapter adapter = new GeminiAiSummarizationAdapter(
            properties,
            restClient
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
        RestClient restClient = builder.build();

        GeminiAiSummarizationAdapter adapter = new GeminiAiSummarizationAdapter(
            properties,
            restClient
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
                org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                    "https://test.gemini.api/v1beta/models/gemini-3.8-flash:generateContent?key=test-key-123"
                )
            )
            .andExpect(
                org.springframework.test.web.client.match.MockRestRequestMatchers.method(
                    HttpMethod.POST
                )
            )
            .andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
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
}
