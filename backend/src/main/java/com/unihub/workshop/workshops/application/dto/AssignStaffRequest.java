package com.unihub.workshop.workshops.application.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignStaffRequest(
        @NotNull UUID staffId,
        @NotNull Integer version) {
}
