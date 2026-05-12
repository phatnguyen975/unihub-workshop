package com.unihub.workshop.iam.presentation.mapper;

import com.unihub.workshop.shared.exception.ApiResponse;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public ApiResponse.Metadata toMetadata(HttpStatus status) {
        return new ApiResponse.Metadata(
                status.value(),
                resolveProcessedBy(),
                OffsetDateTime.now(ZoneOffset.UTC).toString());
    }

    private String resolveProcessedBy() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}
