package com.unihub.workshop.workshops.infrastructure.config;

import com.github.slugify.Slugify;
import com.unihub.workshop.workshops.application.ports.out.SlugGeneratorPort;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SlugService implements SlugGeneratorPort {

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
        String suffix = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);
        return base + "-" + suffix;
    }
}
