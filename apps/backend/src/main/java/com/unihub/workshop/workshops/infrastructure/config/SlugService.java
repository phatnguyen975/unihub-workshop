package com.unihub.workshop.workshops.infrastructure.config;

import com.github.slugify.Slugify;
import com.unihub.workshop.workshops.application.ports.out.SlugGeneratorPort;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SlugService implements SlugGeneratorPort {

    private static final int MAX_BASE_LENGTH = 240;
    private final Slugify slugify;

    public SlugService() {
        this.slugify = Slugify.builder()
            .transliterator(true)
            .lowerCase(true)
            .build();
    }

    @Override
    public String generateSlug(String title) {
        String base = title != null ? slugify.slugify(title) : "workshop";
        if (base == null || base.isBlank()) {
            base = "workshop";
        }
        if (base.length() > MAX_BASE_LENGTH) {
            base = base.substring(0, MAX_BASE_LENGTH);
            if (base.endsWith("-")) {
                base = base.substring(0, base.length() - 1);
            }
        }
        String suffix = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);
        return base + "-" + suffix;
    }
}
