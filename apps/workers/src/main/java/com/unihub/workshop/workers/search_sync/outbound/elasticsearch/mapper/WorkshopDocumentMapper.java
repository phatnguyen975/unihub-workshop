package com.unihub.workshop.workers.search_sync.outbound.elasticsearch.mapper;

import com.unihub.workshop.workers.search_sync.core.models.WorkshopDocument;
import com.unihub.workshop.workers.search_sync.outbound.elasticsearch.WorkshopEsDocument;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface WorkshopDocumentMapper {
    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToString")
    @Mapping(
        target = "organizationId",
        source = "organizationId",
        qualifiedByName = "uuidToString"
    )
    WorkshopEsDocument toEsDocument(WorkshopDocument doc);

    @Mapping(target = "id", source = "id", qualifiedByName = "uuidToString")
    WorkshopEsDocument.SpeakerEsSubDocument toSpeakerEsSubDocument(
        WorkshopDocument.SpeakerDocument speaker
    );

    @Named("uuidToString")
    default String uuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }
}
