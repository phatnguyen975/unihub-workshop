package com.unihub.workshop.iam.application.dto;

public record PaginationMetadata(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {
}
