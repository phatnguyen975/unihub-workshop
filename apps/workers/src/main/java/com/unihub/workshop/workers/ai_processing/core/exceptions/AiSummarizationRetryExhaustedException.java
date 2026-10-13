package com.unihub.workshop.workers.ai_processing.core.exceptions;

public class AiSummarizationRetryExhaustedException extends RuntimeException {

    public AiSummarizationRetryExhaustedException(String message) {
        super(message);
    }

    public AiSummarizationRetryExhaustedException(
        String message,
        Throwable cause
    ) {
        super(message, cause);
    }
}
