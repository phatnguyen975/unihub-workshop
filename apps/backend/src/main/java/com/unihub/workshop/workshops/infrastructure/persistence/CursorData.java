package com.unihub.workshop.workshops.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

public record CursorData(Instant completedAt, UUID id) {
}
