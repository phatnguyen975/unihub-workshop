package com.unihub.workshop.registrations.domain.events;

import java.util.UUID;

public record RegistrationConfirmedEvent(UUID registrationId, UUID userId, UUID workshopId) {
}
