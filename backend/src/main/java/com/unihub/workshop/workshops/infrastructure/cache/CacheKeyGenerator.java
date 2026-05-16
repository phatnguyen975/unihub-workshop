package com.unihub.workshop.workshops.infrastructure.cache;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class CacheKeyGenerator {

    public String generateListKey(int page, int size, String query, Integer minCapacity, String organizerId,
            String sort) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append("page:").append(page);
        keyBuilder.append("|size:").append(size);
        if (query != null && !query.isEmpty()) {
            keyBuilder.append("|q:").append(query);
        }
        if (minCapacity != null) {
            keyBuilder.append("|minCap:").append(minCapacity);
        }
        if (organizerId != null && !organizerId.isEmpty()) {
            keyBuilder.append("|org:").append(organizerId);
        }
        if (sort != null && !sort.isEmpty()) {
            keyBuilder.append("|sort:").append(sort);
        }
        return hashKey(keyBuilder.toString());
    }

    public String generateStaffAvailableKey(java.util.UUID workshopId, int page, int size) {
        String raw = "wid:" + (workshopId == null ? "null" : workshopId.toString()) + "|page:" + page + "|size:" + size;
        return hashKey(raw);
    }

    public String generateManagementListKey(String organizerId, int page, int size, String status) {
        StringBuilder raw = new StringBuilder();
        raw.append("org:").append(organizerId == null ? "null" : organizerId);
        raw.append("|page:").append(page);
        raw.append("|size:").append(size);
        if (status != null && !status.isBlank()) raw.append("|status:").append(status.trim());
        return hashKey(raw.toString());
    }

    public String generateAnalyticsKey(java.util.UUID workshopId) {
        return hashKey("analytics:wid:" + (workshopId == null ? "null" : workshopId.toString()));
    }

    public String generateAttendeesKey(java.util.UUID workshopId, int page, int size, String status) {
        StringBuilder raw = new StringBuilder();
        raw.append("wid:").append(workshopId == null ? "null" : workshopId.toString());
        raw.append("|page:").append(page);
        raw.append("|size:").append(size);
        if (status != null && !status.isBlank()) raw.append("|status:").append(status.trim());
        return hashKey(raw.toString());
    }

    private String hashKey(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return input.replace("|", "-").replace(":", "");
        }
    }
}
