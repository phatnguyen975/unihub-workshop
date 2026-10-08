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
import com.unihub.workshop.workers.notifications.inbound.dto.StaffAssignedEventDto;
import com.unihub.workshop.workers.notifications.inbound.dto.UserProvisionedEventDto;
import com.unihub.workshop.workers.notifications.outbound.config.NotificationAmqpConfig;
import java.time.Instant;
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

    @Mock
    private NotificationEventHandler<UserProvisionedEventDto> userProvisionedHandler;

    @Mock
    private NotificationEventHandler<StaffAssignedEventDto> staffAssignedHandler;

    private ObjectMapper objectMapper;
    private NotificationEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        objectMapper = new NotificationAmqpConfig().objectMapper();
        lenient()
            .when(otpHandler.supportedEventType())
            .thenReturn("OtpRequestedEvent");
        lenient()
            .when(userProvisionedHandler.supportedEventType())
            .thenReturn("UserProvisionedEvent");
        lenient()
            .when(staffAssignedHandler.supportedEventType())
            .thenReturn("StaffAssignedToWorkshopEvent");
        dispatcher = new NotificationEventDispatcher(
            List.of(otpHandler, userProvisionedHandler, staffAssignedHandler),
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
        "Should successfully dispatch event containing Instant field like UserProvisionedEvent"
    )
    void shouldDispatchMatchingEventWithInstantOccurredAt()
        throws JsonProcessingException {
        UUID userId = UUID.randomUUID();
        UserProvisionedEventDto dto = new UserProvisionedEventDto(
            userId,
            UUID.randomUUID(),
            "test-org",
            "staff@example.com",
            "Staff Member",
            "STAFF",
            "TempPass123!",
            Instant.parse("2026-08-31T01:00:00Z")
        );
        JsonNode root = objectMapper.valueToTree(dto);

        when(userProvisionedHandler.eventDtoClass()).thenReturn(
            UserProvisionedEventDto.class
        );

        dispatcher.dispatch(
            "com.unihub.workshop.shared.events.UserProvisionedEvent",
            root
        );

        verify(userProvisionedHandler).handle(
            any(UserProvisionedEventDto.class)
        );
    }

    @Test
    @DisplayName(
        "Should successfully deserialize and dispatch event with space-separated datetime string"
    )
    void shouldDispatchEventWithSpaceSeparatedDateTimeString()
        throws JsonProcessingException {
        String json = """
        {
          "staffUserId": "22222222-2222-2222-2222-222222222222",
          "workshopId": "77777777-7777-7777-7777-777777777777",
          "staffEmail": "staff.checkin@unihub.edu.vn",
          "workshopTitle": "Microservices Architecture & Event-Driven",
          "startsAt": "2026-10-15 08:30:00",
          "endsAt": "2026-10-15 11:30:00",
          "location": "Hội trường A, Tầng 3",
          "occurredAt": "2026-10-08T02:25:00Z"
        }
        """;
        JsonNode root = objectMapper.readTree(json);

        when(staffAssignedHandler.eventDtoClass()).thenReturn(
            StaffAssignedEventDto.class
        );

        dispatcher.dispatch(
            "com.unihub.workshop.shared.events.StaffAssignedToWorkshopEvent",
            root
        );

        verify(staffAssignedHandler).handle(any(StaffAssignedEventDto.class));
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
