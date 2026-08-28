package com.unihub.workshop.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpExtractorTest {

    @Test
    void extractIp_shouldReturnRemoteAddr_whenXForwardedForIsAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");

        String ip = ClientIpExtractor.extractIp(request);

        assertThat(ip).isEqualTo("192.168.1.1");
    }

    @Test
    void extractIp_shouldReturnLeftmostValue_whenXForwardedForIsPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");

        String ip = ClientIpExtractor.extractIp(request);

        assertThat(ip).isEqualTo("203.0.113.195");
    }

    @Test
    void extractIp_shouldReturnLeftmostValue_whenXForwardedForHasSingleValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        request.addHeader("X-Forwarded-For", "203.0.113.195");

        String ip = ClientIpExtractor.extractIp(request);

        assertThat(ip).isEqualTo("203.0.113.195");
    }

    @Test
    void extractIp_shouldIgnoreEmptyXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        request.addHeader("X-Forwarded-For", "");

        String ip = ClientIpExtractor.extractIp(request);

        assertThat(ip).isEqualTo("192.168.1.1");
    }

    @Test
    void extractIp_shouldIgnoreWhitespaceXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        request.addHeader("X-Forwarded-For", "   ");

        String ip = ClientIpExtractor.extractIp(request);

        assertThat(ip).isEqualTo("192.168.1.1");
    }
}
