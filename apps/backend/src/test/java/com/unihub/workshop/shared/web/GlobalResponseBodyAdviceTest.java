package com.unihub.workshop.shared.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
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
    void supports_ShouldReturnTrue_ForAnyType() throws NoSuchMethodException {
        MethodParameter methodParameter = new MethodParameter(TestController.class.getMethod("getProblemDetail"), -1);
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

        ApiResponse<String> existingResponse = ApiResponse.success("Custom Data", "Custom Message");
        existingResponse.setMetadata(new ApiResponse.Metadata(traceId, "test-node"));
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
    void beforeBodyWrite_ShouldWrapProblemDetailInErrorApiResponse() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("/api/v1/test"));

        String traceId = UUID.randomUUID().toString();
        MDC.put(RequestLoggingFilter.MDC_TRACE_ID, traceId);
        MDC.put(RequestLoggingFilter.MDC_PROCESSED_BY, "test-node");

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Not found");
        problemDetail.setProperty("traceId", traceId); // simulate it was set earlier

        Object result = advice.beforeBodyWrite(problemDetail, null, null, null, request, null);

        assertThat(result).isInstanceOf(ApiResponse.class);
        ApiResponse<?> apiResponse = (ApiResponse<?>) result;
        assertThat(apiResponse.isSuccess()).isFalse();
        assertThat(apiResponse.getData()).isNull();
        assertThat(apiResponse.getMessage()).isNull();
        
        ProblemDetail pdResult = (ProblemDetail) apiResponse.getError();
        assertThat(pdResult).isSameAs(problemDetail);
        assertThat(pdResult.getProperties()).doesNotContainKey("traceId");
        
        assertThat(apiResponse.getMetadata().getTraceId()).isEqualTo(traceId);
        assertThat(apiResponse.getMetadata().getProcessedBy()).isEqualTo("test-node");
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
