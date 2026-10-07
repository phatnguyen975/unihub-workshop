package com.unihub.workshop.workers.notifications.core.models;

import java.util.UUID;

public record WorkshopAttendeeRecipient(
    UUID id,
    String email,
    String fullName
) {}
