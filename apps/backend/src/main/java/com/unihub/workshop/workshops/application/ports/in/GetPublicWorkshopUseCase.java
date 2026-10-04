package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.workshops.domain.model.PublicWorkshopDetail;

public interface GetPublicWorkshopUseCase {
    PublicWorkshopDetail execute(GetPublicWorkshopQuery query);
}
