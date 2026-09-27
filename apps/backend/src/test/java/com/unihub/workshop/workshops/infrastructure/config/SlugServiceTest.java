package com.unihub.workshop.workshops.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlugServiceTest {

    private SlugService slugService;

    @BeforeEach
    void setUp() {
        slugService = new SlugService();
    }

    @Test
    @DisplayName(
        "Generates ASCII kebab-case slug for English title with unique suffix"
    )
    void shouldGenerateKebabCaseForEnglishTitle() {
        String slug = slugService.generateSlug(
            "Introduction to Machine Learning"
        );

        assertThat(slug).startsWith("introduction-to-machine-learning-");
        assertThat(slug).matches("^[a-z0-9]+(-[a-z0-9]+)+$");
    }

    @Test
    @DisplayName(
        "Generates ASCII kebab-case slug for Vietnamese title removing diacritics"
    )
    void shouldGenerateKebabCaseForVietnameseTitle() {
        String slug = slugService.generateSlug(
            "Hội thảo Trí tuệ Nhân tạo và Ứng dụng Thực tiễn"
        );

        assertThat(slug).startsWith(
            "hoi-thao-tri-tue-nhan-tao-va-ung-dung-thuc-tien-"
        );
        // Must contain NO non-ASCII or Vietnamese diacritic characters
        assertThat(slug).matches("^[a-z0-9]+(-[a-z0-9]+)+$");
    }

    @Test
    @DisplayName(
        "Generates different suffixes on subsequent calls for same title"
    )
    void shouldGenerateUniqueSuffixes() {
        String slug1 = slugService.generateSlug("Same Title");
        String slug2 = slugService.generateSlug("Same Title");

        assertThat(slug1).isNotEqualTo(slug2);
        assertThat(slug1).startsWith("same-title-");
        assertThat(slug2).startsWith("same-title-");
    }

    @Test
    @DisplayName("Handles titles with special punctuation gracefully")
    void shouldHandlePunctuationGracefully() {
        String slug = slugService.generateSlug(
            "Workshop: Java 25 & Spring Boot 4.0! [2026]"
        );

        assertThat(slug).matches("^[a-z0-9]+(-[a-z0-9]+)+$");
    }

    @Test
    @DisplayName(
        "Guarantees slug length does not exceed 255 chars even with a 255-character title"
    )
    void shouldNeverExceedDatabaseColumnLength_withMaxLengthTitle() {
        String longTitle = "a".repeat(255);
        String slug = slugService.generateSlug(longTitle);

        assertThat(slug.length()).isLessThanOrEqualTo(255);
        assertThat(slug).matches("^[a-z0-9]+(-[a-z0-9]+)+$");
    }
}
