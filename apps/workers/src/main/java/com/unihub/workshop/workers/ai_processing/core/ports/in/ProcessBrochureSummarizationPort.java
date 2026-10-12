package com.unihub.workshop.workers.ai_processing.core.ports.in;

import com.unihub.workshop.workers.ai_processing.core.models.BrochureSummarizationRequest;

public interface ProcessBrochureSummarizationPort {
    void process(BrochureSummarizationRequest request);
}
