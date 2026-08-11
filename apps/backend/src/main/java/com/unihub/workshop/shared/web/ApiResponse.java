package com.unihub.workshop.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private final boolean success;
    private final String message;
    private final T data;
    private Metadata metadata;

    public ApiResponse(T data, String message) {
        this.success = true;
        this.message = message;
        this.data = data;
    }

    public ApiResponse(T data, String message, String traceId, String processedBy) {
        this.success = true; // Envelope is only used for successful 2xx responses
        this.message = message;
        this.data = data;
        this.metadata = new Metadata(traceId, processedBy);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public static class Metadata {
        private final String processedBy;
        private final String traceId;

        public Metadata(String traceId, String processedBy) {
            this.traceId = traceId;
            this.processedBy = processedBy;
        }

        public String getProcessedBy() {
            return processedBy;
        }

        public String getTraceId() {
            return traceId;
        }
    }
}
