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
