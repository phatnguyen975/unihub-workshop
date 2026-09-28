package com.unihub.workshop.workshops.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.application.ports.out.IngestionJobReportRepository;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import com.unihub.workshop.workshops.infrastructure.persistence.mapper.IngestionJobReportPersistenceMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class IngestionJobReportRepositoryAdapter
    implements IngestionJobReportRepository
{

    private final IngestionJobReportJpaRepository repository;
    private final IngestionJobReportPersistenceMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    @Transactional(readOnly = true)
    public CursorPage<IngestionJobReport> findByOrganizationId(
        UUID organizationId,
        String cursor,
        int size
    ) {
        Instant cursorTime = null;
        UUID cursorId = null;

        if (cursor != null && !cursor.isBlank()) {
            try {
                String trimmedCursor = cursor.trim();
                String decoded = new String(
                    Base64.getDecoder().decode(trimmedCursor),
                    StandardCharsets.UTF_8
                );
                IngestionJobCursor cursorData = objectMapper.readValue(
                    decoded,
                    IngestionJobCursor.class
                );
                if (
                    cursorData == null ||
                    cursorData.completedAt() == null ||
                    cursorData.id() == null
                ) {
                    throw new BadRequestException("Invalid cursor format");
                }
                cursorTime = cursorData.completedAt();
                cursorId = cursorData.id();
            } catch (Exception e) {
                if (e instanceof BadRequestException) {
                    throw (BadRequestException) e;
                }
                throw new BadRequestException("Invalid cursor format");
            }
        }

        Pageable pageable = PageRequest.of(0, size + 1);
        List<IngestionJobReportJpaEntity> entities =
            cursorTime == null
                ? repository.findFirstPage(organizationId, pageable)
                : repository.findNextPage(
                      organizationId,
                      cursorTime,
                      cursorId,
                      pageable
                  );

        boolean hasMore = entities.size() > size;
        List<IngestionJobReportJpaEntity> pageEntities = hasMore
            ? entities.subList(0, size)
            : entities;

        List<IngestionJobReport> items = mapper.toDomainList(pageEntities);

        String nextCursor = null;
        if (hasMore && !pageEntities.isEmpty()) {
            IngestionJobReportJpaEntity lastItem = pageEntities.get(
                pageEntities.size() - 1
            );
            try {
                IngestionJobCursor nextCursorData = new IngestionJobCursor(
                    lastItem.getCompletedAt(),
                    lastItem.getId()
                );
                String json = objectMapper.writeValueAsString(nextCursorData);
                nextCursor = Base64.getEncoder().encodeToString(
                    json.getBytes(StandardCharsets.UTF_8)
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to encode next cursor", e);
            }
        }

        return new CursorPage<>(items, nextCursor, hasMore);
    }

    @Override
    @Transactional
    public Optional<IngestionJobReport> findByIdAndOrganizationIdFromPrimary(
        UUID id,
        UUID organizationId
    ) {
        return repository
            .findByIdAndOrganizationId(id, organizationId)
            .map(mapper::toDomain);
    }

    private record IngestionJobCursor(Instant completedAt, UUID id) {}
}
