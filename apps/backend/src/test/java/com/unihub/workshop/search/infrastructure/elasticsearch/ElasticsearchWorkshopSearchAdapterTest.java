package com.unihub.workshop.search.infrastructure.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.unihub.workshop.search.application.ports.in.SearchWorkshopsQuery;
import com.unihub.workshop.search.domain.model.WorkshopCard;
import com.unihub.workshop.search.infrastructure.elasticsearch.mapper.WorkshopEsDocumentMapper;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

@ExtendWith(MockitoExtension.class)
class ElasticsearchWorkshopSearchAdapterTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private SearchHits<WorkshopEsDocument> searchHits;

    @Mock
    private SearchHit<WorkshopEsDocument> hit1;

    @Mock
    private SearchHit<WorkshopEsDocument> hit2;

    private ElasticsearchWorkshopSearchAdapter adapter;
    private final UUID workshopId1 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        WorkshopEsDocumentMapper mapper = Mappers.getMapper(
            WorkshopEsDocumentMapper.class
        );
        adapter = new ElasticsearchWorkshopSearchAdapter(
            elasticsearchOperations,
            mapper
        );
        adapter.setIndexName("workshops");
    }

    private WorkshopEsDocument createDoc(UUID id, BigDecimal price) {
        WorkshopEsDocument doc = new WorkshopEsDocument();
        doc.setId(id.toString());
        doc.setSlug("slug-" + id);
        doc.setTitle("Title " + id);
        doc.setDescription("Desc");
        doc.setLocation("Room 101");
        doc.setStartsAt(Instant.parse("2026-11-15T08:00:00Z"));
        doc.setEndsAt(Instant.parse("2026-11-15T12:00:00Z"));
        doc.setTimezone("Asia/Ho_Chi_Minh");
        doc.setPrice(price);
        doc.setCurrency("USD");
        doc.setStatus("PUBLISHED");
        doc.setPublishedAt(Instant.now());
        return doc;
    }

    @Test
    @DisplayName(
        "search: maps SearchHits to CursorPage<WorkshopCard>, evaluates hasMore=false when hits <= size"
    )
    void shouldMapHitsWhenNoMorePages() {
        SearchWorkshopsQuery query = new SearchWorkshopsQuery(
            null,
            null,
            null,
            null,
            "startsAt,asc",
            null,
            20
        );
        WorkshopEsDocument doc1 = createDoc(workshopId1, BigDecimal.ZERO);

        when(hit1.getContent()).thenReturn(doc1);

        when(searchHits.getSearchHits()).thenReturn(List.of(hit1));
        when(
            elasticsearchOperations.search(
                any(NativeQuery.class),
                eq(WorkshopEsDocument.class),
                any(IndexCoordinates.class)
            )
        ).thenReturn(searchHits);

        CursorPage<WorkshopCard> result = adapter.search(query, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).title()).isEqualTo(
            "Title " + workshopId1
        );
        assertThat(result.items().get(0).priceType()).isEqualTo("FREE");
        assertThat(result.hasMore()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName(
        "search: trims extra hit and generates nextCursor when hits > size"
    )
    void shouldTrimExtraHitAndGenerateCursorWhenHasMore() {
        SearchWorkshopsQuery query = new SearchWorkshopsQuery(
            null,
            null,
            null,
            null,
            "startsAt,asc",
            null,
            1
        );
        WorkshopEsDocument doc1 = createDoc(workshopId1, BigDecimal.ZERO);

        when(hit1.getContent()).thenReturn(doc1);
        when(hit1.getId()).thenReturn(workshopId1.toString());
        when(hit1.getSortValues()).thenReturn(
            List.of("1731657600000", workshopId1.toString())
        );

        when(searchHits.getSearchHits()).thenReturn(List.of(hit1, hit2));
        when(
            elasticsearchOperations.search(
                any(NativeQuery.class),
                eq(WorkshopEsDocument.class),
                any(IndexCoordinates.class)
            )
        ).thenReturn(searchHits);

        CursorPage<WorkshopCard> result = adapter.search(query, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id()).isEqualTo(workshopId1);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.nextCursor()).isNotNull();

        KeysetCursor decoded = KeysetCursorCodec.decode(
            result.nextCursor(),
            "startsAt"
        );
        assertThat(decoded.id()).isEqualTo(workshopId1);
    }
}
