package com.unihub.workshop.shared.web;

import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Intercepts all successful API responses before they are serialized and wraps
 * them in the standard {@link ApiResponse} envelope.
 * 
 * <p>
 * Injects the {@code traceId} from the current SLF4J MDC context into the
 * {@code metadata.traceId} field as required by the API Standards.
 */
@ControllerAdvice
public class GlobalResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> parameterType = returnType.getParameterType();
        if (org.springframework.http.ProblemDetail.class.isAssignableFrom(parameterType)) {
            return false;
        }
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {

        if (body instanceof org.springframework.http.ProblemDetail) {
            return body;
        }

        if (body instanceof org.springframework.core.io.Resource || body instanceof byte[]) {
            return body;
        }

        if (body instanceof String) {
            return body;
        }

        if (request.getURI().getPath().startsWith("/actuator")) {
            return body;
        }

        String traceId = MDC.get(RequestLoggingFilter.MDC_TRACE_ID);
        if (traceId == null) {
            traceId = "unknown";
        }

        String processedBy = MDC.get(RequestLoggingFilter.MDC_PROCESSED_BY);
        if (processedBy == null) {
            processedBy = "unknown";
        }

        if (body instanceof ApiResponse<?> apiResponse) {
            if (apiResponse.getMetadata() == null) {
                apiResponse.setMetadata(new ApiResponse.Metadata(traceId, processedBy));
            }
            return apiResponse;
        }

        return new ApiResponse<>(body, "Success", traceId, processedBy);
    }
}
