package com.unihub.workshop.workshops.presentation.mapper;

import com.unihub.workshop.workshops.domain.model.PublicWorkshopDetail;
import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.presentation.dto.PublicSpeakerResponse;
import com.unihub.workshop.workshops.presentation.dto.PublicWorkshopDetailResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface PublicWorkshopDetailPresentationMapper {
    @Mapping(
        target = "price",
        source = "price",
        qualifiedByName = "formatPrice"
    )
    PublicWorkshopDetailResponse toDetailResponse(PublicWorkshopDetail detail);

    PublicSpeakerResponse toSpeakerResponse(Speaker speaker);

    @Named("formatPrice")
    default String formatPrice(BigDecimal price) {
        return price != null
            ? price.setScale(2, RoundingMode.HALF_UP).toPlainString()
            : "0.00";
    }
}
