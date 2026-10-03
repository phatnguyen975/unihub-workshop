package com.unihub.workshop.workshops.presentation.controllers;

import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.workshops.application.ports.in.GetPublicWorkshopQuery;
import com.unihub.workshop.workshops.application.ports.in.GetPublicWorkshopUseCase;
import com.unihub.workshop.workshops.domain.model.PublicWorkshopDetail;
import com.unihub.workshop.workshops.presentation.dto.PublicWorkshopDetailResponse;
import com.unihub.workshop.workshops.presentation.mapper.PublicWorkshopDetailPresentationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workshops")
@RequiredArgsConstructor
public class PublicWorkshopController {

    private final GetPublicWorkshopUseCase getPublicWorkshopUseCase;
    private final PublicWorkshopDetailPresentationMapper mapper;

    @GetMapping("/{identifier}")
    public ResponseEntity<
        ApiResponse<PublicWorkshopDetailResponse>
    > getPublicWorkshop(@PathVariable String identifier) {
        GetPublicWorkshopQuery query = GetPublicWorkshopQuery.of(identifier);
        PublicWorkshopDetail detail = getPublicWorkshopUseCase.execute(query);
        PublicWorkshopDetailResponse response = mapper.toDetailResponse(detail);
        return ResponseEntity.ok(
            ApiResponse.success(response, "Workshop retrieved")
        );
    }
}
