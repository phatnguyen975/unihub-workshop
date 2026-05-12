package com.unihub.workshop.iam.application.usecases;

import org.springframework.http.HttpStatus;

public record UseCaseResult<T>(
        HttpStatus status,
        String message,
        T data) {
}
