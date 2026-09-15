package com.unihub.workshop.workshops.presentation.mapper;

import com.unihub.workshop.workshops.application.usecases.RejectArtifactUrlResult;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import com.unihub.workshop.workshops.presentation.dto.IngestionJobListResponse.IngestionJobItem;
import com.unihub.workshop.workshops.presentation.dto.RejectArtifactUrlResponse;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface IngestionJobPresentationMapper {
    IngestionJobItem toItem(IngestionJobReport report);

    List<IngestionJobItem> toItemList(List<IngestionJobReport> reports);

    RejectArtifactUrlResponse toRejectArtifactUrlResponse(
        RejectArtifactUrlResult result
    );
}
