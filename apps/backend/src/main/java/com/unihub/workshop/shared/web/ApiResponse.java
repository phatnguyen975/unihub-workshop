package com.unihub.workshop.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "success", "message", "data", "error", "metadata" })
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final Object error;

    @Setter
    private Metadata metadata;

    private ApiResponse(boolean success, String message, T data, Object error) {
        this(success, message, data, error, null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static ApiResponse<Void> error(Object errorObject) {
        return new ApiResponse<>(false, null, null, errorObject);
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @JsonPropertyOrder({ "processedBy", "traceId" })
    public static class Metadata {

        private final String traceId;
        private final String processedBy;
    }
}
