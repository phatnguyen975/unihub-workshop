package com.unihub.workshop.workers.notifications.outbound.persistence;

import java.util.UUID;

public record AttendeeProjection(UUID id, String email, String fullName) {}
