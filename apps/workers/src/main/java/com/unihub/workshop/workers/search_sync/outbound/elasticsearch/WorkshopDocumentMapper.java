package com.unihub.workshop.workers.search_sync.outbound.elasticsearch;

import com.unihub.workshop.workers.search_sync.core.models.WorkshopDocument;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkshopDocumentMapper {

    public WorkshopEsDocument toEsDocument(WorkshopDocument doc) {
        if (doc == null) {
            return null;
        }

        List<WorkshopEsDocument.SpeakerEsSubDocument> speakerDocs =
            doc.speakers() != null
                ? doc
                      .speakers()
                      .stream()
                      .map(s ->
                          new WorkshopEsDocument.SpeakerEsSubDocument(
                              s.id() != null ? s.id().toString() : null,
                              s.name(),
                              s.bio(),
                              s.avatarUrl(),
                              s.linkedinUrl(),
                              s.email(),
                              s.sortOrder()
                          )
                      )
                      .toList()
                : List.of();

        return WorkshopEsDocument.builder()
            .id(doc.id() != null ? doc.id().toString() : null)
            .organizationId(
                doc.organizationId() != null
                    ? doc.organizationId().toString()
                    : null
            )
            .title(doc.title())
            .slug(doc.slug())
            .description(doc.description())
            .coverImageUrl(doc.coverImageUrl())
            .location(doc.location())
            .timezone(doc.timezone())
            .startsAt(doc.startsAt())
            .endsAt(doc.endsAt())
            .capacity(doc.capacity())
            .price(doc.price())
            .currency(doc.currency())
            .status(doc.status())
            .publishedAt(doc.publishedAt())
            .speakers(speakerDocs)
            .build();
    }
}
