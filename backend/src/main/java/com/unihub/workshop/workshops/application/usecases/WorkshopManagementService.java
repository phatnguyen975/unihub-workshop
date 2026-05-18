package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.exception.AppException;
import com.unihub.workshop.shared.web.UseCaseResult;
import com.unihub.workshop.workshops.application.dto.BrochureUploadResponse;
import com.unihub.workshop.workshops.application.dto.ChangeStatusRequest;
import com.unihub.workshop.workshops.application.dto.CreateWorkshopRequest;
import com.unihub.workshop.workshops.application.dto.RescheduleWorkshopRequest;
import com.unihub.workshop.workshops.application.dto.SpeakerRequest;
import com.unihub.workshop.workshops.application.dto.UploadBrochureRequest;
import com.unihub.workshop.workshops.application.dto.UpdateWorkshopRequest;
import com.unihub.workshop.workshops.application.dto.WorkshopManagementFullResponse;
import com.unihub.workshop.workshops.application.dto.WorkshopManagementResponse;
import com.unihub.workshop.workshops.application.dto.WorkshopSpeakerResponse;
import com.unihub.workshop.workshops.domain.events.BrochureUploadedEvent;
import com.unihub.workshop.workshops.domain.events.WorkshopCanceledEvent;
import com.unihub.workshop.workshops.domain.events.WorkshopPublishedEvent;
import com.unihub.workshop.workshops.domain.events.WorkshopRescheduledEvent;
import com.unihub.workshop.workshops.domain.model.Brochure;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopSpeaker;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.domain.repository.BrochureRepository;
import com.unihub.workshop.workshops.domain.repository.WorkshopRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.workshops.infrastructure.cache.WorkshopCacheService;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkshopManagementService {

    private static final long MAX_BROCHURE_SIZE_BYTES = 5L * 1024 * 1024;

    @Value("${app.storage.brochure.base-url}")
    private String brochureBaseUrl;

    private final WorkshopRepository workshopRepository;
    private final BrochureRepository brochureRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final WorkshopCacheService cacheService;
    private final ObjectMapper objectMapper;

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
        sanitizePriceCurrency(workshop);
        workshop.syncSpeakers(mapSpeakers(request.speakers()));

        Workshop saved = workshopRepository.save(workshop);
        cacheService.evictPublicListCachePattern();
        return new UseCaseResult<>(HttpStatus.CREATED, "Workshop created", mapToResponse(saved));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public UseCaseResult<WorkshopManagementFullResponse> getWorkshopForManagement(String organizerId, UUID workshopId) {
        UUID organizer = parseOrganizer(organizerId);
        String cacheKey = workshopId.toString();
        Object cachedResult = cacheService.getManagementDetailCache(cacheKey);
        if (cachedResult != null) {
            try {
                WorkshopManagementFullResponse response = objectMapper.convertValue(cachedResult,
                        WorkshopManagementFullResponse.class);
                return new UseCaseResult<>(HttpStatus.OK, "Workshop details", response);
            } catch (Exception e) {
                // Cache miss on conversion; continue to fetch from DB
            }
        }

        Workshop workshop = loadWorkshop(workshopId, organizer);
        WorkshopManagementFullResponse response = mapToFullResponse(workshop);
        cacheService.setManagementDetailCache(cacheKey, response);
        return new UseCaseResult<>(HttpStatus.OK, "Workshop details", response);
    }

    public UseCaseResult<WorkshopManagementFullResponse> updateWorkshop(
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
        sanitizePriceCurrency(workshop);
        if (request.startTime() != null)
            workshop.setStartTime(request.startTime());
        if (request.endTime() != null)
            workshop.setEndTime(request.endTime());
        workshop.syncSpeakers(mapSpeakers(request.speakers()));

        Workshop saved = workshopRepository.save(workshop);
        cacheService.evictAllDetailCaches(workshopId.toString());
        cacheService.evictPublicListCachePattern();
        return new UseCaseResult<>(HttpStatus.OK, "Workshop updated", mapToFullResponse(saved));
    }

    public UseCaseResult<WorkshopManagementFullResponse> rescheduleWorkshop(
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

        cacheService.evictAllDetailCaches(workshopId.toString());
        cacheService.evictPublicListCachePattern();
        return new UseCaseResult<>(HttpStatus.OK, "Workshop rescheduled", mapToFullResponse(saved));
    }

    public UseCaseResult<WorkshopManagementFullResponse> changeStatus(
            String organizerId, UUID workshopId, ChangeStatusRequest request) {
        UUID organizer = parseOrganizer(organizerId);
        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Workshop not found"));
        requireOwnership(workshop, organizer);

        checkVersion(workshop, request.version());
        validateStatusTransition(workshop, request.status());

        WorkshopStatus previous = workshop.getStatus();
        workshop.setStatus(request.status());

        if (request.status() == WorkshopStatus.PUBLISHED) {
            ensurePublishable(workshop);
        }

        if (request.status() == WorkshopStatus.CANCELED) {
            workshop.setCapacityRemaining(workshop.getCapacityTotal());
        }

        Workshop saved = workshopRepository.saveAndFlush(workshop);

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
                    "Canceled via status update",
                    OffsetDateTime.now()));
        }

        cacheService.evictPublicDetailCache(workshopId.toString());
        cacheService.evictManagementDetailCache(workshopId.toString());
        cacheService.evictPublicListCachePattern();
        return new UseCaseResult<>(HttpStatus.OK, "Workshop status updated", mapToFullResponse(saved));
    }

    public UseCaseResult<WorkshopManagementFullResponse> deleteWorkshop(
            String organizerId, UUID workshopId, int version) {
        UUID organizer = parseOrganizer(organizerId);
        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Workshop not found"));
        requireOwnership(workshop, organizer);
        checkVersion(workshop, version);
        // capture full state before deletion
        var full = mapToFullResponse(workshop);
        workshopRepository.delete(workshop);
        cacheService.evictAllDetailCaches(workshopId.toString());
        cacheService.evictPublicListCachePattern();
        return new UseCaseResult<>(HttpStatus.OK, "Deleted", full);
    }

    public UseCaseResult<BrochureUploadResponse> uploadBrochure(
            String organizerId, UUID workshopId, UploadBrochureRequest request) {
        UUID organizer = parseOrganizer(organizerId);
        Workshop workshop = loadWorkshop(workshopId, organizer);
        ensureEditable(workshop);

        MultipartFile file = request == null ? null : request.file();
        validateBrochure(file);
        if (file == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Brochure file is required");
        }

        OffsetDateTime now = OffsetDateTime.now();
        String originalFileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim()
                : "brochure.pdf";
        String sanitizedFileName = originalFileName.replaceAll("[^A-Za-z0-9._-]", "_");
        String storageKey = "workshops/" + workshop.getId() + "/brochures/" + now.toInstant().toEpochMilli()
                + "-" + sanitizedFileName;
        String normalizedBaseUrl = brochureBaseUrl.endsWith("/") ? brochureBaseUrl : brochureBaseUrl + "/";
        String fileUrl = normalizedBaseUrl + workshop.getId() + "/" + sanitizedFileName;

        Brochure brochure = new Brochure(
                storageKey,
                originalFileName,
                file.getContentType(),
                file.getSize(),
                fileUrl,
                now);
        workshop.addBrochure(brochure);
        forceVersionIncrement(workshop);

        workshopRepository.saveAndFlush(workshop);
        Brochure savedBrochure = brochureRepository.saveAndFlush(brochure);

        eventPublisher.publishEvent(new BrochureUploadedEvent(
                workshop.getId(),
                workshop.getOrganizerId(),
                savedBrochure.getFileUrl(),
                savedBrochure.getFileName(),
                savedBrochure.getSizeBytes() == null ? 0L : savedBrochure.getSizeBytes(),
                savedBrochure.getUploadedAt()));

        cacheService.evictPublicDetailCache(workshopId.toString());
        cacheService.evictManagementDetailCache(workshopId.toString());
        cacheService.evictPublicListCachePattern();
        return new UseCaseResult<>(HttpStatus.ACCEPTED, "Brochure upload accepted", new BrochureUploadResponse(
                savedBrochure.getId(),
                savedBrochure.getFileUrl(),
                savedBrochure.getStatus(),
                "PROCESSING"));
    }

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

    private Workshop loadWorkshop(UUID workshopId, UUID organizer) {
        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Workshop not found"));
        requireOwnership(workshop, organizer);
        return workshop;
    }

    private void checkVersion(Workshop workshop, Integer version) {
        if (version == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Version is required");
        }
        if (workshop.getVersion() != version) {
            throw new AppException(HttpStatus.CONFLICT, "Version mismatch");
        }
    }

    private void validateStatusTransition(Workshop workshop, WorkshopStatus targetStatus) {
        if (targetStatus == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Status is required");
        }

        WorkshopStatus currentStatus = workshop.getStatus();
        boolean validTransition = switch (currentStatus) {
            case DRAFT -> targetStatus == WorkshopStatus.PUBLISHED;
            case PUBLISHED -> targetStatus == WorkshopStatus.CANCELED;
            default -> false;
        };

        if (!validTransition) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid workshop status transition");
        }
    }

    private void ensurePublishable(Workshop workshop) {
        if (!StringUtils.hasText(workshop.getLocation())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Workshop location is required to publish");
        }
        if (!StringUtils.hasText(workshop.getVenue())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Workshop venue is required to publish");
        }
        if (!StringUtils.hasText(workshop.getRoom())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Workshop room is required to publish");
        }
        if (workshop.getCapacityTotal() <= 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Workshop capacity must be greater than zero");
        }
        if (workshop.getSpeakers().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "At least one speaker is required to publish");
        }
        if (workshop.getStartTime() == null || !workshop.getStartTime().isAfter(OffsetDateTime.now())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Workshop start time must be in the future to publish");
        }
    }

    private void ensureEditable(Workshop workshop) {
        if (workshop.getStatus() == WorkshopStatus.CANCELED || workshop.getStatus() == WorkshopStatus.COMPLETED) {
            throw new AppException(HttpStatus.CONFLICT, "Workshop is not editable in current status");
        }
    }

    private void validateBrochure(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Brochure file is required");
        }
        if (file.getSize() > MAX_BROCHURE_SIZE_BYTES) {
            throw new AppException(HttpStatus.CONTENT_TOO_LARGE, "Brochure must not exceed 5MB");
        }
        String contentType = file.getContentType();
        String originalName = file.getOriginalFilename();
        boolean looksLikePdf = StringUtils.hasText(originalName) && originalName.toLowerCase().endsWith(".pdf");
        if (!"application/pdf".equalsIgnoreCase(contentType) && !looksLikePdf) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only PDF brochures are accepted");
        }
    }

    private void forceVersionIncrement(Workshop workshop) {
        entityManager.lock(workshop, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    private void sanitizePriceCurrency(Workshop workshop) {
        BigDecimal priceAmount = workshop.getPriceAmount();
        if (priceAmount != null && priceAmount.compareTo(BigDecimal.ZERO) == 0) {
            workshop.setCurrency(null);
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

    private List<WorkshopSpeaker> mapSpeakers(List<SpeakerRequest> speakerRequests) {
        if (speakerRequests == null) {
            return Collections.emptyList();
        }

        return speakerRequests.stream()
                .map(s -> new WorkshopSpeaker(
                        s.name().trim(),
                        s.bio(),
                        s.sortOrder() == null ? 0 : s.sortOrder()))
                .sorted(Comparator.comparingInt(WorkshopSpeaker::getSortOrder))
                .toList();
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
                .map(s -> new WorkshopSpeakerResponse(
                        s.getId(), s.getSpeakerName(), s.getSpeakerBio(), s.getSortOrder()))
                .toList();
        var assigned = w.getAssignments().stream()
                .map(a -> new com.unihub.workshop.workshops.application.dto.AssignedStaffResponse(
                        a.getStaffId(), a.getAssignedAt(), a.getCreatedAt(), a.getUpdatedAt()))
                .toList();
        String brochureUrl = brochureRepository.findByWorkshopIdOrderByUploadedAtDesc(w.getId()).stream()
                .findFirst()
                .map(Brochure::getFileUrl)
                .orElse(null);
        return new WorkshopManagementFullResponse(
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
                assigned,
                brochureUrl);
    }
}
