package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.exception.AppException;
import com.unihub.workshop.shared.web.UseCaseResult;
import com.unihub.workshop.workshops.application.dto.ChangeStatusRequest;
import com.unihub.workshop.workshops.application.dto.CreateWorkshopRequest;
import com.unihub.workshop.workshops.application.dto.RescheduleWorkshopRequest;
import com.unihub.workshop.workshops.application.dto.UpdateWorkshopRequest;
import com.unihub.workshop.workshops.application.dto.WorkshopManagementResponse;
import com.unihub.workshop.workshops.domain.events.WorkshopCanceledEvent;
import com.unihub.workshop.workshops.domain.events.WorkshopPublishedEvent;
import com.unihub.workshop.workshops.domain.events.WorkshopRescheduledEvent;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.domain.repository.WorkshopRepository;

import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkshopManagementService {

    private final WorkshopRepository workshopRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UseCaseResult<WorkshopManagementResponse> createWorkshop(String organizerId, CreateWorkshopRequest request) {
        UUID organizer = parseOrganizer(organizerId);
        Workshop workshop = new Workshop(organizer, request.title(), request.location(), request.startTime(),
                request.endTime());
        workshop.setTitle(request.title());
        workshop.setDescription(request.description());
        workshop.setVenue(request.venue());
        workshop.setRoom(request.room());
        workshop.setRoomLayoutKey(request.roomLayoutKey());
        workshop.setCapacityTotal(request.capacityTotal());
        workshop.setCapacityRemaining(request.capacityTotal());
        workshop.setPriceAmount(request.priceAmount());
        workshop.setCurrency(request.currency());

        Workshop saved = workshopRepository.save(workshop);
        return new UseCaseResult<>(HttpStatus.CREATED, "Workshop created", mapToResponse(saved));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public UseCaseResult<com.unihub.workshop.workshops.application.dto.WorkshopManagementFullResponse> getWorkshopForManagement(
            String organizerId, UUID workshopId) {
        UUID organizer = parseOrganizer(organizerId);
        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Workshop not found"));
        requireOwnership(workshop, organizer);
        return new UseCaseResult<>(HttpStatus.OK, "Workshop details", mapToFullResponse(workshop));
    }

    public UseCaseResult<com.unihub.workshop.workshops.application.dto.WorkshopManagementFullResponse> updateWorkshop(
            String organizerId, UUID workshopId, UpdateWorkshopRequest request) {
        UUID organizer = parseOrganizer(organizerId);
        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Workshop not found"));
        requireOwnership(workshop, organizer);
        checkVersion(workshop, request.version());
        if (workshop.getStatus() == WorkshopStatus.PUBLISHED) {
            // locked fields cannot be changed when published
            if (lockedFieldsChanged(workshop, request)) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Cannot modify locked fields when workshop is published");
            }
        }

        workshop.setTitle(request.title());
        workshop.setDescription(request.description());
        workshop.setLocation(request.location());
        workshop.setVenue(request.venue());
        workshop.setRoom(request.room());
        workshop.setRoomLayoutKey(request.roomLayoutKey());
        workshop.setCapacityTotal(request.capacityTotal());
        if (request.priceAmount() != null)
            workshop.setPriceAmount(request.priceAmount());
        if (request.currency() != null)
            workshop.setCurrency(request.currency());
        if (request.startTime() != null)
            workshop.setStartTime(request.startTime());
        if (request.endTime() != null)
            workshop.setEndTime(request.endTime());

        Workshop saved = workshopRepository.save(workshop);
        return new UseCaseResult<>(HttpStatus.OK, "Workshop updated", mapToFullResponse(saved));
    }

    public UseCaseResult<com.unihub.workshop.workshops.application.dto.WorkshopManagementFullResponse> rescheduleWorkshop(
            String organizerId, UUID workshopId, RescheduleWorkshopRequest request) {
        UUID organizer = parseOrganizer(organizerId);
        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Workshop not found"));
        requireOwnership(workshop, organizer);
        if (workshop.getStatus() == WorkshopStatus.PUBLISHED) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Cannot reschedule a published workshop");
        }
        OffsetDateTime previousStart = workshop.getStartTime();
        OffsetDateTime previousEnd = workshop.getEndTime();
        String previousLocation = workshop.getLocation();

        workshop.setStartTime(request.startTime());
        workshop.setEndTime(request.endTime());
        if (request.location() != null)
            workshop.setLocation(request.location());

        Workshop saved = workshopRepository.save(workshop);

        // publish rescheduled event
        eventPublisher.publishEvent(new WorkshopRescheduledEvent(
                saved.getId(),
                saved.getOrganizerId(),
                saved.getTitle(),
                previousStart,
                saved.getStartTime(),
                previousEnd,
                saved.getEndTime(),
                previousLocation,
                saved.getLocation(),
                OffsetDateTime.now()));

        return new UseCaseResult<>(HttpStatus.OK, "Workshop rescheduled", mapToFullResponse(saved));
    }

    public UseCaseResult<com.unihub.workshop.workshops.application.dto.WorkshopManagementFullResponse> changeStatus(
            String organizerId, UUID workshopId, ChangeStatusRequest request) {
        UUID organizer = parseOrganizer(organizerId);
        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Workshop not found"));
        requireOwnership(workshop, organizer);

        WorkshopStatus previous = workshop.getStatus();
        workshop.setStatus(request.status());
        Workshop saved = workshopRepository.save(workshop);

        if (previous != WorkshopStatus.PUBLISHED && saved.getStatus() == WorkshopStatus.PUBLISHED) {
            eventPublisher.publishEvent(new WorkshopPublishedEvent(
                    saved.getId(),
                    saved.getOrganizerId(),
                    saved.getTitle(),
                    saved.getStartTime(),
                    saved.getEndTime(),
                    saved.getLocation(),
                    saved.getCapacityTotal(),
                    OffsetDateTime.now()));
        }

        if (saved.getStatus() == WorkshopStatus.CANCELED) {
            eventPublisher.publishEvent(new WorkshopCanceledEvent(
                    saved.getId(),
                    saved.getOrganizerId(),
                    saved.getTitle(),
                    request.reason(),
                    OffsetDateTime.now()));
        }

        return new UseCaseResult<>(HttpStatus.OK, "Status changed", mapToFullResponse(saved));
    }

    public UseCaseResult<com.unihub.workshop.workshops.application.dto.WorkshopManagementFullResponse> deleteWorkshop(
            String organizerId, UUID workshopId, int version) {
        UUID organizer = parseOrganizer(organizerId);
        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Workshop not found"));
        requireOwnership(workshop, organizer);
        checkVersion(workshop, version);
        // capture full state before deletion
        var full = mapToFullResponse(workshop);
        workshopRepository.delete(workshop);
        return new UseCaseResult<>(HttpStatus.OK, "Deleted", full);
    }

    // --- helpers ---
    private UUID parseOrganizer(String organizerId) {
        try {
            return UUID.fromString(organizerId);
        } catch (Exception e) {
            throw new AppException(HttpStatus.FORBIDDEN, "Invalid organizer id");
        }
    }

    private void requireOwnership(Workshop workshop, UUID organizer) {
        if (!Objects.equals(workshop.getOrganizerId(), organizer)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not the owner");
        }
    }

    private void checkVersion(Workshop workshop, int version) {
        if (workshop.getVersion() != version) {
            throw new AppException(HttpStatus.CONFLICT, "Version mismatch");
        }
    }

    private boolean lockedFieldsChanged(Workshop workshop, UpdateWorkshopRequest request) {
        if (request.priceAmount() != null && !Objects.equals(workshop.getPriceAmount(), request.priceAmount()))
            return true;
        if (request.currency() != null && !Objects.equals(workshop.getCurrency(), request.currency()))
            return true;
        if (request.startTime() != null && !Objects.equals(workshop.getStartTime(), request.startTime()))
            return true;
        if (request.endTime() != null && !Objects.equals(workshop.getEndTime(), request.endTime()))
            return true;
        if (request.location() != null && !Objects.equals(workshop.getLocation(), request.location()))
            return true;
        if (request.venue() != null && !Objects.equals(workshop.getVenue(), request.venue()))
            return true;
        if (request.room() != null && !Objects.equals(workshop.getRoom(), request.room()))
            return true;
        if (request.roomLayoutKey() != null && !Objects.equals(workshop.getRoomLayoutKey(), request.roomLayoutKey()))
            return true;
        return false;
    }

    private WorkshopManagementResponse mapToResponse(Workshop w) {
        return new WorkshopManagementResponse(
                w.getId(),
                w.getOrganizerId(),
                w.getTitle(),
                w.getDescription(),
                w.getLocation(),
                w.getVenue(),
                w.getRoom(),
                w.getRoomLayoutKey(),
                w.getStatus(),
                w.getVersion(),
                w.getCapacityTotal(),
                w.getCapacityRemaining(),
                w.getPriceAmount(),
                w.getCurrency(),
                w.getStartTime(),
                w.getEndTime(),
                w.getCreatedAt(),
                w.getUpdatedAt());
    }

    private com.unihub.workshop.workshops.application.dto.WorkshopManagementFullResponse mapToFullResponse(Workshop w) {
        var speakers = w.getSpeakers().stream()
                .map(s -> new com.unihub.workshop.workshops.application.dto.WorkshopSpeakerResponse(
                        s.getId(), s.getSpeakerName(), s.getSpeakerBio(), s.getSortOrder()))
                .toList();
        var assigned = w.getAssignments().stream()
                .map(a -> new com.unihub.workshop.workshops.application.dto.AssignedStaffResponse(
                        a.getStaffId(), a.getAssignedAt(), a.getCreatedAt(), a.getUpdatedAt()))
                .toList();
        return new com.unihub.workshop.workshops.application.dto.WorkshopManagementFullResponse(
                w.getId(),
                w.getOrganizerId(),
                w.getTitle(),
                w.getDescription(),
                w.getLocation(),
                w.getVenue(),
                w.getRoom(),
                w.getRoomLayoutKey(),
                w.getStatus(),
                w.getVersion(),
                w.getCapacityTotal(),
                w.getCapacityRemaining(),
                w.getPriceAmount(),
                w.getCurrency(),
                w.getStartTime(),
                w.getEndTime(),
                w.getCreatedAt(),
                w.getUpdatedAt(),
                speakers,
                assigned);
    }
}
