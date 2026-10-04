package com.unihub.workshop.search.presentation.mapper;

import com.unihub.workshop.search.domain.model.WorkshopCard;
import com.unihub.workshop.search.presentation.dto.WorkshopCardResponse;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.PageResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface WorkshopSearchPresentationMapper {
    @Mapping(
        target = "price",
        source = "price",
        qualifiedByName = "formatPrice"
    )
    @Mapping(
        target = "startsAt",
        source = "startsAt",
        qualifiedByName = "formatInstant"
    )
    @Mapping(
        target = "endsAt",
        source = "endsAt",
        qualifiedByName = "formatInstant"
    )
    @Mapping(
        target = "availableSeats",
        source = "availableSeats",
        qualifiedByName = "formatAvailableSeats"
    )
    WorkshopCardResponse toCardResponse(WorkshopCard card);

    List<WorkshopCardResponse> toCardResponses(List<WorkshopCard> cards);

    default PageResponse<WorkshopCardResponse> toPageResponse(
        CursorPage<WorkshopCard> page
    ) {
        return PageResponse.from(page, this::toCardResponse);
    }

    @Named("formatPrice")
    default String formatPrice(BigDecimal price) {
        return price != null
            ? price.setScale(2, RoundingMode.HALF_UP).toPlainString()
            : "0.00";
    }

    @Named("formatInstant")
    default String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    @Named("formatAvailableSeats")
    default Integer formatAvailableSeats(Integer seats) {
        return seats != null ? Math.max(0, seats) : 0;
    }
}
