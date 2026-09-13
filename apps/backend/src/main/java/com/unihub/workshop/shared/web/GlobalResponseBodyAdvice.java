package com.unihub.workshop.shared.web;

import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

@ControllerAdvice
public class GlobalResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(
        MethodParameter returnType,
        Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
        Object body,
        MethodParameter returnType,
        MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType,
        ServerHttpRequest request,
        ServerHttpResponse response
    ) {
        if (body == null) {
            return null;
        }

        if (
            body instanceof Resource ||
            body instanceof byte[] ||
            body instanceof ResponseBodyEmitter
        ) {
            return body;
        }

        if (body instanceof String) {
            return body;
        }

        if (
            request.getURI().getPath().startsWith("/actuator") ||
            request.getURI().getPath().startsWith("/.well-known")
        ) {
            return body;
        }

        String traceId = RequestLoggingFilter.getCurrentTraceId();
        String processedBy = RequestLoggingFilter.getCurrentProcessedBy();

        if (body instanceof ApiResponse<?> apiResponse) {
            if (apiResponse.getMetadata() == null) {
                apiResponse.setMetadata(
                    new ApiResponse.Metadata(traceId, processedBy)
                );
            }
            return apiResponse;
        }

        if (body instanceof ProblemDetail problemDetail) {
            if (problemDetail.getProperties() != null) {
                problemDetail.getProperties().remove("traceId");
            }
            ApiResponse<Void> apiResponse = ApiResponse.error(problemDetail);
            apiResponse.setMetadata(
                new ApiResponse.Metadata(traceId, processedBy)
            );
            return apiResponse;
        }

        ApiResponse<Object> apiResponse = ApiResponse.success(body, "Success");
        apiResponse.setMetadata(new ApiResponse.Metadata(traceId, processedBy));
        return apiResponse;
    }
}
