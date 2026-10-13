package com.unihub.workshop.workers.ai_processing.outbound.ai_providers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockAiSummarizationAdapterTest {

    @Test
    @DisplayName(
        "Should return canned structured Markdown summary with all required sections"
    )
    void shouldReturnCannedSummary() {
        MockAiSummarizationAdapter adapter = new MockAiSummarizationAdapter();
        String summary = adapter.summarizeText("Sample brochure text");

        assertThat(summary).isNotNull();
        assertThat(summary).contains("## Workshop Summary");
        assertThat(summary).contains("### Objectives");
        assertThat(summary).contains("### Target Audience");
        assertThat(summary).contains("### Key Highlights / Agenda");
        assertThat(summary).contains("### Prerequisites & Speaker Info");
    }
}
