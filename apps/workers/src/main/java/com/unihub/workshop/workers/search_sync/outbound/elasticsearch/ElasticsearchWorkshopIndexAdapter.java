package com.unihub.workshop.workers.search_sync.outbound.elasticsearch;

import com.unihub.workshop.workers.search_sync.core.models.WorkshopDocument;
import com.unihub.workshop.workers.search_sync.core.ports.out.WorkshopIndexStore;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchWorkshopIndexAdapter implements WorkshopIndexStore {

    private final ElasticsearchOperations elasticsearchOperations;
    private final WorkshopDocumentMapper mapper;

    @Override
    public void upsert(WorkshopDocument document) {
        if (document == null || document.id() == null) {
            return;
        }
        WorkshopEsDocument esDoc = mapper.toEsDocument(document);
        elasticsearchOperations.save(esDoc);
        log.info(
            "Successfully upserted workshop document in Elasticsearch: id={}",
            document.id()
        );
    }

    @Override
    public void updateSchedule(
        UUID workshopId,
        Instant startsAt,
        Instant endsAt,
        String timezone
    ) {
        if (workshopId == null) {
            return;
        }
        WorkshopEsDocument existing = elasticsearchOperations.get(
            workshopId.toString(),
            WorkshopEsDocument.class
        );
        if (existing != null) {
            existing.setStartsAt(startsAt);
            existing.setEndsAt(endsAt);
            existing.setTimezone(timezone);
            elasticsearchOperations.save(existing);
            log.info(
                "Successfully updated schedule for workshop in Elasticsearch: id={}",
                workshopId
            );
        } else {
            log.warn(
                "Workshop document not found in Elasticsearch for reschedule: id={}",
                workshopId
            );
        }
    }

    @Override
    public void delete(UUID workshopId) {
        if (workshopId == null) {
            return;
        }
        elasticsearchOperations.delete(
            workshopId.toString(),
            WorkshopEsDocument.class
        );
        log.info(
            "Successfully deleted workshop document from Elasticsearch: id={}",
            workshopId
        );
    }
}
