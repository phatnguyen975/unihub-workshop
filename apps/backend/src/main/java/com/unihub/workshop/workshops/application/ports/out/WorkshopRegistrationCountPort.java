package com.unihub.workshop.workshops.application.ports.out;

import java.util.UUID;

public interface WorkshopRegistrationCountPort {
    long countActiveRegistrations(UUID workshopId);
}
