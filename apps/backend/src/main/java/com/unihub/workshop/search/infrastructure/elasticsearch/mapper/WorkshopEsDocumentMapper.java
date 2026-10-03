package com.unihub.workshop.search.infrastructure.elasticsearch.mapper;

import com.unihub.workshop.search.domain.model.WorkshopCard;
import com.unihub.workshop.search.infrastructure.elasticsearch.WorkshopEsDocument;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface WorkshopEsDocumentMapper {
    @Mapping(target = "id", source = "id", qualifiedByName = "stringToUuid")
    @Mapping(
        target = "organizationId",
        source = "organizationId",
        qualifiedByName = "stringToUuid"
    )
    @Mapping(target = "price", source = "price", defaultValue = "0")
    @Mapping(target = "currency", source = "currency", defaultValue = "USD")
    @Mapping(target = "availableSeats", ignore = true)
    @Mapping(
        target = "priceType",
        source = "doc",
        qualifiedByName = "derivePriceType"
    )
    WorkshopCard toCard(WorkshopEsDocument doc);

    List<WorkshopCard> toCards(List<WorkshopEsDocument> docs);

    @Named("stringToUuid")
    default UUID stringToUuid(String id) {
        return id != null && !id.isBlank() ? UUID.fromString(id) : null;
    }

    @Named("derivePriceType")
    default String derivePriceType(WorkshopEsDocument doc) {
        BigDecimal price =
            doc != null && doc.getPrice() != null
                ? doc.getPrice()
                : BigDecimal.ZERO;
        return WorkshopCard.derivePriceType(price);
    }
}
