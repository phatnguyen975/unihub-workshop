package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.workshops.application.ports.in.DeleteDraftUseCase;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteDraftUseCaseImpl implements DeleteDraftUseCase {

    private final WorkshopRepository workshopRepository;
    private final MediaStoragePort mediaStoragePort;

    @Override
    @Transactional
    public void execute(UUID id, UUID callerOrganizationId) {
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
                "You do not have permission to delete this workshop."
            );
        }

        if (workshop.status() != WorkshopStatus.DRAFT) {
            throw new ConflictException(
                "Workshop cannot be deleted because it is not in DRAFT status."
            );
        }

        List<String> mediaUrlsToDelete = new ArrayList<>();
        if (
            workshop.coverImageUrl() != null &&
            !workshop.coverImageUrl().isBlank()
        ) {
            mediaUrlsToDelete.add(workshop.coverImageUrl());
        }
        if (workshop.speakers() != null) {
            for (Speaker speaker : workshop.speakers()) {
                if (
                    speaker.avatarUrl() != null &&
                    !speaker.avatarUrl().isBlank()
                ) {
                    mediaUrlsToDelete.add(speaker.avatarUrl());
                }
            }
        }

        workshopRepository.deleteById(id);

        if (!mediaUrlsToDelete.isEmpty()) {
            mediaStoragePort.deleteAllByUrls(mediaUrlsToDelete);
        }
    }
}
