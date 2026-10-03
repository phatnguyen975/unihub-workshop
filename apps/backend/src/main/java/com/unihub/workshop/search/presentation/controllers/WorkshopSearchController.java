package com.unihub.workshop.search.presentation.controllers;

import com.unihub.workshop.search.application.ports.in.SearchWorkshopsQuery;
import com.unihub.workshop.search.application.ports.in.SearchWorkshopsUseCase;
import com.unihub.workshop.search.domain.model.WorkshopCard;
import com.unihub.workshop.search.presentation.dto.WorkshopCardResponse;
import com.unihub.workshop.search.presentation.mapper.WorkshopSearchPresentationMapper;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.PageResponse;
import com.unihub.workshop.shared.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workshops")
@RequiredArgsConstructor
public class WorkshopSearchController {

    private final SearchWorkshopsUseCase searchWorkshopsUseCase;
    private final WorkshopSearchPresentationMapper mapper;

    @GetMapping
    public ApiResponse<PageResponse<WorkshopCardResponse>> searchWorkshops(
        @RequestParam(name = "q", required = false) String q,
        @RequestParam(name = "priceType", required = false) String priceType,
        @RequestParam(name = "from", required = false) String from,
        @RequestParam(name = "to", required = false) String to,
        @RequestParam(
            name = "sort",
            required = false,
            defaultValue = "startsAt,asc"
        ) String sort,
        @RequestParam(name = "cursor", required = false) String cursor,
        @RequestParam(
            name = "size",
            required = false,
            defaultValue = "20"
        ) Integer size
    ) {
        SearchWorkshopsQuery query = SearchWorkshopsQuery.of(
            q,
            priceType,
            from,
            to,
            sort,
            cursor,
            size
        );
        CursorPage<WorkshopCard> page = searchWorkshopsUseCase.search(query);
        PageResponse<WorkshopCardResponse> response = mapper.toPageResponse(
            page
        );
        return ApiResponse.success(response, "Workshops retrieved");
    }
}
