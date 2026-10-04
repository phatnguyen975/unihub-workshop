package com.unihub.workshop.workers.search_sync.inbound.mapper;

import com.unihub.workshop.workers.search_sync.core.models.WorkshopDocument;
import com.unihub.workshop.workers.search_sync.inbound.dto.WorkshopPublishedEventDto;
import com.unihub.workshop.workers.search_sync.inbound.dto.WorkshopUpdatedEventDto;
import java.time.Instant;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", imports = { List.class })
public interface SearchSyncEventMapper {
    @Mapping(target = "id", source = "workshopId")
    @Mapping(target = "status", constant = "PUBLISHED")
    @Mapping(target = "price", source = "price", defaultValue = "0")
    @Mapping(target = "currency", source = "currency", defaultValue = "USD")
    @Mapping(
        target = "startsAt",
        source = "startsAt",
        qualifiedByName = "parseInstant"
    )
    @Mapping(
        target = "endsAt",
        source = "endsAt",
        qualifiedByName = "parseInstant"
    )
    @Mapping(
        target = "publishedAt",
        source = "publishedAt",
        qualifiedByName = "parseInstant"
    )
    WorkshopDocument toWorkshopDocument(WorkshopPublishedEventDto dto);

    WorkshopDocument.SpeakerDocument toSpeakerDocument(
        WorkshopPublishedEventDto.SpeakerDto speaker
    );

    @Mapping(target = "id", source = "workshopId")
    @Mapping(target = "status", constant = "PUBLISHED")
    @Mapping(target = "price", source = "price", defaultValue = "0")
    @Mapping(target = "currency", source = "currency", defaultValue = "USD")
    @Mapping(target = "speakers", expression = "java(List.of())")
    @Mapping(
        target = "startsAt",
        source = "startsAt",
        qualifiedByName = "parseInstant"
    )
    @Mapping(
        target = "endsAt",
        source = "endsAt",
        qualifiedByName = "parseInstant"
    )
    @Mapping(
        target = "publishedAt",
        source = "publishedAt",
        qualifiedByName = "parseInstant"
    )
    WorkshopDocument toWorkshopDocument(WorkshopUpdatedEventDto dto);

    @Named("parseInstant")
    default Instant parseInstant(String value) {
        return value != null && !value.isBlank() ? Instant.parse(value) : null;
    }
}
