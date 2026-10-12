package com.unihub.workshop.workers.ai_processing.core.exceptions;

public class AiSummarizationTransientException extends RuntimeException {

    public AiSummarizationTransientException(String message) {
        super(message);
    }

    public AiSummarizationTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
