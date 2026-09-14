package com.unihub.workshop.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final Object error;
    private Metadata metadata;

    private ApiResponse(boolean success, String message, T data, Object error) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.error = error;
        this.metadata = null;
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static ApiResponse<Void> error(Object errorObject) {
        return new ApiResponse<>(false, null, null, errorObject);
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

    public Object getError() {
        return error;
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
