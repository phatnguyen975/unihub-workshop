package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.exception.AppException;
import com.unihub.workshop.shared.web.PaginationMetadata;
import com.unihub.workshop.shared.web.UseCaseResult;
import com.unihub.workshop.workshops.application.dto.WorkshopDiscoveryItemResponse;
import com.unihub.workshop.workshops.application.dto.WorkshopDiscoveryPageResponse;
import com.unihub.workshop.workshops.application.dto.WorkshopPublicDetailsResponse;
import com.unihub.workshop.workshops.application.dto.WorkshopSpeakerResponse;
import com.unihub.workshop.workshops.domain.model.Brochure;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopSpeaker;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.domain.repository.BrochureRepository;
import com.unihub.workshop.workshops.domain.repository.WorkshopRepository;
import com.unihub.workshop.workshops.domain.repository.WorkshopSpeakerRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.workshops.infrastructure.cache.CacheKeyGenerator;
import com.unihub.workshop.workshops.infrastructure.cache.WorkshopCacheService;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkshopPublicService implements WorkshopPublicUseCase {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final WorkshopRepository workshopRepository;
    private final WorkshopSpeakerRepository workshopSpeakerRepository;
    private final BrochureRepository brochureRepository;
    private final WorkshopCacheService cacheService;
    private final CacheKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    @Override
    public UseCaseResult<WorkshopDiscoveryPageResponse> discoverWorkshops(WorkshopDiscoveryCriteria criteria) {
        WorkshopDiscoveryCriteria normalizedCriteria = normalize(criteria);
        String cacheKey = keyGenerator.generateListKey(
                normalizedCriteria.page(),
                normalizedCriteria.size(),
                normalizedCriteria.q(),
                normalizedCriteria.minCapacity(),
                normalizedCriteria.organizerId() != null ? normalizedCriteria.organizerId().toString() : null,
                normalizedCriteria.sort());

        Object cachedResult = cacheService.getPublicListCache(cacheKey);
        if (cachedResult != null) {
            try {
                WorkshopDiscoveryPageResponse response = objectMapper.convertValue(cachedResult, WorkshopDiscoveryPageResponse.class);
                return new UseCaseResult<>(HttpStatus.OK, "Workshops list retrieved", response);
            } catch (Exception e) {
                // Cache miss on conversion; continue to fetch from DB
            }
        }

        PageRequest pageRequest = PageRequest.of(
                normalizedCriteria.page(),
                normalizedCriteria.size(),
                parseSort(normalizedCriteria.sort()));

        Page<Workshop> workshopPage = workshopRepository.searchPublishedWorkshops(normalizedCriteria, pageRequest);
        List<WorkshopDiscoveryItemResponse> items = workshopPage.getContent().stream()
                .map(this::toDiscoveryItem)
                .collect(Collectors.toList());

        PaginationMetadata pagination = new PaginationMetadata(
                workshopPage.getNumber(),
                workshopPage.getSize(),
                workshopPage.getTotalElements(),
                workshopPage.getTotalPages(),
                workshopPage.hasNext(),
                workshopPage.hasPrevious());

        WorkshopDiscoveryPageResponse response = new WorkshopDiscoveryPageResponse(items, pagination);
        cacheService.setPublicListCache(cacheKey, response);
        return new UseCaseResult<>(HttpStatus.OK, "Workshops list retrieved", response);
    }

    @Override
    public UseCaseResult<WorkshopPublicDetailsResponse> getWorkshopDetails(UUID workshopId) {
        String cacheKey = workshopId.toString();
        Object cachedResult = cacheService.getPublicDetailCache(cacheKey);
        if (cachedResult != null) {
            try {
                WorkshopPublicDetailsResponse response = objectMapper.convertValue(cachedResult, WorkshopPublicDetailsResponse.class);
                return new UseCaseResult<>(HttpStatus.OK, "Workshop details retrieved", response);
            } catch (Exception e) {
                // Cache miss on conversion; continue to fetch from DB
            }
        }

        Workshop workshop = workshopRepository.findById(workshopId)
                .filter(found -> found.getStatus() == WorkshopStatus.PUBLISHED)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Workshop not found", "not_found"));

        List<WorkshopSpeakerResponse> speakers = workshopSpeakerRepository
                .findByWorkshopIdOrderBySortOrder(workshopId)
                .stream()
                .map(this::toSpeakerResponse)
                .collect(Collectors.toList());

        String brochureUrl = brochureRepository.findByWorkshopIdOrderByUploadedAtDesc(workshopId).stream()
                .findFirst()
                .map(Brochure::getFileUrl)
                .orElse(null);

        WorkshopPublicDetailsResponse response = new WorkshopPublicDetailsResponse(
                workshop.getId(),
                workshop.getVersion(),
                workshop.getTitle(),
                workshop.getDescription(),
                workshop.getStartTime(),
                workshop.getEndTime(),
                workshop.getCapacityTotal(),
                workshop.getCapacityRemaining(),
                workshop.getStatus().name(),
                workshop.getLocation(),
                workshop.getVenue(),
                workshop.getRoom(),
                workshop.getRoomLayoutKey(),
                workshop.getPriceAmount(),
                workshop.getCurrency(),
                speakers,
                brochureUrl);

        cacheService.setPublicDetailCache(cacheKey, response);
        return new UseCaseResult<>(HttpStatus.OK, "Workshop details retrieved", response);
    }

    private WorkshopDiscoveryCriteria normalize(WorkshopDiscoveryCriteria criteria) {
        if (criteria == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid discovery criteria", "invalid_filter");
        }

        int page = Math.max(criteria.page(), 0);
        int size = criteria.size() > 0 ? criteria.size() : DEFAULT_PAGE_SIZE;
        Integer minCapacity = criteria.minCapacity();
        if (minCapacity != null && minCapacity < 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "minCapacity must be non-negative", "invalid_filter");
        }
        if (StringUtils.hasText(criteria.q()) && criteria.q().trim().length() < 2) {
            throw new AppException(HttpStatus.BAD_REQUEST, "q must contain at least 2 characters", "invalid_filter");
        }
        return new WorkshopDiscoveryCriteria(
                StringUtils.hasText(criteria.q()) ? criteria.q().trim() : null,
                criteria.organizerId(),
                page,
                size,
                criteria.startDate(),
                criteria.endDate(),
                minCapacity,
                StringUtils.hasText(criteria.sort()) ? criteria.sort().trim() : "startTime,asc");
    }

    private Sort parseSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return Sort.by(Sort.Direction.ASC, "startTime");
        }

        String[] tokens = sort.split(",");
        if (tokens.length > 2 || tokens.length == 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid sort parameter", "invalid_filter");
        }

        String property = tokens[0].trim();
        if (!StringUtils.hasText(property)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid sort parameter", "invalid_filter");
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (tokens.length == 2) {
            String directionToken = tokens[1].trim();
            if (!StringUtils.hasText(directionToken)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Invalid sort parameter", "invalid_filter");
            }
            try {
                direction = Sort.Direction.fromString(directionToken);
            } catch (IllegalArgumentException ex) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Invalid sort direction", "invalid_filter");
            }
        }

        if (!isSupportedSortField(property)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Unsupported sort field", "invalid_filter");
        }
        return Sort.by(direction, property);
    }

    private boolean isSupportedSortField(String field) {
        return switch (field) {
            case "id", "version", "title", "description", "startTime", "endTime",
                    "capacityTotal", "capacityRemaining", "priceAmount", "location", "venue",
                    "room", "roomLayoutKey", "createdAt" -> true;
            default -> false;
        };
    }

    private WorkshopDiscoveryItemResponse toDiscoveryItem(Workshop workshop) {
        return new WorkshopDiscoveryItemResponse(
                workshop.getId(),
                workshop.getVersion(),
                workshop.getTitle(),
                workshop.getDescription(),
                workshop.getStartTime(),
                workshop.getEndTime(),
                workshop.getCapacityTotal(),
                workshop.getCapacityRemaining(),
                workshop.getStatus().name(),
                workshop.getLocation(),
                workshop.getVenue(),
                workshop.getRoom(),
                workshop.getRoomLayoutKey(),
                workshop.getPriceAmount(),
                workshop.getCurrency());
    }

    private WorkshopSpeakerResponse toSpeakerResponse(WorkshopSpeaker speaker) {
        return new WorkshopSpeakerResponse(
                speaker.getId(),
                speaker.getSpeakerName(),
                speaker.getSpeakerBio(),
                speaker.getSortOrder());
    }
}
