package com.unihub.workshop.workshops.application.ports.out;

import com.unihub.workshop.workshops.domain.model.WorkshopBrochure;
import java.util.Optional;
import java.util.UUID;

public interface WorkshopBrochureRepository {
    WorkshopBrochure save(WorkshopBrochure brochure);

    Optional<WorkshopBrochure> findActiveByWorkshopId(UUID workshopId);

    void deactivateAllByWorkshopId(UUID workshopId);
}
