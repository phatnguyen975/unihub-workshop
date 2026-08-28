package com.unihub.workshop.shared.web;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpExtractor {

    private ClientIpExtractor() {
    }

    public static String extractIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");

        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            // The X-Forwarded-For can contain multiple IP addresses separated by commas
            // The leftmost IP address is the original client IP
            String[] ips = xForwardedFor.split(",");
            return ips[0].trim();
        }

        return request.getRemoteAddr();
    }
}
