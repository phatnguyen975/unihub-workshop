package com.unihub.workshop.shared.web;

import java.util.List;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Metadata metadata,
        ProblemDetails error) {

    public static <T> ApiResponse<T> success(T data, String message, Metadata metadata) {
        return new ApiResponse<>(true, message, data, metadata, null);
    }

    public static <T> ApiResponse<T> failure(String message, Metadata metadata, ProblemDetails error) {
        return new ApiResponse<>(false, message, null, metadata, error);
    }

    public record Metadata(int statusCode, String processedBy, String timestamp) {
    }

    public record ProblemDetails(
            String type,
            String title,
            String detail,
            String instance,
            List<String> validationErrors) {
    }
}
