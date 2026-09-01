package com.unihub.workshop.workshops.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.workshops.application.ports.out.IngestionJobReportRepository;
import com.unihub.workshop.workshops.domain.model.CursorPage;
import com.unihub.workshop.workshops.domain.model.IngestionJobOutcome;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class IngestionJobReportRepositoryAdapter implements IngestionJobReportRepository {

    private final IngestionJobReportJpaRepository repository;
    private final ObjectMapper objectMapper;

    public IngestionJobReportRepositoryAdapter(IngestionJobReportJpaRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<IngestionJobReport> findByOrganizationId(UUID organizationId, String cursor, int limit) {
        Instant cursorTime = null;
        UUID cursorId = null;

        if (cursor != null && !cursor.isBlank()) {
            try {
                String decoded = new String(Base64.getDecoder().decode(cursor));
                CursorData cursorData = objectMapper.readValue(decoded, CursorData.class);
                cursorTime = cursorData.completedAt();
                cursorId = cursorData.id();
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid cursor format");
            }
        }

        // Fetch limit + 1 to know if there's a next page
        List<IngestionJobReportJpaEntity> entities = repository.findNextPage(
                organizationId, cursorTime, cursorId, PageRequest.of(0, limit + 1));

        boolean hasMore = entities.size() > limit;
        List<IngestionJobReportJpaEntity> pageEntities = hasMore ? entities.subList(0, limit) : entities;

        List<IngestionJobReport> items = pageEntities.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());

        String nextCursor = null;
        if (hasMore) {
            IngestionJobReportJpaEntity lastItem = pageEntities.get(pageEntities.size() - 1);
            try {
                CursorData nextCursorData = new CursorData(lastItem.getCompletedAt(), lastItem.getId());
                String json = objectMapper.writeValueAsString(nextCursorData);
                nextCursor = Base64.getEncoder().encodeToString(json.getBytes());
            } catch (Exception e) {
                throw new RuntimeException("Failed to encode next cursor", e);
            }
        }

        return new CursorPage<>(items, nextCursor, hasMore);
    }

    private IngestionJobReport toDomain(IngestionJobReportJpaEntity entity) {
        return new IngestionJobReport(
                entity.getId(),
                entity.getJobExecutionId(),
                entity.getOrganizationId(),
                entity.getSourceFileKey(),
                IngestionJobOutcome.valueOf(entity.getOutcome()),
                entity.getProcessedRowCount(),
                entity.getRejectedRowCount(),
                entity.getFileRejectionSourceKey(),
                entity.getRejectArtifactKey(),
                entity.getCompletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
