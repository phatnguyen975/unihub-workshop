package com.unihub.workshop.workers.search_sync.outbound.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workers.search_sync.core.models.WorkshopDocument;
import com.unihub.workshop.workers.search_sync.outbound.elasticsearch.mapper.WorkshopDocumentMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

class ElasticsearchWorkshopIndexAdapterTest {

    private ElasticsearchOperations elasticsearchOperations;
    private WorkshopDocumentMapper mapper;
    private ElasticsearchWorkshopIndexAdapter adapter;

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        elasticsearchOperations = mock(ElasticsearchOperations.class);
        mapper = Mappers.getMapper(WorkshopDocumentMapper.class);
        adapter = new ElasticsearchWorkshopIndexAdapter(
            elasticsearchOperations,
            mapper
        );
    }

    @Test
    @DisplayName(
        "upsert: maps domain document to ES document and saves to Elasticsearch"
    )
    void shouldMapAndSaveEsDocumentOnUpsert() {
        Instant now = Instant.now();
        WorkshopDocument doc = new WorkshopDocument(
            WORKSHOP_ID,
            ORG_ID,
            "Kubernetes Deep Dive",
            "kubernetes-deep-dive",
            "Advanced K8s orchestration",
            "https://cdn.unihub.com/cover.jpg",
            "Room 204",
            "Asia/Ho_Chi_Minh",
            now.plusSeconds(3600),
            now.plusSeconds(7200),
            75,
            BigDecimal.valueOf(15.00),
            "USD",
            "PUBLISHED",
            now,
            List.of(
                new WorkshopDocument.SpeakerDocument(
                    UUID.randomUUID(),
                    "Linus Torvalds",
                    "OS architect",
                    "https://cdn.unihub.com/linus.jpg",
                    null,
                    "linus@kernel.org",
                    0
                )
            )
        );

        adapter.upsert(doc);

        ArgumentCaptor<WorkshopEsDocument> captor = ArgumentCaptor.forClass(
            WorkshopEsDocument.class
        );
        verify(elasticsearchOperations).save(captor.capture());

        WorkshopEsDocument saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(WORKSHOP_ID.toString());
        assertThat(saved.getTitle()).isEqualTo("Kubernetes Deep Dive");
        assertThat(saved.getCapacity()).isEqualTo(75);
        assertThat(saved.getSpeakers()).hasSize(1);
        assertThat(saved.getSpeakers().get(0).getName()).isEqualTo(
            "Linus Torvalds"
        );
    }

    @Test
    @DisplayName(
        "updateSchedule: updates startsAt, endsAt, and timezone of existing ES document"
    )
    void shouldUpdateScheduleOfExistingDocument() {
        Instant oldStartsAt = Instant.now().plusSeconds(3600);
        Instant oldEndsAt = oldStartsAt.plusSeconds(3600);
        WorkshopEsDocument existing = new WorkshopEsDocument();
        existing.setId(WORKSHOP_ID.toString());
        existing.setTitle("Existing Workshop");
        existing.setStartsAt(oldStartsAt);
        existing.setEndsAt(oldEndsAt);
        existing.setTimezone("UTC");

        when(
            elasticsearchOperations.get(
                WORKSHOP_ID.toString(),
                WorkshopEsDocument.class
            )
        ).thenReturn(existing);

        Instant newStartsAt = Instant.now().plusSeconds(86400);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);
        String newTimezone = "Asia/Ho_Chi_Minh";

        adapter.updateSchedule(
            WORKSHOP_ID,
            newStartsAt,
            newEndsAt,
            newTimezone
        );

        ArgumentCaptor<WorkshopEsDocument> captor = ArgumentCaptor.forClass(
            WorkshopEsDocument.class
        );
        verify(elasticsearchOperations).save(captor.capture());

        WorkshopEsDocument updated = captor.getValue();
        assertThat(updated.getStartsAt()).isEqualTo(newStartsAt);
        assertThat(updated.getEndsAt()).isEqualTo(newEndsAt);
        assertThat(updated.getTimezone()).isEqualTo(newTimezone);
    }

    @Test
    @DisplayName("delete: deletes workshop by ID from Elasticsearch")
    void shouldDeleteFromElasticsearch() {
        adapter.delete(WORKSHOP_ID);

        verify(elasticsearchOperations).delete(
            WORKSHOP_ID.toString(),
            WorkshopEsDocument.class
        );
    }
}
