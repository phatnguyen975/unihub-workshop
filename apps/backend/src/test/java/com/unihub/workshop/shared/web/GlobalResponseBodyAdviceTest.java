package com.unihub.workshop.shared.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.ServerHttpRequest;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalResponseBodyAdviceTest {

    private GlobalResponseBodyAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new GlobalResponseBodyAdvice();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void supports_ShouldReturnTrue_WhenReturningApiResponse() throws NoSuchMethodException {
        MethodParameter methodParameter = new MethodParameter(TestController.class.getMethod("getApiResponse"), -1);
        boolean supports = advice.supports(methodParameter, null);
        assertThat(supports).isTrue();
    }

    @Test
    void supports_ShouldReturnFalse_WhenReturningProblemDetail() throws NoSuchMethodException {
        MethodParameter methodParameter = new MethodParameter(TestController.class.getMethod("getProblemDetail"), -1);
        boolean supports = advice.supports(methodParameter, null);
        assertThat(supports).isFalse();
    }

    @Test
    void supports_ShouldReturnTrue_WhenReturningOtherObjects() throws NoSuchMethodException {
        MethodParameter methodParameter = new MethodParameter(TestController.class.getMethod("getString"), -1);
        boolean supports = advice.supports(methodParameter, null);
        assertThat(supports).isTrue();
    }

    @Test
    void beforeBodyWrite_ShouldInjectMetadata_WhenBodyIsAlreadyApiResponse() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("/api/v1/test"));

        String traceId = UUID.randomUUID().toString();
        MDC.put(RequestLoggingFilter.MDC_TRACE_ID, traceId);
        MDC.put(RequestLoggingFilter.MDC_PROCESSED_BY, "test-node");

        ApiResponse<String> existingResponse = new ApiResponse<>("Custom Data", "Custom Message");
        Object result = advice.beforeBodyWrite(existingResponse, null, null, null, request, null);

        assertThat(result).isSameAs(existingResponse);
        ApiResponse<?> apiResponse = (ApiResponse<?>) result;
        assertThat(apiResponse.getData()).isEqualTo("Custom Data");
        assertThat(apiResponse.getMessage()).isEqualTo("Custom Message");
        assertThat(apiResponse.getMetadata().getTraceId()).isEqualTo(traceId);
        assertThat(apiResponse.getMetadata().getProcessedBy()).isEqualTo("test-node");
    }

    @Test
    void beforeBodyWrite_ShouldReturnStringDirectly() throws NoSuchMethodException {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("/api/v1/test"));

        Object result = advice.beforeBodyWrite("raw string", null, null, null, request, null);

        assertThat(result).isEqualTo("raw string");
    }

    @Test
    void beforeBodyWrite_ShouldReturnBodyDirectly_ForActuatorPaths() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("/actuator/health"));

        Object body = new Object();
        Object result = advice.beforeBodyWrite(body, null, null, null, request, null);

        assertThat(result).isSameAs(body);
    }

    @Test
    void beforeBodyWrite_ShouldWrapInApiResponseAndInjectTraceIdAndProcessedBy() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("/api/v1/test"));

        String traceId = UUID.randomUUID().toString();
        MDC.put(RequestLoggingFilter.MDC_TRACE_ID, traceId);
        MDC.put(RequestLoggingFilter.MDC_PROCESSED_BY, "test-node");

        Object body = new Object();
        Object result = advice.beforeBodyWrite(body, null, null, null, request, null);

        assertThat(result).isInstanceOf(ApiResponse.class);
        ApiResponse<?> apiResponse = (ApiResponse<?>) result;
        assertThat(apiResponse.getData()).isSameAs(body);
        assertThat(apiResponse.isSuccess()).isTrue();
        assertThat(apiResponse.getMessage()).isEqualTo("Success");
        assertThat(apiResponse.getMetadata().getTraceId()).isEqualTo(traceId);
        assertThat(apiResponse.getMetadata().getProcessedBy()).isEqualTo("test-node");
    }

    @Test
    void beforeBodyWrite_ShouldWrapWithUnknownDefaults_WhenNotInMDC() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("/api/v1/test"));

        Object body = new Object();
        Object result = advice.beforeBodyWrite(body, null, null, null, request, null);

        assertThat(result).isInstanceOf(ApiResponse.class);
        ApiResponse<?> apiResponse = (ApiResponse<?>) result;
        assertThat(apiResponse.getData()).isSameAs(body);
        assertThat(apiResponse.isSuccess()).isTrue();
        assertThat(apiResponse.getMessage()).isEqualTo("Success");
        assertThat(apiResponse.getMetadata().getTraceId()).isEqualTo("unknown");
        assertThat(apiResponse.getMetadata().getProcessedBy()).isEqualTo("unknown");
    }

    @SuppressWarnings("unused")
    private static class TestController {
        public ApiResponse<String> getApiResponse() {
            return null;
        }

        public ProblemDetail getProblemDetail() {
            return null;
        }

        public String getString() {
            return null;
        }
    }
}
