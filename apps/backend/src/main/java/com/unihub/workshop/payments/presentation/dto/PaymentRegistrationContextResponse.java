package com.unihub.workshop.payments.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentRegistrationContextResponse(
    UUID id,
    UUID workshopId,
    String status
) {}
