package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.workshops.domain.model.Workshop;

public interface RescheduleWorkshopUseCase {
    Workshop execute(RescheduleWorkshopCommand command);
}
