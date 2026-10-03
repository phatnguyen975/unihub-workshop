package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.shared.exception.ValidationException;

public record GetPublicWorkshopQuery(String identifier) {
    public GetPublicWorkshopQuery {
        if (identifier == null || identifier.isBlank()) {
            throw new ValidationException("identifier", "must not be blank");
        }
        identifier = identifier.trim();
    }

    public static GetPublicWorkshopQuery of(String identifier) {
        return new GetPublicWorkshopQuery(identifier);
    }
}
