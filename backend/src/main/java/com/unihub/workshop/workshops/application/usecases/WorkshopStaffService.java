package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.iam.application.dto.OrganizationStaffPageResponse;
import com.unihub.workshop.iam.contracts.IamWorkshopFacade;
import com.unihub.workshop.iam.application.usecases.UserUseCase;
import com.unihub.workshop.shared.exception.AppException;
import com.unihub.workshop.shared.web.PaginationMetadata;
import com.unihub.workshop.shared.web.UseCaseResult;
import com.unihub.workshop.workshops.application.dto.AssignStaffRequest;
import com.unihub.workshop.workshops.application.dto.WorkshopManagementFullResponse;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopAssignment;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.domain.repository.BrochureRepository;
import com.unihub.workshop.workshops.domain.repository.WorkshopAssignmentRepository;
import com.unihub.workshop.workshops.domain.repository.WorkshopRepository;
import com.unihub.workshop.workshops.infrastructure.cache.CacheKeyGenerator;
import com.unihub.workshop.workshops.infrastructure.cache.WorkshopCacheService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import jakarta.persistence.EntityManager;

@Service
@RequiredArgsConstructor
public class WorkshopStaffService {

    private final WorkshopRepository workshopRepository;
    private final WorkshopAssignmentRepository assignmentRepository;
    private final IamWorkshopFacade iamFacade;
    private final UserUseCase userUseCase;
    private final WorkshopCacheService cacheService;
    private final CacheKeyGenerator keyGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final BrochureRepository brochureRepository;

    @Transactional(readOnly = true)
    public UseCaseResult<OrganizationStaffPageResponse> getAvailableStaff(String userId, UUID workshopId,
            PageRequest pageable) {
        UUID organizer = parseOrganizer(userId);
        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Workshop not found", "not_found"));
        if (!organizer.equals(workshop.getOrganizerId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not the owner");
        }

        java.util.UUID orgId = iamFacade.getOrganizationIdForUser(organizer);
        if (orgId == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Organizer has no organization context",
                    "invalid_organization");
        }

        String cacheKey = keyGenerator.generateStaffAvailableKey(workshopId, pageable.getPageNumber(),
                pageable.getPageSize());
        Object cached = cacheService.getStaffAvailableCache(cacheKey);
        if (cached != null) {
            OrganizationStaffPageResponse resp = (OrganizationStaffPageResponse) cached;
            return new UseCaseResult<>(HttpStatus.OK, "Available staff retrieved", resp);
        }

        UseCaseResult<OrganizationStaffPageResponse> iamResult = userUseCase.listOrganizationStaff(userId,
                orgId.toString(), pageable);
        OrganizationStaffPageResponse iamPage = iamResult.data();

        List<com.unihub.workshop.iam.application.dto.OrganizationStaffResponse> filtered = iamPage.items().stream()
                .filter(s -> {
                    try {
                        UUID staffUuid = UUID.fromString(s.id());
                        List<WorkshopAssignment> overlaps = assignmentRepository.findOverlappingAssignments(staffUuid,
                                workshop.getStartTime(), workshop.getEndTime());
                        return overlaps.isEmpty();
                    } catch (Exception e) {
                        return false;
                    }
                }).collect(Collectors.toList());

        OrganizationStaffPageResponse response = new OrganizationStaffPageResponse(
                filtered,
                new PaginationMetadata(iamPage.pagination().page(), iamPage.pagination().size(), filtered.size(), 1,
                        false, false));

        cacheService.setStaffAvailableCache(cacheKey, response);
        return new UseCaseResult<>(HttpStatus.OK, "Available staff retrieved", response);
    }

