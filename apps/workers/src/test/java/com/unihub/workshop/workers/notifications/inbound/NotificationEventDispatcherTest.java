package com.unihub.workshop.workers.notifications.inbound;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.workers.notifications.core.ports.in.NotificationEventHandler;
import com.unihub.workshop.workers.notifications.inbound.dto.OtpRequestedEventDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventDispatcherTest {

    @Mock
    private NotificationEventHandler<OtpRequestedEventDto> otpHandler;

    private ObjectMapper objectMapper;
    private NotificationEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lenient()
            .when(otpHandler.supportedEventType())
            .thenReturn("OtpRequestedEvent");
        dispatcher = new NotificationEventDispatcher(
            List.of(otpHandler),
            objectMapper
        );
    }

    @Test
    @DisplayName(
        "Should successfully dispatch event when __TypeId__ matches supported event type"
    )
    void shouldDispatchMatchingEvent() throws JsonProcessingException {
        UUID userId = UUID.randomUUID();
        OtpRequestedEventDto dto = new OtpRequestedEventDto(
            userId,
            "student@hcmus.edu.vn",
            "123456",
            "ACTIVATION",
            "2026-10-07T03:00:00Z"
        );
        JsonNode root = objectMapper.valueToTree(dto);

        when(otpHandler.eventDtoClass()).thenReturn(OtpRequestedEventDto.class);

        dispatcher.dispatch(
            "com.unihub.workshop.shared.events.OtpRequestedEvent",
            root
        );

        verify(otpHandler).handle(any(OtpRequestedEventDto.class));
    }

    @Test
    @DisplayName(
        "Should throw IllegalArgumentException when __TypeId__ is missing or blank"
    )
    void shouldThrowWhenTypeIdBlank() {
        JsonNode root = objectMapper.createObjectNode();

        assertThrows(IllegalArgumentException.class, () ->
            dispatcher.dispatch(null, root)
        );
        assertThrows(IllegalArgumentException.class, () ->
            dispatcher.dispatch("  ", root)
        );
    }

    @Test
    @DisplayName(
        "Should throw IllegalArgumentException when no handler matches __TypeId__"
    )
    void shouldThrowWhenNoHandlerMatches() {
        JsonNode root = objectMapper.createObjectNode();

        assertThrows(IllegalArgumentException.class, () ->
            dispatcher.dispatch("UnknownEvent", root)
        );
    }
}
