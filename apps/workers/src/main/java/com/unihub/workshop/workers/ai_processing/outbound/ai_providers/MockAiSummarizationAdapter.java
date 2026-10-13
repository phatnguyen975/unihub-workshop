package com.unihub.workshop.workers.ai_processing.outbound.ai_providers;

import com.unihub.workshop.workers.ai_processing.core.ports.out.AiSummarizationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
    name = "app.ai.provider",
    havingValue = "mock",
    matchIfMissing = true
)
public class MockAiSummarizationAdapter implements AiSummarizationPort {

    private static final String CANNED_SUMMARY = """
    ## Workshop Summary

    ### Objectives
    - Provide comprehensive practical and theoretical knowledge on the presented topics.

    ### Target Audience
    - University students, academic staff, and industry attendees.

    ### Key Highlights / Agenda
    - In-depth exploration of core principles and industry best practices.
    - Interactive hands-on exercises, demonstrations, and expert Q&A.

    ### Prerequisites & Speaker Info
    - No strict prerequisites required; open to all interested participants.
    - Delivered by experienced guest industry specialists and faculty instructors.
    """;

    @Override
    public String summarizeText(String text) {
        log.info(
            "MockAiSummarizationAdapter: Generating canned summary for input text of length {}",
            text != null ? text.length() : 0
        );
        return CANNED_SUMMARY.trim();
    }
}
