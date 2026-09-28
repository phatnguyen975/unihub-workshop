package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.workshops.application.ports.in.GetWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Workshop;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetWorkshopUseCaseImpl implements GetWorkshopUseCase {

    private final WorkshopRepository workshopRepository;

    @Override
    @Transactional
    public Workshop execute(UUID id, UUID callerOrganizationId) {
        if (id == null) {
            throw new BadRequestException("Workshop ID is required");
        }
        if (callerOrganizationId == null) {
            throw new BadRequestException("Organization ID is required");
        }

        Workshop workshop = workshopRepository
            .findById(id)
            .orElseThrow(() ->
                new NotFoundException("Workshop not found with id: " + id)
            );

        if (!callerOrganizationId.equals(workshop.organizationId())) {
            throw new ForbiddenException(
                "You do not have permission to access this workshop."
            );
        }

        return workshop;
    }
}