    @Transactional
    public UseCaseResult<WorkshopManagementFullResponse> assignStaff(String userId, UUID workshopId,
            AssignStaffRequest request) {
        UUID organizer = parseOrganizer(userId);
        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Workshop not found", "not_found"));
        if (!organizer.equals(workshop.getOrganizerId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not the owner");
        }
        if (workshop.getVersion() != request.version()) {
            throw new AppException(HttpStatus.CONFLICT, "Version mismatch");
        }
        if (workshop.getStatus() == WorkshopStatus.CANCELED || workshop.getStatus() == WorkshopStatus.COMPLETED) {
            throw new AppException(HttpStatus.CONFLICT, "Workshop is not editable in current status");
        }

        UUID staffId = request.staffId();
        java.util.UUID orgId = iamFacade.getOrganizationIdForUser(organizer);
        if (orgId == null || !iamFacade.verifyStaffExistsInOrganization(staffId, orgId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Staff not found in organizer organization", "not_found");
        }

        List<WorkshopAssignment> overlaps = assignmentRepository.findOverlappingAssignments(staffId,
                workshop.getStartTime(), workshop.getEndTime());
        if (!overlaps.isEmpty()) {
            throw new AppException(HttpStatus.CONFLICT, "Staff has overlapping assignment", "conflict");
        }

        WorkshopAssignment assignment = new WorkshopAssignment(staffId, OffsetDateTime.now());
        workshop.addAssignment(assignment);
        entityManager.lock(workshop, jakarta.persistence.LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        workshopRepository.saveAndFlush(workshop);

        eventPublisher.publishEvent(new com.unihub.workshop.workshops.domain.events.StaffAssignedToWorkshopEvent(
                workshop.getId(), staffId, workshop.getOrganizerId(), OffsetDateTime.now()));

        cacheService.evictStaffAvailablePattern(workshopId.toString());
        cacheService.evictManagementDetailCache(workshopId.toString());

        Workshop refreshed = workshopRepository.findById(workshopId).orElse(workshop);

        var assigned = refreshed.getAssignments().stream()
                .map(a -> new com.unihub.workshop.workshops.application.dto.AssignedStaffResponse(
                        a.getStaffId(), a.getAssignedAt(), a.getCreatedAt(), a.getUpdatedAt()))
                .toList();
        var speakers = refreshed.getSpeakers().stream()
                .map(s -> new com.unihub.workshop.workshops.application.dto.WorkshopSpeakerResponse(
                        s.getId(), s.getSpeakerName(), s.getSpeakerBio(), s.getSortOrder()))
                .toList();
        String brochureUrl = brochureRepository.findByWorkshopIdOrderByUploadedAtDesc(refreshed.getId()).stream()
                .findFirst().map(b -> b.getFileUrl()).orElse(null);

        WorkshopManagementFullResponse resp = new WorkshopManagementFullResponse(
                refreshed.getId(),
                refreshed.getOrganizerId(),
                refreshed.getTitle(),
                refreshed.getDescription(),
                refreshed.getLocation(),
                refreshed.getVenue(),
                refreshed.getRoom(),
                refreshed.getRoomLayoutKey(),
                refreshed.getStatus(),
                refreshed.getVersion(),
                refreshed.getCapacityTotal(),
                refreshed.getCapacityRemaining(),
                refreshed.getPriceAmount(),
                refreshed.getCurrency(),
                refreshed.getStartTime(),
                refreshed.getEndTime(),
                refreshed.getCreatedAt(),
                refreshed.getUpdatedAt(),
                speakers,
                assigned,
                brochureUrl);

        return new UseCaseResult<>(HttpStatus.CREATED, "Staff assigned to workshop", resp);
    }

    @Transactional
    public UseCaseResult<WorkshopManagementFullResponse> removeStaff(String userId, UUID workshopId, UUID staffId,
            int version) {
        UUID organizer = parseOrganizer(userId);
        Workshop workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Workshop not found", "not_found"));
        if (!organizer.equals(workshop.getOrganizerId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not the owner");
        }
        if (workshop.getVersion() != version) {
            throw new AppException(HttpStatus.CONFLICT, "Version mismatch");
        }

        var assignment = assignmentRepository.findByStaffIdAndWorkshopId(staffId, workshopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Assignment not found"));

        workshop.removeAssignment(assignment);
        assignmentRepository.delete(assignment);
        entityManager.lock(workshop, jakarta.persistence.LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        workshopRepository.saveAndFlush(workshop);

        eventPublisher.publishEvent(new com.unihub.workshop.workshops.domain.events.StaffRemovedFromWorkshopEvent(
                workshopId, staffId, workshop.getOrganizerId(), OffsetDateTime.now()));

        cacheService.evictStaffAvailablePattern(workshopId.toString());
        cacheService.evictManagementDetailCache(workshopId.toString());

        Workshop refreshed = workshopRepository.findById(workshopId).orElse(workshop);
        var assigned = refreshed.getAssignments().stream()
                .map(a -> new com.unihub.workshop.workshops.application.dto.AssignedStaffResponse(
                        a.getStaffId(), a.getAssignedAt(), a.getCreatedAt(), a.getUpdatedAt()))
                .toList();
        var speakers = refreshed.getSpeakers().stream()
                .map(s -> new com.unihub.workshop.workshops.application.dto.WorkshopSpeakerResponse(
                        s.getId(), s.getSpeakerName(), s.getSpeakerBio(), s.getSortOrder()))
                .toList();
        String brochureUrl = brochureRepository.findByWorkshopIdOrderByUploadedAtDesc(refreshed.getId()).stream()
                .findFirst().map(b -> b.getFileUrl()).orElse(null);

        WorkshopManagementFullResponse resp = new WorkshopManagementFullResponse(
                refreshed.getId(),
                refreshed.getOrganizerId(),
                refreshed.getTitle(),
                refreshed.getDescription(),
                refreshed.getLocation(),
                refreshed.getVenue(),
                refreshed.getRoom(),
                refreshed.getRoomLayoutKey(),
                refreshed.getStatus(),
                refreshed.getVersion(),
                refreshed.getCapacityTotal(),
                refreshed.getCapacityRemaining(),
                refreshed.getPriceAmount(),
                refreshed.getCurrency(),
                refreshed.getStartTime(),
                refreshed.getEndTime(),
                refreshed.getCreatedAt(),
                refreshed.getUpdatedAt(),
                speakers,
                assigned,
                brochureUrl);

        return new UseCaseResult<>(HttpStatus.OK, "Staff unassigned", resp);
    }

    private UUID parseOrganizer(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (Exception e) {
            throw new AppException(HttpStatus.FORBIDDEN, "Invalid organizer id");
        }
    }
}
