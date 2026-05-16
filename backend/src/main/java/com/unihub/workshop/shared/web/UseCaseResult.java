package com.unihub.workshop.shared.web;

import org.springframework.http.HttpStatus;

public record UseCaseResult<T>(
        HttpStatus status,
        String message,
        T data) {
}
