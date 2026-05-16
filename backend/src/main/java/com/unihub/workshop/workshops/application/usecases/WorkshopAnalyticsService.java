package com.unihub.workshop.workshops.application.usecases;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.registrations.contracts.AttendeePage;
import com.unihub.workshop.registrations.contracts.RegistrationWorkshopFacade;
import com.unihub.workshop.registrations.contracts.AnalyticsData;
import com.unihub.workshop.shared.exception.AppException;
import com.unihub.workshop.shared.web.PaginationMetadata;
import com.unihub.workshop.shared.web.UseCaseResult;
import com.unihub.workshop.workshops.application.dto.AttendeeItemResponse;
import com.unihub.workshop.workshops.application.dto.AttendeePageResponse;
import com.unihub.workshop.workshops.application.dto.WorkshopAnalyticsResponse;
import com.unihub.workshop.workshops.application.dto.WorkshopManagementPageResponse;
import com.unihub.workshop.workshops.application.dto.WorkshopManagementResponse;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.domain.repository.WorkshopRepository;
import com.unihub.workshop.workshops.infrastructure.cache.CacheKeyGenerator;
import com.unihub.workshop.workshops.infrastructure.cache.WorkshopCacheService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkshopAnalyticsService {

    private final WorkshopRepository workshopRepository;
    private final RegistrationWorkshopFacade registrationFacade;
    private final WorkshopCacheService cacheService;
    private final CacheKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    public UseCaseResult<WorkshopManagementPageResponse> listOrganizerWorkshops(String userId, int page, int size,
            String status) {
        UUID organizer = parseOrganizer(userId);
        String cacheKey = keyGenerator.generateManagementListKey(organizer.toString(), page, size, status);
        Object cached = cacheService.getManagementListCache(cacheKey);
        if (cached != null) {
            try {
                WorkshopManagementPageResponse resp = objectMapper.convertValue(cached,
                        WorkshopManagementPageResponse.class);
                return new UseCaseResult<>(HttpStatus.OK, "Workshops list retrieved", resp);
            } catch (Exception ignored) {
            }
        }

        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var pageRes = (status == null || status.isBlank()) ? workshopRepository.findByOrganizerId(organizer, pr)
                : workshopRepository.findByOrganizerIdAndStatus(organizer, WorkshopStatus.valueOf(status), pr);

        List<WorkshopManagementResponse> items = pageRes.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        PaginationMetadata pagination = new PaginationMetadata(
                pageRes.getNumber(), pageRes.getSize(), pageRes.getTotalElements(), pageRes.getTotalPages(),
                pageRes.hasNext(), pageRes.hasPrevious());

        WorkshopManagementPageResponse response = new WorkshopManagementPageResponse(items, pagination);
        cacheService.setManagementListCache(cacheKey, response);
        return new UseCaseResult<>(HttpStatus.OK, "Workshops list retrieved", response);
    }

    public UseCaseResult<WorkshopAnalyticsResponse> getWorkshopAnalytics(String userId, UUID workshopId) {
        UUID organizer = parseOrganizer(userId);
        Workshop w = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Workshop not found"));
        if (!w.getOrganizerId().equals(organizer))
            throw new AppException(HttpStatus.FORBIDDEN, "Not the owner");

        Object cached = cacheService.getManagementAnalyticsCache(workshopId.toString());
        if (cached != null) {
            try {
                WorkshopAnalyticsResponse resp = objectMapper.convertValue(cached, WorkshopAnalyticsResponse.class);
                return new UseCaseResult<>(HttpStatus.OK, "Analytics retrieved", resp);
            } catch (Exception ignored) {
            }
        }

        AnalyticsData analytics = registrationFacade.getWorkshopAnalytics(workshopId);
        int confirmed = registrationFacade.getConfirmedRegistrationCount(workshopId);
        int pending = registrationFacade.getPendingRegistrationCount(workshopId);

        WorkshopAnalyticsResponse resp = new WorkshopAnalyticsResponse(
                analytics.totalCapacity(),
                analytics.totalRegistered(),
                analytics.totalConfirmed(),
                analytics.totalCanceled(),
                analytics.totalCheckedIn(),
                analytics.totalNoShow(),
                analytics.utilizationPercentage(),
                analytics.confirmationRate(),
                confirmed,
                pending);

        cacheService.setManagementAnalyticsCache(workshopId.toString(), resp);
        return new UseCaseResult<>(HttpStatus.OK, "Analytics retrieved", resp);
    }

    public UseCaseResult<AttendeePageResponse> getWorkshopAttendees(String userId, UUID workshopId, int page, int size,
            String status) {
        UUID organizer = parseOrganizer(userId);
        Workshop w = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Workshop not found"));
        if (!w.getOrganizerId().equals(organizer))
            throw new AppException(HttpStatus.FORBIDDEN, "Not the owner");

        String cacheKey = keyGenerator.generateAttendeesKey(workshopId, page, size, status);
        Object cached = cacheService.getManagementAttendeesCache(cacheKey);
        if (cached != null) {
            try {
                AttendeePageResponse resp = objectMapper.convertValue(cached, AttendeePageResponse.class);
                return new UseCaseResult<>(HttpStatus.OK, "Attendee list retrieved", resp);
            } catch (Exception ignored) {
            }
        }

        AttendeePage remote = registrationFacade.listAttendees(workshopId, page, size, status);
        List<AttendeeItemResponse> items = remote.items().stream()
                .map(a -> new AttendeeItemResponse(a.id(), a.studentId(), a.fullName(), a.email(), a.status()))
                .collect(Collectors.toList());

        AttendeePageResponse response = new AttendeePageResponse(items, remote.pagination());
        cacheService.setManagementAttendeesCache(cacheKey, response);
        return new UseCaseResult<>(HttpStatus.OK, "Attendee list retrieved", response);
    }

    private UUID parseOrganizer(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (Exception e) {
            throw new AppException(HttpStatus.FORBIDDEN, "Invalid organizer id");
        }
    }

    private WorkshopManagementResponse mapToResponse(Workshop w) {
        return new WorkshopManagementResponse(
                w.getId(), w.getOrganizerId(), w.getTitle(), w.getDescription(), w.getLocation(), w.getVenue(),
                w.getRoom(), w.getRoomLayoutKey(), w.getStatus(), w.getVersion(), w.getCapacityTotal(),
                w.getCapacityRemaining(), w.getPriceAmount(), w.getCurrency(), w.getStartTime(), w.getEndTime(),
                w.getCreatedAt(), w.getUpdatedAt());
    }
}
