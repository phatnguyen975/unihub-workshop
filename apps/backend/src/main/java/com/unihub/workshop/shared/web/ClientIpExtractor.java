package com.unihub.workshop.shared.web;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpExtractor {

    private ClientIpExtractor() {}

    /**
     * Extracts the real client IP address from the request.
     *
     * <p>
     * Trusts {@code X-Forwarded-For} only if the direct TCP connection
     * ({@code remoteAddr}) originates from a trusted local or Docker internal proxy
     * network (10.*, 172.*, 192.168.*, 127.0.0.1, ::1). If the request is from
     * an untrusted peer or the header is missing/empty, falls back to {@code remoteAddr}.
     *
     * @param request the incoming HTTP servlet request
     * @return the resolved client IP address, never null (returns empty string if remoteAddr is null)
     */
    public static String extractIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        boolean isInternal =
            remoteAddr != null &&
            (remoteAddr.startsWith("10.") ||
                remoteAddr.startsWith("172.") ||
                remoteAddr.startsWith("192.168.") ||
                remoteAddr.equals("127.0.0.1") ||
                remoteAddr.equals("0:0:0:0:0:0:0:1"));

        if (isInternal) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
                // The X-Forwarded-For can contain multiple IP addresses separated by commas
                // The leftmost IP address is the original client IP
                String[] ips = xForwardedFor.split(",");
                return ips[0].trim();
            }
        }

        return remoteAddr != null ? remoteAddr : "";
    }
}
