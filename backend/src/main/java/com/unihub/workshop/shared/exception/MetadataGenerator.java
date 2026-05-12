package com.unihub.workshop.shared.exception;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.unihub.workshop.shared.web.ApiResponse;

@Component
public class MetadataGenerator {

    private final String processedBy;

    public MetadataGenerator() {
        this.processedBy = resolveProcessedBy();
    }

    public ApiResponse.Metadata create(HttpStatus status) {
        return new ApiResponse.Metadata(status.value(), processedBy, OffsetDateTime.now(ZoneOffset.UTC).toString());
    }

    public String getProcessedBy() {
        return processedBy;
    }

    private String resolveProcessedBy() {
        String hostName = System.getenv("HOSTNAME");
        if (hostName != null && !hostName.isBlank()) {
            return hostName.trim();
        }
        try {
            String localHostName = InetAddress.getLocalHost().getHostName();
            if (localHostName != null && !localHostName.isBlank()) {
                return localHostName;
            }
        } catch (Exception ignored) {
            // Ignore any exceptions and fallback to "unknown"
        }
        return "unknown";
    }
}
