package com.unihub.workshop.search.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.unihub.workshop.search.application.ports.in.SearchWorkshopsQuery;
import com.unihub.workshop.search.application.ports.out.WorkshopSearchRepository;
import com.unihub.workshop.search.domain.model.WorkshopCard;
import com.unihub.workshop.search.infrastructure.elasticsearch.mapper.WorkshopEsDocumentMapper;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ElasticsearchWorkshopSearchAdapter
    implements WorkshopSearchRepository
{

    private final ElasticsearchOperations elasticsearchOperations;
    private final WorkshopEsDocumentMapper documentMapper;

    @Value("${app.search.index-name:workshops}")
    private String indexName = "workshops";

    void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    @PostConstruct
    public void initIndex() {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(
                WorkshopEsDocument.class
            );
            if (!indexOps.exists()) {
                indexOps.createWithMapping();
                log.info(
                    "Initialized Elasticsearch index '{}' with schema mapping",
                    indexName
                );
            }
        } catch (Exception e) {
            log.warn(
                "Could not check or initialize Elasticsearch index '{}': {}",
                indexName,
                e.getMessage()
            );
        }
    }

    @Override
    public CursorPage<WorkshopCard> search(
        SearchWorkshopsQuery query,
        KeysetCursor cursor
    ) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // Only workshops with status = 'PUBLISHED' are visible
        boolQuery.filter(f ->
            f.term(t -> t.field("status").value("PUBLISHED"))
        );

        // Filter: priceType (FREE = price <= 0, PAID = price > 0)
        if (query.priceType() != null && !query.priceType().isBlank()) {
            if ("FREE".equalsIgnoreCase(query.priceType())) {
                boolQuery.filter(f ->
                    f.range(r -> r.number(n -> n.field("price").lte(0.0)))
                );
            } else if ("PAID".equalsIgnoreCase(query.priceType())) {
                boolQuery.filter(f ->
                    f.range(r -> r.number(n -> n.field("price").gt(0.0)))
                );
            }
        }

        // Filter: from (startsAt >= from)
        if (query.from() != null) {
            boolQuery.filter(f ->
                f.range(r ->
                    r.date(d ->
                        d.field("startsAt").gte(query.from().toString())
                    )
                )
            );
        }

        // Filter: to (startsAt <= to)
        if (query.to() != null) {
            boolQuery.filter(f ->
                f.range(r ->
                    r.date(d -> d.field("startsAt").lte(query.to().toString()))
                )
            );
        }

        // Keyword query: multi-match across root fields OR nested query on speakers.name
        if (query.q() != null && !query.q().isBlank()) {
            String q = query.q().trim();
            boolQuery.must(m ->
                m.bool(b ->
                    b
                        .should(s ->
                            s.multiMatch(mm ->
                                mm
                                    .query(q)
                                    .fields(
                                        "title^3",
                                        "slug^2",
                                        "location^1",
                                        "description^1"
                                    )
                                    .fuzziness("AUTO")
                            )
                        )
                        .should(s ->
                            s.nested(n ->
                                n.path("speakers").query(nq ->
                                    nq.match(mq ->
                                        mq
                                            .field("speakers.name")
                                            .query(v -> v.stringValue(q))
                                            .fuzziness("AUTO")
                                            .boost(2.0f)
                                    )
                                )
                            )
                        )
                )
            );
        }

        Sort sort = buildSort(query.sort());
        int pageSize = query.size();
        int fetchLimit = pageSize + 1; // +1 to evaluate hasMore

        NativeQueryBuilder queryBuilder = new NativeQueryBuilder()
            .withQuery(q -> q.bool(boolQuery.build()))
            .withSort(sort)
            .withPageable(PageRequest.of(0, fetchLimit));

        if (cursor != null && cursor.primaryValue() != null) {
            List<Object> searchAfter = parseSearchAfterValues(
                query.sort(),
                cursor
            );
            queryBuilder.withSearchAfter(searchAfter);
        }

        NativeQuery nativeQuery = queryBuilder.build();
        SearchHits<WorkshopEsDocument> searchHits =
            elasticsearchOperations.search(
                nativeQuery,
                WorkshopEsDocument.class,
                IndexCoordinates.of(indexName)
            );

        List<SearchHit<WorkshopEsDocument>> hitList =
            searchHits.getSearchHits();
        boolean hasMore = hitList.size() > pageSize;
        List<SearchHit<WorkshopEsDocument>> pageHits = hasMore
            ? hitList.subList(0, pageSize)
            : hitList;

        List<WorkshopCard> items = new ArrayList<>();
        for (SearchHit<WorkshopEsDocument> hit : pageHits) {
            items.add(documentMapper.toCard(hit.getContent()));
        }

        String nextCursor = null;
        if (hasMore && !pageHits.isEmpty()) {
            SearchHit<WorkshopEsDocument> lastHit = pageHits.get(
                pageHits.size() - 1
            );
            List<Object> sortValues = lastHit.getSortValues();
            if (sortValues != null && sortValues.size() >= 2) {
                String primaryVal =
                    sortValues.get(0) != null
                        ? sortValues.get(0).toString()
                        : "";
                String secondaryVal =
                    sortValues.get(1) != null
                        ? sortValues.get(1).toString()
                        : lastHit.getId();
                UUID lastId = UUID.fromString(lastHit.getId());
                String effectiveSort =
                    query.sort() != null && !query.sort().isBlank()
                        ? query.sort()
                        : SearchWorkshopsQuery.DEFAULT_SORT;
                String primarySortField = effectiveSort.split(",")[0].trim();
                KeysetCursor nextKeysetCursor = KeysetCursor.of(
                    lastId,
                    primarySortField,
                    primaryVal,
                    secondaryVal
                );
                nextCursor = KeysetCursorCodec.encode(nextKeysetCursor);
            }
        }

        return new CursorPage<>(items, nextCursor, hasMore);
    }

    private Sort buildSort(String sortParam) {
        String effectiveSort =
            sortParam != null && !sortParam.isBlank()
                ? sortParam
                : SearchWorkshopsQuery.DEFAULT_SORT;
        String[] parts = effectiveSort.split(",");
        String field = parts[0].trim();
        Sort.Direction direction =
            parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        // Primary sort + tie-breaker on id
        return Sort.by(
            new Sort.Order(direction, field),
            new Sort.Order(Sort.Direction.ASC, "id")
        );
    }

    private List<Object> parseSearchAfterValues(
        String sortParam,
        KeysetCursor cursor
    ) {
        String effectiveSort =
            sortParam != null && !sortParam.isBlank()
                ? sortParam
                : SearchWorkshopsQuery.DEFAULT_SORT;
        String field = effectiveSort.split(",")[0].trim();
        String primaryStr = cursor.primaryValue();
        Object primaryVal = primaryStr;

        if ("startsAt".equals(field)) {
            try {
                primaryVal = Long.parseLong(primaryStr);
            } catch (NumberFormatException e) {
                try {
                    primaryVal = Instant.parse(primaryStr).toEpochMilli();
                } catch (Exception ignored) {}
            }
        } else if ("price".equals(field)) {
            try {
                primaryVal = Double.parseDouble(primaryStr);
            } catch (NumberFormatException ignored) {}
        } else if ("capacity".equals(field)) {
            try {
                primaryVal = Integer.parseInt(primaryStr);
            } catch (NumberFormatException ignored) {}
        }

        return List.of(primaryVal, cursor.id().toString());
    }
}
