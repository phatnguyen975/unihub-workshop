package com.unihub.workshop.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unihub.workshop.shared.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MediaTypeTest {

    @ParameterizedTest
    @CsvSource({
        "COVER_IMAGE, COVER_IMAGE, workshops/covers",
        "cover_image, COVER_IMAGE, workshops/covers",
        "SPEAKER_AVATAR, SPEAKER_AVATAR, speakers/avatars",
        "speaker_avatar, SPEAKER_AVATAR, speakers/avatars",
        "USER_AVATAR, USER_AVATAR, users/avatars",
        "user_avatar, USER_AVATAR, users/avatars",
    })
    @DisplayName(
        "Parses valid media type case-insensitively and maps to correct storage folder"
    )
    void shouldParseValidMediaType(
        String input,
        MediaType expectedType,
        String expectedFolder
    ) {
        MediaType mediaType = MediaType.fromString(input);
        assertThat(mediaType).isEqualTo(expectedType);
        assertThat(mediaType.getFolderPrefix()).isEqualTo(expectedFolder);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t" })
    @DisplayName("Throws BadRequestException when media type is null or blank")
    void shouldRejectNullOrBlank(String input) {
        assertThatThrownBy(() -> MediaType.fromString(input))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("Media type is required");
    }

    @Test
    @DisplayName(
        "Throws BadRequestException listing all allowed media types when invalid"
    )
    void shouldRejectInvalidMediaTypeAndListAllAllowed() {
        assertThatThrownBy(() -> MediaType.fromString("UNKNOWN_TYPE"))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "Invalid media type: UNKNOWN_TYPE. Allowed: COVER_IMAGE, SPEAKER_AVATAR, USER_AVATAR"
            );
    }
}
