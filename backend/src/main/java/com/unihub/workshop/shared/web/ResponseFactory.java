package com.unihub.workshop.shared.web;

import com.unihub.workshop.shared.exception.MetadataGenerator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ResponseFactory {

    private final MetadataGenerator metadataGenerator;

    public ResponseFactory(MetadataGenerator metadataGenerator) {
        this.metadataGenerator = metadataGenerator;
    }

    public <T> ApiResponse<T> success(T data, String message, HttpStatus status) {
        return ApiResponse.success(data, message, metadataGenerator.create(status));
    }

    public <T> ApiResponse<T> success(T data, String message) {
        return success(data, message, HttpStatus.OK);
    }
}
